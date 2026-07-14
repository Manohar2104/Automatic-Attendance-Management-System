# ruff: noqa: E402
from fastapi import FastAPI, Depends, HTTPException, Body, Header
from typing import List, Optional
from contextlib import asynccontextmanager
from .schemas import HealthCheck, UserCreate, Token, UserMeResponse, TimetableEntryCreate, TimetableEntryResponse
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
from sqlalchemy import select
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
        await asyncio.sleep(60)  # Run every minute


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
            await conn.execute(text("ALTER TABLE sessions ADD COLUMN teacher_id UUID"))
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
    q = await db.execute(select(User).where(User.email == payload.email))
    existing = q.scalars().first()
    if existing:
        raise HTTPException(status_code=400, detail="Email already registered")
    from .models import RoleEnum
    role_name = (payload.role or "STUDENT").upper()
    try:
        user_role = RoleEnum[role_name]
    except KeyError:
        raise HTTPException(status_code=400, detail="Invalid role specified")
    user = User(
        email=payload.email,
        password_hash=get_password_hash(payload.password),
        role=user_role
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
        # if already bound to another user, reject
        if existing.user_id != current_user.id:
            raise HTTPException(
                status_code=400,
                detail="Device fingerprint already registered to another user",
            )
        return {
            "id": str(existing.id),
            "device_fingerprint": existing.device_fingerprint,
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

    # Resolve event timestamp
    ts = payload.timestamp or datetime.datetime.now(datetime.timezone.utc)
    session_id = payload.session_id

    # If it is a teacher entering, try auto-starting a timetable session
    from .models import RoleEnum, Session, SessionStatus, EventType
    from sqlalchemy import and_
    if user_id and payload.event_type == "ENTER" and payload.location:
        q_user = await db.execute(select(User).where(User.id == user_id))
        user_obj = q_user.scalars().first()
        if user_obj and user_obj.role == RoleEnum.FACULTY:
            from .session_scheduler import auto_start_timetable_session
            started_sess = await auto_start_timetable_session(db, user_id, payload.location, ts)
            if started_sess:
                session_id = str(started_sess.id)

    # Resolve active session from location if not explicitly provided
    if not session_id and payload.location:
        sess_q = await db.execute(
            select(Session).where(
                and_(
                    Session.location == payload.location,
                    Session.status == SessionStatus.ACTIVE,
                )
            )
        )
        active_sess = sess_q.scalars().first()
        if active_sess:
            session_id = str(active_sess.id)

    # Create event
    ev = Event(
        user_id=user_id,
        session_id=session_id,
        type=EventType.ENTER if payload.event_type == "ENTER" else EventType.LEAVE,
        location=payload.location,
        timestamp=ts,
    )
    db.add(ev)
    # update device binding last_seen and status if bound
    if binding:
        binding.last_seen_at = ts
        binding.status = binding.status or "ACTIVE"
        db.add(binding)
    await db.commit()
    await db.refresh(ev)
    return {"id": str(ev.id)}


@app.post("/compute-attendance/{session_id}")
async def compute_attendance(
    session_id: str, location_filter: str = None, db: AsyncSession = Depends(get_db)
):
    """
    Compute attendance for a session with location-based validation.
    If location_filter is provided, only count events from that location.
    """
    from .schemas import ComputeAttendanceResponse, AttendanceResult

    q = await db.execute(select(Event).where(Event.session_id == session_id))
    events = q.scalars().all()

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

    results = []
    bound_count = 0
    for user_id in all_student_ids:
        evs = user_events.get(user_id, [])
        if evs:
            bound_count += 1
        enter_count = sum(1 for e in evs if e.type == EventType.ENTER)
        raw_score = (
            min(100, int((enter_count / max_possible) * 100)) if max_possible else 0
        )
        status_enum = (
            AttendanceStatus.PRESENT
            if raw_score >= settings.present_threshold_percent
            else (
                AttendanceStatus.PARTIAL
                if raw_score >= settings.partial_threshold_percent
                else AttendanceStatus.ABSENT
            )
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
        results.append(
            AttendanceResult(
                user_id=str(user_id),
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
    teacher_id: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user),
):
    """Create a new session. Only admin can create."""
    if current_user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Only admins can create sessions")

    from .models import Session, SessionStatus
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

    t_uuid = None
    if teacher_id:
        try:
            t_uuid = uuid.UUID(teacher_id)
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid teacher_id format")

    session = Session(
        course_id=course_id,
        room_id=room_id,
        location=location,
        scheduled_start=parsed_start,
        scheduled_end=parsed_end,
        status=SessionStatus.SCHEDULED,
        teacher_id=t_uuid
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

        t_uuid = None
        if entry.get("teacher_id"):
            try:
                t_uuid = uuid.UUID(entry.get("teacher_id"))
            except ValueError:
                raise HTTPException(status_code=400, detail="Invalid teacher_id format in bulk upload")

        session = Session(
            course_id=entry.get("course_id"),
            room_id=entry.get("room_id"),
            location=entry.get("location"),
            scheduled_start=scheduled_start,
            scheduled_end=scheduled_end,
            status=SessionStatus.SCHEDULED,
            teacher_id=t_uuid
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


@app.get("/sessions")
async def list_sessions(
    db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)
):
    """List all sessions."""
    from .models import Session

    if current_user.role == "FACULTY":
        q = await db.execute(
            select(Session).where(
                (Session.teacher_id == current_user.id) | (Session.teacher_id == None)
            ).order_by(Session.scheduled_start)
        )
    else:
        q = await db.execute(select(Session).order_by(Session.scheduled_start))
        
    sessions = q.scalars().all()
    return [
        {
            "id": str(s.id),
            "course_id": s.course_id,
            "location": s.location,
            "scheduled_start": s.scheduled_start.isoformat(),
            "scheduled_end": s.scheduled_end.isoformat(),
            "status": s.status.value,
            "teacher_id": str(s.teacher_id) if s.teacher_id else None,
        }
        for s in sessions
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
    """Admin override attendance for a student."""
    # Check authorization: ADMIN or FACULTY who is the teacher of this session
    from uuid import UUID as UUID_type
    try:
        session_uuid = UUID_type(session_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid session ID format")

    from .models import Session
    sess_q = await db.execute(select(Session).where(Session.id == session_uuid))
    session_obj = sess_q.scalars().first()
    if not session_obj:
        raise HTTPException(status_code=404, detail="Session not found")

    if current_user.role == "FACULTY":
        if session_obj.teacher_id != current_user.id:
            raise HTTPException(
                status_code=403, detail="Forbidden: You can only override attendance for your own sessions"
            )
    elif current_user.role != "ADMIN":
        raise HTTPException(
            status_code=403, detail="Only admins and course teachers can override attendance"
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

    logger.info(
        f"AUDIT: Admin {current_user.id} overridden student {student_id} attendance in session {session_id} to {override_status}. Justification: {justification}",
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


@app.get("/users/me", response_model=UserMeResponse)
async def get_me(current_user=Depends(require_current_user)):
    role_str = current_user.role.value if hasattr(current_user.role, "value") else str(current_user.role)
    return UserMeResponse(
        id=str(current_user.id),
        email=current_user.email,
        role=role_str
    )


@app.get("/students", response_model=List[UserMeResponse])
async def list_students(
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    if current_user.role not in ["FACULTY", "ADMIN"]:
        raise HTTPException(status_code=403, detail="Only faculty and admins can view the student list")
    
    from .models import RoleEnum
    q = await db.execute(select(User).where(User.role == RoleEnum.STUDENT).order_by(User.email))
    rows = q.scalars().all()
    return [
        UserMeResponse(
            id=str(r.id),
            email=r.email,
            role=r.role.value if hasattr(r.role, "value") else str(r.role)
        )
        for r in rows
    ]



@app.post("/timetable", response_model=TimetableEntryResponse)
async def create_timetable_entry(
    payload: TimetableEntryCreate,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    if current_user.role not in ["FACULTY", "ADMIN"]:
        raise HTTPException(status_code=403, detail="Only faculty and admins can manage the timetable")
    
    from .models import TimetableEntry
    entry = TimetableEntry(
        teacher_id=current_user.id,
        course_id=payload.course_id,
        room_id=payload.room_id,
        location=payload.location,
        day_of_week=payload.day_of_week,
        start_time=payload.start_time,
        end_time=payload.end_time
    )
    db.add(entry)
    await db.commit()
    await db.refresh(entry)
    return TimetableEntryResponse(
        id=str(entry.id),
        teacher_id=str(entry.teacher_id),
        course_id=entry.course_id,
        room_id=entry.room_id,
        location=entry.location,
        day_of_week=entry.day_of_week,
        start_time=entry.start_time,
        end_time=entry.end_time
    )


@app.get("/timetable", response_model=List[TimetableEntryResponse])
async def list_timetable_entries(
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    if current_user.role not in ["FACULTY", "ADMIN"]:
        raise HTTPException(status_code=403, detail="Only faculty and admins can view the timetable")
    
    from .models import TimetableEntry
    if current_user.role == "ADMIN":
        q = await db.execute(select(TimetableEntry).order_by(TimetableEntry.day_of_week, TimetableEntry.start_time))
    else:
        q = await db.execute(select(TimetableEntry).where(TimetableEntry.teacher_id == current_user.id).order_by(TimetableEntry.day_of_week, TimetableEntry.start_time))
    
    rows = q.scalars().all()
    return [
        TimetableEntryResponse(
            id=str(r.id),
            teacher_id=str(r.teacher_id),
            course_id=r.course_id,
            room_id=r.room_id,
            location=r.location,
            day_of_week=r.day_of_week,
            start_time=r.start_time,
            end_time=r.end_time
        )
        for r in rows
    ]


@app.delete("/timetable/{entry_id}")
async def delete_timetable_entry(
    entry_id: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    if current_user.role not in ["FACULTY", "ADMIN"]:
        raise HTTPException(status_code=403, detail="Only faculty and admins can delete timetable entries")
    
    from .models import TimetableEntry
    try:
        entry_uuid = uuid.UUID(entry_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid timetable entry ID")
        
    q = await db.execute(select(TimetableEntry).where(TimetableEntry.id == entry_uuid))
    entry = q.scalars().first()
    if not entry:
        raise HTTPException(status_code=404, detail="Timetable entry not found")
        
    if current_user.role != "ADMIN" and entry.teacher_id != current_user.id:
        raise HTTPException(status_code=403, detail="Forbidden: You can only delete your own timetable entries")
        
    await db.delete(entry)
    await db.commit()
    return {"status": "deleted"}


@app.post("/timetable/bulk")
async def bulk_create_timetable(
    entries: List[TimetableEntryCreate] = Body(...),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    if current_user.role not in ["FACULTY", "ADMIN"]:
        raise HTTPException(status_code=403, detail="Only faculty and admins can bulk upload timetables")
        
    from .models import TimetableEntry
    created = []
    for payload in entries:
        entry = TimetableEntry(
            teacher_id=current_user.id,
            course_id=payload.course_id,
            room_id=payload.room_id,
            location=payload.location,
            day_of_week=payload.day_of_week,
            start_time=payload.start_time,
            end_time=payload.end_time
        )
        db.add(entry)
        created.append(entry)
        
    await db.commit()
    return {"created": len(created)}


@app.get("/student/attendance")
async def get_student_attendance(
    date: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    if current_user.role not in ["STUDENT", "ADMIN"]:
        raise HTTPException(status_code=403, detail="Only students can view daily attendance report")
        
    try:
        if date:
            target_date = datetime.date.fromisoformat(date)
        else:
            target_date = datetime.datetime.now().date()
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid date format. Use YYYY-MM-DD")
        
    day_start = datetime.datetime.combine(target_date, datetime.time.min).astimezone()
    day_end = datetime.datetime.combine(target_date, datetime.time.max).astimezone()
    day_start_utc = day_start.astimezone(datetime.timezone.utc)
    day_end_utc = day_end.astimezone(datetime.timezone.utc)
    
    from .models import Session, Attendance, Event
    from sqlalchemy import cast, String, and_
    
    sess_stmt = select(Session).where(
        and_(
            Session.scheduled_start >= day_start_utc,
            Session.scheduled_start <= day_end_utc
        )
    ).order_by(Session.scheduled_start)
    
    sess_result = await db.execute(sess_stmt)
    sessions = sess_result.scalars().all()
    
    session_ids = [str(s.id) for s in sessions]
    att_stmt = select(Attendance).where(
        and_(
            Attendance.student_id == current_user.id,
            Attendance.session_id.in_(session_ids)
        )
    )
    att_result = await db.execute(att_stmt)
    attendances = {a.session_id: a for a in att_result.scalars().all()}
    
    results = []
    for s in sessions:
        sess_id_str = str(s.id)
        att = attendances.get(sess_id_str)
        
        if att:
            score = att.score
            status = att.status.value if hasattr(att.status, "value") else str(att.status)
        else:
            if s.status in ["ACTIVE", "COMPLETED"]:
                ev_stmt = select(Event).where(
                    and_(
                        Event.user_id == current_user.id,
                        Event.session_id == sess_id_str
                    )
                )
                ev_result = await db.execute(ev_stmt)
                student_events = ev_result.scalars().all()
                
                enter_count = sum(1 for e in student_events if e.type == "ENTER")
                
                duration = int((s.scheduled_end - s.scheduled_start).total_seconds())
                max_checks = max(1, duration // settings.submission_interval_seconds)
                score = min(100.0, float((enter_count / max_checks) * 100))
                
                if score >= settings.present_threshold_percent:
                    status = "PRESENT"
                elif score >= settings.partial_threshold_percent:
                    status = "PARTIAL"
                else:
                    status = "ABSENT"
            else:
                score = 0.0
                status = "ABSENT"
                
        results.append({
            "session_id": sess_id_str,
            "course_id": s.course_id,
            "location": s.location,
            "status": status,
            "score": score,
            "scheduled_start": s.scheduled_start.isoformat(),
            "scheduled_end": s.scheduled_end.isoformat(),
            "actual_start": s.actual_start.isoformat() if s.actual_start else None,
            "actual_end": s.actual_end.isoformat() if s.actual_end else None,
            "session_status": s.status.value if hasattr(s.status, "value") else str(s.status)
        })
        
    return results
