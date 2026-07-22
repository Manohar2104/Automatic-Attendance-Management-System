# ruff: noqa: E402
from fastapi import FastAPI, Depends, HTTPException, Body, Header
from typing import List, Optional
from contextlib import asynccontextmanager
from .schemas import HealthCheck, UserCreate, Token
from .db import Base, get_db
from .config import settings
from .models import User
from .auth import (
    get_password_hash,
    verify_password,
    create_access_token,
    create_refresh_token,
)
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
import asyncio
import datetime
from .schemas import DeviceRegister, PresenceEvent, DeviceInfo
from .models import (
    DeviceBinding,
    Event,
    EventType,
    Attendance,
    AttendanceStatus,
    AttendanceOverride,
    Session,
    SessionStatus,
    BluetoothProximity,
)
import uuid
import time
from .auth import require_current_user
from sqlalchemy.exc import IntegrityError
import logging
from .logging_config import (
    setup_logging,
    request_id_var,
    user_id_var,
    session_id_var,
    endpoint_var,
)

# Initialize structured logging configuration immediately on app startup
setup_logging()

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

# initialize find3 subscriber if configured
from .find3_subscriber import init_subscriber, start_subscriber, stop_subscriber
from .session_scheduler import check_and_process_sessions
from .rate_limit import get_redis, close_redis, is_rate_limited, blacklist_token

logger = logging.getLogger(__name__)


async def session_scheduler_task():
    """Background task that periodically processes session state transitions."""
    while True:
        try:
            await check_and_process_sessions()
        except Exception as e:
            logger.error(f"Error in session scheduler: {e}", exc_info=True)
        await asyncio.sleep(15)  # Run every 15 seconds for fast session activation


@asynccontextmanager
async def lifespan(app):
    # startup
    init_subscriber(app)
    from .db import get_engine

    eng = get_engine()
    async with eng.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        try:
            from sqlalchemy import text
            await conn.execute(text("ALTER TABLE sessions ADD COLUMN faculty_id UUID REFERENCES users(id)"))
        except Exception:
            pass
    await start_subscriber(app)

    # Initialize Redis
    try:
        await get_redis()
    except Exception as e:
        logger.error(f"Failed to initialize Redis: {e}")
        logger.warning("Continuing without Redis - rate limiting disabled")

    # Start session scheduler background task
    scheduler_task = asyncio.create_task(session_scheduler_task())
    app.session_scheduler_task = scheduler_task

    yield

    # shutdown
    await stop_subscriber(app)
    scheduler_task.cancel()
    try:
        await scheduler_task
    except asyncio.CancelledError:
        pass

    # Close Redis
    await close_redis()


app = FastAPI(title="Smart Attendance Registry", lifespan=lifespan)


class RateLimitMiddleware(BaseHTTPMiddleware):
    """Middleware to apply rate limiting based on endpoint."""

    async def dispatch(self, request: Request, call_next) -> Response:
        # Get client IP
        client_ip = request.client.host if request.client else "unknown"

        # Bypass rate limit for localhost, Docker internal networks, or bypass header
        if (
            client_ip in ("127.0.0.1", "::1", "localhost")
            or client_ip.startswith("172.")
            or request.headers.get("x-bypass-rate-limit") == "true"
        ):
            return await call_next(request)

        # Determine rate limit based on path
        path = request.url.path

        if path == "/presence":
            requests_per_minute = 300  # 5 per second
        elif path in ["/register", "/login"]:
            requests_per_minute = 60  # 1 per second
        else:
            requests_per_minute = 100  # ~1.67 per second

        # Check rate limit
        if await is_rate_limited(client_ip, path, requests_per_minute):
            return Response(
                content='{"detail":"Rate limit exceeded"}',
                status_code=429,
                media_type="application/json",
            )

        response = await call_next(request)
        return response


class StructuredLoggingMiddleware(BaseHTTPMiddleware):
    """Middleware to inject context variables and log request lifecycle in JSON."""

    async def dispatch(self, request: Request, call_next) -> Response:
        req_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
        request_id_var.set(req_id)
        endpoint_var.set(request.url.path)

        # Reset user and session context
        user_id_var.set("")
        session_id_var.set("")

        start_time = time.perf_counter()
        status_code = 500
        try:
            response = await call_next(request)
            status_code = response.status_code
            return response
        except Exception as e:
            logger.exception("Exception occurred during request processing")
            raise e
        finally:
            latency_ms = round((time.perf_counter() - start_time) * 1000, 2)
            client_ip = request.client.host if request.client else "unknown"

            logger.info(
                f"Request {request.method} {request.url.path} finished with status {status_code}",
                extra={
                    "latency_ms": latency_ms,
                    "status_code": status_code,
                    "client_ip": client_ip,
                    "method": request.method,
                },
            )


# Apply rate limiting middleware
app.add_middleware(RateLimitMiddleware)

# Apply structured logging middleware (runs outermost to capture rate limit blocks too)
app.add_middleware(StructuredLoggingMiddleware)


@app.get("/health", response_model=HealthCheck)
async def health(db: AsyncSession = Depends(get_db)):
    now = datetime.datetime.now(datetime.timezone.utc)
    return HealthCheck(status="ok", now=now)


@app.get("/dashboard")
async def get_dashboard():
    from fastapi.responses import FileResponse
    import os
    static_file_path = os.path.join(os.path.dirname(__file__), "static", "dashboard.html")
    return FileResponse(static_file_path)


@app.post("/register", response_model=Token)
async def register(payload: UserCreate, db: AsyncSession = Depends(get_db)):
    # rudimentary registration
    q = await db.execute(select(User).where(User.email == payload.email))
    existing = q.scalars().first()
    if existing:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    from .models import RoleEnum
    role_val = RoleEnum.STUDENT
    if payload.role == "FACULTY":
        role_val = RoleEnum.FACULTY
    elif payload.role == "ADMIN":
        role_val = RoleEnum.ADMIN

    user = User(
        email=payload.email,
        password_hash=get_password_hash(payload.password),
        role=role_val
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    user_id_var.set(str(user.id))
    logger.info(
        f"User registered successfully: {payload.email}",
        extra={"user_email": payload.email, "user_id": str(user.id)},
    )
    access_token = create_access_token(str(user.id))
    refresh_token = create_refresh_token(str(user.id))
    return Token(access_token=access_token, refresh_token=refresh_token)


@app.post("/login", response_model=Token)
async def login(payload: UserCreate, db: AsyncSession = Depends(get_db)):
    q = await db.execute(select(User).where(User.email == payload.email))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    user_id_var.set(str(user.id))
    logger.info(
        f"User logged in successfully: {payload.email}",
        extra={"user_email": payload.email, "user_id": str(user.id)},
    )
    access_token = create_access_token(str(user.id))
    refresh_token = create_refresh_token(str(user.id))
    return Token(access_token=access_token, refresh_token=refresh_token)


@app.get("/users/me")
async def get_current_user_profile(current_user=Depends(require_current_user)):
    return {
        "id": str(current_user.id),
        "email": current_user.email,
        "role": current_user.role.value if hasattr(current_user.role, "value") else str(current_user.role)
    }


@app.get("/users/me/ble-beacon-id")
async def get_ble_beacon_id(current_user=Depends(require_current_user)):
    """
    Returns the BLE beacon identifier the faculty member's phone should broadcast.
    The mobile app reads this value and uses it as the BLE advertisement UUID so
    that student devices can detect and record faculty proximity.
    """
    return {"beacon_id": str(current_user.id)}


@app.post("/refresh", response_model=Token)
async def refresh(
    authorization: str = Header(None), db: AsyncSession = Depends(get_db)
):
    """Refresh access token using a refresh token."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid auth header")
    token = authorization[7:]

    try:
        from jose import jwt

        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        user_id = payload.get("sub")
        token_type = payload.get("type")

        if token_type != "refresh":
            raise HTTPException(
                status_code=401, detail="Invalid token type. Use refresh token"
            )

        if not user_id:
            raise HTTPException(status_code=401, detail="Invalid token payload")

        # Verify user exists
        user_id = uuid.UUID(user_id)
        q = await db.execute(select(User).where(User.id == user_id))
        user = q.scalars().first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")

        # Generate new access and refresh tokens
        new_access_token = create_access_token(str(user.id))
        new_refresh_token = create_refresh_token(str(user.id))
        return Token(access_token=new_access_token, refresh_token=new_refresh_token)
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=401, detail="Refresh token expired. Please login again"
        )
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid refresh token")


@app.post("/logout")
async def logout(
    authorization: str = Header(None), current_user=Depends(require_current_user)
):
    """Logout and blacklist the access token."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid auth header")
    token = authorization[7:]

    # Blacklist the token
    await blacklist_token(token, ttl_seconds=900)  # 15 minutes
    return {"message": "Logged out successfully"}


@app.post("/register-device")
async def register_device(
    payload: DeviceRegister,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    # Attach a device to the current authenticated user, enforcing max devices
    q = await db.execute(
        select(DeviceBinding).where(
            DeviceBinding.user_id == current_user.id, DeviceBinding.status == "ACTIVE"
        )
    )
    bound = q.scalars().all()
    if len(bound) >= settings.max_devices_per_user:
        raise HTTPException(
            status_code=400,
            detail=f"Max devices ({settings.max_devices_per_user}) reached",
        )
    # ensure fingerprint isn't already bound
    q2 = await db.execute(
        select(DeviceBinding).where(
            DeviceBinding.device_fingerprint == payload.device_fingerprint
        )
    )
    existing = q2.scalars().first()
    if existing:
        if existing.user_id != current_user.id and existing.status == "ACTIVE":
            raise HTTPException(
                status_code=400,
                detail="This device is already registered to another user's account.",
            )
        return {
            "id": str(existing.id),
            "device_fingerprint": existing.device_fingerprint,
            "status": existing.status.value if hasattr(existing.status, "value") else str(existing.status),
        }
    binding = DeviceBinding(
        user_id=current_user.id, device_fingerprint=payload.device_fingerprint
    )
    db.add(binding)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(
            status_code=409, detail="Device fingerprint already registered"
        )
    await db.refresh(binding)
    return DeviceInfo(
        id=str(binding.id),
        device_fingerprint=binding.device_fingerprint,
        status=binding.status.value,
        last_seen_at=binding.last_seen_at,
    )


@app.get("/devices")
async def list_devices(
    db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)
):
    q = await db.execute(
        select(DeviceBinding).where(DeviceBinding.user_id == current_user.id)
    )
    rows = q.scalars().all()
    out = []
    for r in rows:
        out.append(
            DeviceInfo(
                id=str(r.id),
                device_fingerprint=r.device_fingerprint,
                status=r.status.value,
                last_seen_at=r.last_seen_at,
            )
        )
    return out


@app.post("/devices/{device_id}/revoke")
async def revoke_device(
    device_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    from uuid import UUID as UUID_type

    try:
        device_uuid = UUID_type(device_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid device ID format")

    q = await db.execute(select(DeviceBinding).where(DeviceBinding.id == device_uuid))
    binding = q.scalars().first()
    if not binding:
        raise HTTPException(status_code=404, detail="Device not found")
    if binding.user_id != current_user.id and current_user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Forbidden")
    binding.status = "REVOKED"
    binding.revoked_at = datetime.datetime.now(datetime.timezone.utc)
    binding.revoked_by = current_user.id
    await db.commit()

    logger.info(
        f"AUDIT: Device {device_id} revoked by user {current_user.id}",
        extra={
            "event_type": "audit_device_revocation",
            "device_id": device_id,
            "revoked_by": str(current_user.id),
            "owner_id": str(binding.user_id) if binding.user_id else "",
        },
    )

    return {"status": "revoked"}


@app.post("/presence")
async def presence_event(payload: PresenceEvent, db: AsyncSession = Depends(get_db)):
    # Record an incoming presence event from a device
    # Resolve user from device_fingerprint
    q = await db.execute(
        select(DeviceBinding).where(
            DeviceBinding.device_fingerprint == payload.device_fingerprint
        )
    )
    binding = q.scalars().first()
    user_id = binding.user_id if binding else None
    # don't invent a user_id for anonymous devices; leave null
    ev = Event(
        user_id=user_id,
        session_id=payload.session_id,
        type=EventType.ENTER if payload.event_type == "ENTER" else EventType.LEAVE,
        location=payload.location,
    )
    db.add(ev)
    # update device binding last_seen and status if bound
    if binding:
        binding.last_seen_at = datetime.datetime.now(datetime.timezone.utc)
        binding.status = binding.status or "ACTIVE"
        db.add(binding)
    await db.commit()
    await db.refresh(ev)
    return {"status": "ok"}


def _ensure_utc(dt):
    """Ensure a datetime is timezone-aware (UTC). Naive datetimes are assumed to be UTC."""
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=datetime.timezone.utc)
    return dt


@app.post("/find3-data")
async def proxy_find3_data(payload: dict, db: AsyncSession = Depends(get_db)):
    """Proxy FIND3 sensor payloads from mobile devices directly to the FIND3 container."""
    import httpx
    device_id = payload.get("d") or payload.get("device") or "unknown_device"

    # Extract Bluetooth BLE beacons if present in scan payload
    sensors = payload.get("s", {})
    bluetooth = sensors.get("bluetooth", {}) if isinstance(sensors, dict) else {}
    if bluetooth:
        from .models import DeviceBinding, BindingStatus, BluetoothProximity, User, RoleEnum
        from sqlalchemy import and_

        # Bug Fix #5: Strip family prefix (e.g. "home:android:abcd" → "android:abcd")
        # FIND3 prepends a family name that is NOT part of the registered fingerprint.
        raw_device_id = device_id.split(":", 1)[1] if ":" in device_id else device_id

        stmt = select(DeviceBinding).where(
            and_(
                DeviceBinding.device_fingerprint.in_([device_id, raw_device_id]),
                DeviceBinding.status == BindingStatus.ACTIVE
            )
        )
        res_binding = await db.execute(stmt)
        binding = res_binding.scalars().first()
        if binding and binding.user_id:
            student_id = binding.user_id

            # Bug Fix #3: Resolve the active session for this student's location so we
            # can tag the BluetoothProximity row with a session_id.
            active_session_id = None
            family = sensors.get("f") or (device_id.split(":", 1)[0] if ":" in device_id else None)
            # Try to find any active session (we'll use it for all BLE rows in this scan)
            sess_q = await db.execute(
                select(Session).where(Session.status == SessionStatus.ACTIVE).limit(1)
            )
            active_sess = sess_q.scalars().first()
            if active_sess:
                active_session_id = str(active_sess.id)

            for raw_beacon_id, rssi in bluetooth.items():
                try:
                    beacon_uuid = uuid.UUID(raw_beacon_id)
                    # Check if this beacon UUID corresponds to a registered Faculty member
                    # Bug Fix #1: Faculty broadcasts their User.id UUID as the BLE beacon identifier.
                    # The faculty app reads this from GET /users/me/ble-beacon-id.
                    stmt_fac = select(User).where(and_(User.id == beacon_uuid, User.role == RoleEnum.FACULTY))
                    res_fac = await db.execute(stmt_fac)
                    faculty = res_fac.scalars().first()
                    if faculty:
                        log_prox = BluetoothProximity(
                            student_id=student_id,
                            faculty_id=faculty.id,
                            rssi=float(rssi),
                            session_id=active_session_id,  # Bug Fix #4: always populate session_id
                        )
                        db.add(log_prox)
                        logger.info(
                            f"BLUETOOTH PROXIMITY: student={student_id} detected "
                            f"faculty={faculty.id} RSSI={rssi} session={active_session_id}"
                        )
                except Exception as ex:
                    logger.debug(f"Invalid BLE beacon identifier or parse error: {ex}")
            try:
                await db.commit()
            except Exception as commit_ex:
                logger.error(f"Failed to commit Bluetooth proximity logs: {commit_ex}")
        else:
            logger.warning(
                f"BLE scan received but no active binding found for device '{device_id}' "
                f"(tried raw='{raw_device_id}'). No proximity recorded."
            )

    async with httpx.AsyncClient() as client:
        try:
            res = await client.post("http://find3:8003/data", json=payload, timeout=5.0)
            res_json = res.json()
            guesses = res_json.get("guesses", [])
            top_guess = guesses[0].get("location") if guesses else "None"
            top_prob = guesses[0].get("probability", 0.0) if guesses else 0.0
            logger.info(f"FIND3 SCAN PROXIED: device={device_id} | guess='{top_guess}' (prob={top_prob*100:.1f}%)")
            return res_json
        except Exception as e:
            logger.error(f"Error proxying FIND3 data: {e}")
            return {"error": str(e), "guesses": []}


@app.post("/compute-attendance/{session_id}")
async def compute_attendance(
    session_id: str, location_filter: str = None, db: AsyncSession = Depends(get_db)
):
    """
    Compute attendance for a session with location-based validation.
    If location_filter is provided, only count events from that location.
    Corridor/hallway events are ALWAYS excluded regardless of location_filter.
    """
    from .schemas import ComputeAttendanceResponse, AttendanceResult

    sess_q = await db.execute(select(Session).where(Session.id == session_id))
    sess_obj = sess_q.scalars().first()

    # Auto-populate location_filter from the session's assigned room if not provided
    if not location_filter and sess_obj and sess_obj.location:
        location_filter = sess_obj.location
        logger.info(f"Auto-set location_filter='{location_filter}' from session {session_id}")

    q = await db.execute(select(Event).where(Event.session_id == session_id))
    events = q.scalars().all()

    # ALWAYS exclude corridor/hallway events from attendance computation
    events = [
        e for e in events
        if not e.location or not any(
            tag in e.location.lower() for tag in ("corridor", "hallway")
        )
    ]

    # Filter events by location if provided
    if location_filter:
        events = [e for e in events if e.location == location_filter]

    # Group events by user
    user_events: dict = {}
    for e in events:
        if not e.user_id:
            continue
        user_events.setdefault(e.user_id, []).append(e)

    # Determine session window from events
    session_id_var.set(session_id)
    if events:
        timestamps = [e.timestamp for e in events if e.timestamp is not None]
        start = min(timestamps)
        end = max(timestamps)
        duration_seconds = max(1, int((end - start).total_seconds()))
    else:
        start = None
        end = None
        duration_seconds = 0

    max_possible = 0
    if duration_seconds:
        max_possible = max(1, duration_seconds // settings.submission_interval_seconds)

    # Fetch existing attendances and overrides for this session
    q_existing = await db.execute(
        select(Attendance).where(Attendance.session_id == session_id)
    )
    existing_attendances = {a.student_id: a for a in q_existing.scalars().all()}

    q_overrides = await db.execute(
        select(AttendanceOverride, Attendance.student_id)
        .join(Attendance, AttendanceOverride.attendance_id == Attendance.id)
        .where(Attendance.session_id == session_id)
    )
    overrides_data = q_overrides.all()
    overrides = {}
    for ov, student_id in overrides_data:
        if student_id not in overrides or ov.created_at > overrides[student_id].created_at:
            overrides[student_id] = ov

    # Compute for union of user_events and existing_attendances to preserve overrides/history
    all_student_ids = set(user_events.keys()).union(existing_attendances.keys())

    # Filter out the faculty_id from the student list
    faculty_scans_count = 0
    if sess_obj and sess_obj.faculty_id:
        faculty_evs = user_events.get(sess_obj.faculty_id, [])
        faculty_scans_count = sum(1 for e in faculty_evs if e.type == EventType.ENTER)
        if sess_obj.faculty_id in all_student_ids:
            all_student_ids.remove(sess_obj.faculty_id)

    # Use professor's valid scans as baseline. Fall back to expected max_possible if 0.
    baseline_scans = faculty_scans_count if faculty_scans_count > 0 else (max_possible or 1)

    results = []
    bound_count = 0
    for user_id in all_student_ids:
        evs = user_events.get(user_id, [])
        if evs:
            bound_count += 1
        enter_count = sum(1 for e in evs if e.type == EventType.ENTER)

        # Check Bluetooth proximity verification if professor was active
        has_ble_verification = True
        if faculty_scans_count > 0 and sess_obj and sess_obj.faculty_id:
            # Bug Fix #2 & #3: Prefer session_id-based lookup (reliable); fall back to
            # UTC-normalised timestamp range only if session_id is unavailable.
            prox_filters = [
                BluetoothProximity.student_id == user_id,
                BluetoothProximity.faculty_id == sess_obj.faculty_id,
            ]
            # Prefer session_id match (most reliable)
            prox_filters_with_session = prox_filters + [
                BluetoothProximity.session_id == str(session_id)
            ]
            stmt_prox = select(BluetoothProximity).where(
                and_(*prox_filters_with_session)
            ).limit(1)
            res_prox = await db.execute(stmt_prox)
            prox_row = res_prox.scalars().first()

            if prox_row is None:
                # Fallback: timestamp range with Bug Fix #2 (ensure UTC-aware comparison)
                start_time = _ensure_utc(start or sess_obj.scheduled_start)
                end_time   = _ensure_utc(end   or sess_obj.scheduled_end)
                stmt_prox_ts = select(BluetoothProximity).where(
                    and_(
                        BluetoothProximity.student_id == user_id,
                        BluetoothProximity.faculty_id == sess_obj.faculty_id,
                        BluetoothProximity.timestamp >= start_time,
                        BluetoothProximity.timestamp <= end_time,
                    )
                ).limit(1)
                res_prox_ts = await db.execute(stmt_prox_ts)
                prox_row = res_prox_ts.scalars().first()

            has_ble_verification = prox_row is not None
            if not has_ble_verification:
                logger.info(
                    f"BLE check FAILED for student={user_id} in session={session_id} "
                    f"(faculty={sess_obj.faculty_id}): no proximity record found"
                )

        raw_score = (
            min(100, int((enter_count / baseline_scans) * 100)) if baseline_scans else 0
        )
        status_enum = (
            AttendanceStatus.PRESENT
            if (raw_score >= settings.present_threshold_percent and has_ble_verification)
            else AttendanceStatus.ABSENT
        )

        attendance = existing_attendances.get(user_id)
        override = overrides.get(user_id)

        final_status = status_enum
        if override:
            final_status = override.override_status

        if attendance:
            attendance.score = raw_score
            attendance.status = final_status
            db.add(attendance)
        else:
            attendance = Attendance(
                student_id=user_id,
                session_id=session_id,
                score=raw_score,
                status=final_status,
            )
            db.add(attendance)
            await db.flush()

        location_match = location_filter is None or any(
            e.location == location_filter for e in evs
        )
        
        # Resolve student email from User table
        q_user = await db.execute(select(User).where(User.id == user_id))
        user_obj = q_user.scalars().first()
        student_email = user_obj.email if user_obj else "unknown@student.com"

        results.append(
            AttendanceResult(
                user_id=str(user_id),
                email=student_email,
                score=raw_score,
                status=final_status.value,
                enter_count=enter_count,
                location_match=location_match,
            )
        )

    # crowd check
    crowd_ok = bound_count >= settings.min_crowd_size
    location_validated = location_filter is not None

    await db.commit()

    logger.info(
        f"AUDIT: Computed attendance for session {session_id} (location_filter={location_filter}). Found {bound_count} bound devices.",
        extra={
            "event_type": "audit_compute_attendance",
            "session_id": session_id,
            "location_filter": location_filter,
            "bound_devices_count": bound_count,
            "crowd_ok": crowd_ok,
        },
    )

    return ComputeAttendanceResponse(
        session_id=session_id,
        duration_seconds=duration_seconds,
        max_possible_submissions=max_possible,
        bound_devices=bound_count,
        crowd_ok=crowd_ok,
        location_validated=location_validated,
        results=results,
    )


@app.post("/sessions/create")
async def create_session(
    course_id: str,
    room_id: str,
    location: str,
    scheduled_start: str,
    scheduled_end: str,
    faculty_email: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    """Create a new session. Only admin can create."""
    if current_user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Only admins can create sessions")

    from .models import Session, SessionStatus, User, RoleEnum
    from datetime import datetime as dt

    try:
        # Handle + sign decoded as space in URL query params
        parsed_start = dt.fromisoformat(scheduled_start.replace(" ", "+"))
        parsed_end = dt.fromisoformat(scheduled_end.replace(" ", "+"))
    except ValueError:
        raise HTTPException(
            status_code=400,
            detail="Invalid scheduled_start or scheduled_end format. Use ISO format.",
        )

    faculty_id = None
    if faculty_email:
        q_fac = await db.execute(select(User).where(User.email == faculty_email))
        fac = q_fac.scalars().first()
        if not fac:
            raise HTTPException(
                status_code=400,
                detail=f"Faculty user with email {faculty_email} does not exist.",
            )
        if fac.role != RoleEnum.FACULTY:
            raise HTTPException(
                status_code=400,
                detail=f"User {faculty_email} does not have the FACULTY role.",
            )
        faculty_id = fac.id

    session = Session(
        course_id=course_id,
        room_id=room_id,
        location=location,
        scheduled_start=parsed_start,
        scheduled_end=parsed_end,
        status=SessionStatus.SCHEDULED,
        faculty_id=faculty_id,
    )
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return {
        "id": str(session.id),
        "course_id": session.course_id,
        "location": session.location,
        "scheduled_start": session.scheduled_start.isoformat(),
        "scheduled_end": session.scheduled_end.isoformat(),
        "status": session.status.value,
        "faculty_email": faculty_email,
    }


@app.post("/sessions/bulk-upload")
async def bulk_upload_timetable(
    entries: List[dict] = Body(...),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    """
    Bulk upload timetable entries and create sessions.
    entries: list of {course_id, room_id, location, scheduled_start, scheduled_end}
    """
    if current_user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Only admins can upload timetable")

    from .models import Session, SessionStatus
    from datetime import datetime as dt

    created = []
    for entry in entries:
        try:
            scheduled_start = (
                dt.fromisoformat(entry.get("scheduled_start"))
                if isinstance(entry.get("scheduled_start"), str)
                else entry.get("scheduled_start")
            )
            scheduled_end = (
                dt.fromisoformat(entry.get("scheduled_end"))
                if isinstance(entry.get("scheduled_end"), str)
                else entry.get("scheduled_end")
            )
        except (ValueError, KeyError, TypeError) as e:
            raise HTTPException(
                status_code=400, detail=f"Invalid entry format: {str(e)}"
            )

        faculty_email = entry.get("faculty_email")
        faculty_id = None
        if faculty_email:
            from .models import User, RoleEnum
            q_fac = await db.execute(select(User).where(User.email == faculty_email))
            fac = q_fac.scalars().first()
            if not fac:
                raise HTTPException(
                    status_code=400,
                    detail=f"Faculty email {faculty_email} not found in database.",
                )
            if fac.role != RoleEnum.FACULTY:
                raise HTTPException(
                    status_code=400,
                    detail=f"User {faculty_email} is not registered with FACULTY role.",
                )
            faculty_id = fac.id

        session = Session(
            course_id=entry.get("course_id"),
            room_id=entry.get("room_id"),
            location=entry.get("location"),
            scheduled_start=scheduled_start,
            scheduled_end=scheduled_end,
            status=SessionStatus.SCHEDULED,
            faculty_id=faculty_id,
        )
        db.add(session)
        created.append(session)

    await db.commit()
    return {
        "created": len(created),
        "sessions": [
            {
                "id": str(s.id),
                "course_id": s.course_id,
                "location": s.location,
                "status": s.status.value,
            }
            for s in created
        ],
    }


@app.patch("/sessions/{session_id}/status")
async def update_session_status(
    session_id: str,
    status: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    """Manually update session status (SCHEDULED, ACTIVE, COMPLETED)."""
    from .models import Session, SessionStatus, RoleEnum

    if current_user.role not in (RoleEnum.ADMIN, RoleEnum.FACULTY):
        raise HTTPException(status_code=403, detail="Only admins and faculty can update session status")

    q = await db.execute(select(Session).where(Session.id == session_id))
    sess = q.scalars().first()
    if not sess:
        raise HTTPException(status_code=404, detail="Session not found")

    try:
        new_status = SessionStatus[status.upper()]
    except KeyError:
        raise HTTPException(status_code=400, detail=f"Invalid status: {status}. Must be SCHEDULED, ACTIVE, or COMPLETED.")

    sess.status = new_status
    now = datetime.datetime.now(datetime.timezone.utc)
    if new_status == SessionStatus.ACTIVE and not sess.actual_start:
        sess.actual_start = now
    elif new_status == SessionStatus.COMPLETED and not sess.actual_end:
        sess.actual_end = now

    db.add(sess)
    await db.commit()
    await db.refresh(sess)
    return {
        "id": str(sess.id),
        "status": sess.status.value,
        "actual_start": sess.actual_start.isoformat() if sess.actual_start else None,
        "actual_end": sess.actual_end.isoformat() if sess.actual_end else None,
    }


@app.get("/sessions")
async def list_sessions(
    db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)
):
    """List all sessions, filtering by assigned faculty if caller has FACULTY role."""
    from .models import Session, User, RoleEnum
    from sqlalchemy import or_

    stmt = select(Session, User.email).outerjoin(User, Session.faculty_id == User.id)

    if current_user.role == RoleEnum.FACULTY:
        stmt = stmt.where(
            or_(
                Session.faculty_id == current_user.id,
                Session.faculty_id.is_(None)
            )
        )

    stmt = stmt.order_by(Session.scheduled_start)
    q = await db.execute(stmt)
    results = q.all()

    return [
        {
            "id": str(s.id),
            "course_id": s.course_id,
            "location": s.location,
            "scheduled_start": s.scheduled_start.isoformat(),
            "scheduled_end": s.scheduled_end.isoformat(),
            "status": s.status.value,
            "faculty_email": email,
        }
        for s, email in results
    ]


@app.post("/admin/promote")
async def promote_to_admin(
    user_id: str = Body(..., embed=True),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    """Promote a user to ADMIN role. Only existing admins can do this."""
    if current_user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Only admins can promote users")

    from uuid import UUID as UUID_type

    try:
        user_uuid = UUID_type(user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid user ID format")

    q = await db.execute(select(User).where(User.id == user_uuid))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    user.role = "ADMIN"
    await db.commit()
    await db.refresh(user)

    return {"user_id": str(user.id), "email": user.email, "role": user.role}


@app.post("/admin/setup", response_model=Token)
async def admin_setup(payload: UserCreate, db: AsyncSession = Depends(get_db)):
    """
    Setup the first admin user. Only works if no admin users exist yet.
    This endpoint allows initialization without requiring an existing admin.
    """
    # Check if any admin exists
    q = await db.execute(select(User).where(User.role == "ADMIN"))
    admin_exists = q.scalars().first() is not None

    if admin_exists:
        raise HTTPException(
            status_code=403, detail="Admin already exists. Use /admin/promote instead"
        )

    # Check if user already exists
    q = await db.execute(select(User).where(User.email == payload.email))
    existing = q.scalars().first()
    if existing:
        # Promote existing user to admin
        existing.role = "ADMIN"
        await db.commit()
        await db.refresh(existing)
        access_token = create_access_token(str(existing.id))
        refresh_token = create_refresh_token(str(existing.id))
        return Token(access_token=access_token, refresh_token=refresh_token)

    # Create new admin user
    user = User(
        email=payload.email,
        password_hash=get_password_hash(payload.password),
        role="ADMIN",
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    access_token = create_access_token(str(user.id))
    refresh_token = create_refresh_token(str(user.id))
    return Token(access_token=access_token, refresh_token=refresh_token)


@app.post("/sessions/{session_id}/override-attendance")
async def override_attendance(
    session_id: str,
    student_id: str,
    override_status: str,
    justification: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    """Admin/Faculty override attendance for a student."""
    if current_user.role not in ["ADMIN", "FACULTY"]:
        raise HTTPException(
            status_code=403, detail="Only admins and faculty can override attendance"
        )

    # Find existing attendance record
    from uuid import UUID as UUID_type

    try:
        student_uuid = UUID_type(student_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid student ID format")

    q = await db.execute(
        select(Attendance).where(
            (Attendance.session_id == session_id)
            & (Attendance.student_id == student_uuid)
        )
    )
    attendance = q.scalars().first()
    session_id_var.set(session_id)
    if not attendance:
        # Create a placeholder attendance record first
        attendance = Attendance(
            student_id=student_uuid,
            session_id=session_id,
            score=0.0,
            status=AttendanceStatus.ABSENT,
        )
        db.add(attendance)
        await db.flush()  # Populate attendance.id

    # Create override record
    override = AttendanceOverride(
        attendance_id=attendance.id,
        admin_id=current_user.id,
        original_status=attendance.status,
        override_status=AttendanceStatus[override_status],
        justification=justification,
    )
    db.add(override)

    # Update attendance status
    attendance.status = AttendanceStatus[override_status]
    await db.commit()

    role_str = current_user.role.value if hasattr(current_user.role, "value") else str(current_user.role)
    logger.info(
        f"AUDIT: User {current_user.id} ({role_str}) overridden student {student_id} attendance in session {session_id} to {override_status}. Justification: {justification}",
        extra={
            "event_type": "audit_override_attendance",
            "admin_id": str(current_user.id),
            "student_id": student_id,
            "session_id": session_id,
            "original_status": override.original_status.value,
            "new_status": override_status,
            "justification": justification,
        },
    )

    return {
        "attendance_id": str(attendance.id),
        "original_status": override.original_status.value,
        "new_status": attendance.status.value,
        "justification": justification,
    }
