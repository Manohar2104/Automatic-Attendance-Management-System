from fastapi import FastAPI, Depends, HTTPException, Body, Header
from typing import List
from contextlib import asynccontextmanager
from .schemas import HealthCheck, UserCreate, Token
from .db import Base, get_db
from .config import settings
from .models import User
from .auth import get_password_hash, verify_password, create_access_token, create_refresh_token
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
import asyncio
import datetime
from .schemas import DeviceRegister, PresenceEvent, DeviceInfo
from .models import DeviceBinding, Event, EventType, Attendance, AttendanceStatus, AttendanceOverride
from sqlalchemy import insert
import uuid
import time
from .auth import require_current_user, get_current_user
from sqlalchemy.exc import IntegrityError
from .config import settings
import logging
from .logging_config import setup_logging, request_id_var, user_id_var, session_id_var, endpoint_var

# Initialize structured logging configuration immediately on app startup
setup_logging()

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

# initialize find3 subscriber if configured
from .find3_subscriber import init_subscriber, start_subscriber, stop_subscriber
from .session_scheduler import check_and_process_sessions
from .rate_limit import get_redis, close_redis, is_rate_limited, blacklist_token, is_token_blacklisted

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
        
        # Determine rate limit based on path
        path = request.url.path
        
        if path == "/presence":
            requests_per_minute = 300  # 5 per second
        elif path in ["/register", "/login"]:
            requests_per_minute = 60   # 1 per second
        else:
            requests_per_minute = 100  # ~1.67 per second
        
        # Check rate limit
        if await is_rate_limited(client_ip, path, requests_per_minute):
            return Response(
                content='{"detail":"Rate limit exceeded"}',
                status_code=429,
                media_type="application/json"
            )
        
        response = await call_next(request)
        return response


class StructuredLoggingMiddleware(BaseHTTPMiddleware):
    """Middleware to inject context variables and log request lifecycle in JSON."""
    
    async def dispatch(self, request: Request, call_next) -> Response:
        req_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
        token_request_id = request_id_var.set(req_id)
        token_endpoint = endpoint_var.set(request.url.path)
        
        # Reset user and session context
        token_user = user_id_var.set("")
        token_session = session_id_var.set("")
        
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
                }
            )


# Apply rate limiting middleware
app.add_middleware(RateLimitMiddleware)

# Apply structured logging middleware (runs outermost to capture rate limit blocks too)
app.add_middleware(StructuredLoggingMiddleware)


@app.get("/health", response_model=HealthCheck)
async def health(db: AsyncSession = Depends(get_db)):
    now = datetime.datetime.now(datetime.timezone.utc)
    return HealthCheck(status="ok", now=now)


@app.post("/register", response_model=Token)
async def register(payload: UserCreate, db: AsyncSession = Depends(get_db)):
    # rudimentary registration
    q = await db.execute(select(User).where(User.email == payload.email))
    existing = q.scalars().first()
    if existing:
        raise HTTPException(status_code=400, detail="Email already registered")
    user = User(email=payload.email, password_hash=get_password_hash(payload.password))
    db.add(user)
    await db.commit()
    await db.refresh(user)
    user_id_var.set(str(user.id))
    logger.info(f"User registered successfully: {payload.email}", extra={"user_email": payload.email, "user_id": str(user.id)})
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
    logger.info(f"User logged in successfully: {payload.email}", extra={"user_email": payload.email, "user_id": str(user.id)})
    access_token = create_access_token(str(user.id))
    refresh_token = create_refresh_token(str(user.id))
    return Token(access_token=access_token, refresh_token=refresh_token)


@app.post("/refresh", response_model=Token)
async def refresh(authorization: str = Header(None), db: AsyncSession = Depends(get_db)):
    """Refresh access token using a refresh token."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid auth header")
    token = authorization[7:]
    
    try:
        from .auth import settings as auth_settings
        from jose import jwt
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        user_id = payload.get("sub")
        token_type = payload.get("type")
        
        if token_type != "refresh":
            raise HTTPException(status_code=401, detail="Invalid token type. Use refresh token")
        
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
        raise HTTPException(status_code=401, detail="Refresh token expired. Please login again")
    except Exception as e:
        raise HTTPException(status_code=401, detail="Invalid refresh token")


@app.post("/logout")
async def logout(authorization: str = Header(None), current_user=Depends(require_current_user)):
    """Logout and blacklist the access token."""
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing authorization header")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid auth header")
    token = authorization[7:]
    
    # Blacklist the token
    await blacklist_token(token, ttl_seconds=900)  # 15 minutes
    return {"message": "Logged out successfully"}


@app.post('/register-device')
async def register_device(payload: DeviceRegister, db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)):
    # Attach a device to the current authenticated user, enforcing max devices
    q = await db.execute(select(DeviceBinding).where(DeviceBinding.user_id == current_user.id, DeviceBinding.status == 'ACTIVE'))
    bound = q.scalars().all()
    if len(bound) >= settings.max_devices_per_user:
        raise HTTPException(status_code=400, detail=f"Max devices ({settings.max_devices_per_user}) reached")
    # ensure fingerprint isn't already bound
    q2 = await db.execute(select(DeviceBinding).where(DeviceBinding.device_fingerprint == payload.device_fingerprint))
    existing = q2.scalars().first()
    if existing:
        # if already bound to another user, reject
        if existing.user_id != current_user.id:
            raise HTTPException(status_code=400, detail="Device fingerprint already registered to another user")
        return {"id": str(existing.id), "device_fingerprint": existing.device_fingerprint}
    binding = DeviceBinding(user_id=current_user.id, device_fingerprint=payload.device_fingerprint)
    db.add(binding)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(status_code=409, detail="Device fingerprint already registered")
    await db.refresh(binding)
    return DeviceInfo(id=str(binding.id), device_fingerprint=binding.device_fingerprint, status=binding.status.value, last_seen_at=binding.last_seen_at)


@app.get('/devices')
async def list_devices(db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)):
    q = await db.execute(select(DeviceBinding).where(DeviceBinding.user_id == current_user.id))
    rows = q.scalars().all()
    out = []
    for r in rows:
        out.append(DeviceInfo(id=str(r.id), device_fingerprint=r.device_fingerprint, status=r.status.value, last_seen_at=r.last_seen_at))
    return out


@app.post('/devices/{device_id}/revoke')
async def revoke_device(device_id: str, db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)):
    from uuid import UUID as UUID_type
    try:
        device_uuid = UUID_type(device_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid device ID format")
    
    q = await db.execute(select(DeviceBinding).where(DeviceBinding.id == device_uuid))
    binding = q.scalars().first()
    if not binding:
        raise HTTPException(status_code=404, detail="Device not found")
    if binding.user_id != current_user.id and current_user.role != 'ADMIN':
        raise HTTPException(status_code=403, detail="Forbidden")
    binding.status = 'REVOKED'
    binding.revoked_at = datetime.datetime.now(datetime.timezone.utc)
    binding.revoked_by = current_user.id
    await db.commit()
    
    logger.info(
        f"AUDIT: Device {device_id} revoked by user {current_user.id}",
        extra={
            "event_type": "audit_device_revocation",
            "device_id": device_id,
            "revoked_by": str(current_user.id),
            "owner_id": str(binding.user_id) if binding.user_id else ""
        }
    )
    
    return {"status": "revoked"}


@app.post('/presence')
async def presence_event(payload: PresenceEvent, db: AsyncSession = Depends(get_db)):
    # Record an incoming presence event from a device
    # Resolve user from device_fingerprint
    q = await db.execute(select(DeviceBinding).where(DeviceBinding.device_fingerprint == payload.device_fingerprint))
    binding = q.scalars().first()
    user_id = binding.user_id if binding else None
    # don't invent a user_id for anonymous devices; leave null
    ev = Event(user_id=user_id, session_id=payload.session_id, type=EventType.ENTER if payload.event_type == 'ENTER' else EventType.LEAVE, location=payload.location)
    db.add(ev)
    # update device binding last_seen and status if bound
    if binding:
        binding.last_seen_at = datetime.datetime.now(datetime.timezone.utc)
        binding.status = binding.status or 'ACTIVE'
        db.add(binding)
    await db.commit()
    await db.refresh(ev)
    return {"id": str(ev.id)}


@app.post('/compute-attendance/{session_id}')
async def compute_attendance(session_id: str, location_filter: str = None, db: AsyncSession = Depends(get_db)):
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

    results = []
    bound_count = 0
    for user_id, evs in user_events.items():
        bound_count += 1
        enter_count = sum(1 for e in evs if e.type == EventType.ENTER)
        raw_score = min(100, int((enter_count / max_possible) * 100)) if max_possible else 0
        status_enum = AttendanceStatus.PRESENT if raw_score >= settings.present_threshold_percent else AttendanceStatus.PARTIAL if raw_score >= settings.partial_threshold_percent else AttendanceStatus.ABSENT
        attendance = Attendance(student_id=user_id, session_id=session_id, score=raw_score, status=status_enum)
        db.add(attendance)
        location_match = location_filter is None or any(e.location == location_filter for e in evs)
        results.append(AttendanceResult(
            user_id=str(user_id),
            score=raw_score,
            status=status_enum.value,
            enter_count=enter_count,
            location_match=location_match
        ))

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
            "crowd_ok": crowd_ok
        }
    )
    
    return ComputeAttendanceResponse(
        session_id=session_id,
        duration_seconds=duration_seconds,
        max_possible_submissions=max_possible,
        bound_devices=bound_count,
        crowd_ok=crowd_ok,
        location_validated=location_validated,
        results=results
    )


@app.post('/sessions/create')
async def create_session(
    course_id: str,
    room_id: str,
    location: str,
    scheduled_start: str,
    scheduled_end: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    """Create a new session. Only admin can create."""
    if current_user.role != 'ADMIN':
        raise HTTPException(status_code=403, detail="Only admins can create sessions")
    
    from .models import Session, SessionStatus
    from datetime import datetime as dt
    
    session = Session(
        course_id=course_id,
        room_id=room_id,
        location=location,
        scheduled_start=dt.fromisoformat(scheduled_start),
        scheduled_end=dt.fromisoformat(scheduled_end),
        status=SessionStatus.SCHEDULED
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
        "status": session.status.value
    }


@app.post('/sessions/bulk-upload')
async def bulk_upload_timetable(
    entries: List[dict] = Body(...),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    """
    Bulk upload timetable entries and create sessions.
    entries: list of {course_id, room_id, location, scheduled_start, scheduled_end}
    """
    if current_user.role != 'ADMIN':
        raise HTTPException(status_code=403, detail="Only admins can upload timetable")
    
    from .models import Session, SessionStatus
    from datetime import datetime as dt
    
    created = []
    for entry in entries:
        try:
            scheduled_start = dt.fromisoformat(entry.get('scheduled_start')) if isinstance(entry.get('scheduled_start'), str) else entry.get('scheduled_start')
            scheduled_end = dt.fromisoformat(entry.get('scheduled_end')) if isinstance(entry.get('scheduled_end'), str) else entry.get('scheduled_end')
        except (ValueError, KeyError, TypeError) as e:
            raise HTTPException(status_code=400, detail=f"Invalid entry format: {str(e)}")
        
        session = Session(
            course_id=entry.get('course_id'),
            room_id=entry.get('room_id'),
            location=entry.get('location'),
            scheduled_start=scheduled_start,
            scheduled_end=scheduled_end,
            status=SessionStatus.SCHEDULED
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
                "status": s.status.value
            }
            for s in created
        ]
    }


@app.get('/sessions')
async def list_sessions(db: AsyncSession = Depends(get_db), current_user=Depends(require_current_user)):
    """List all sessions."""
    from .models import Session
    q = await db.execute(select(Session).order_by(Session.scheduled_start))
    sessions = q.scalars().all()
    return [
        {
            "id": str(s.id),
            "course_id": s.course_id,
            "location": s.location,
            "scheduled_start": s.scheduled_start.isoformat(),
            "scheduled_end": s.scheduled_end.isoformat(),
            "status": s.status.value
        }
        for s in sessions
    ]


@app.post('/admin/promote')
async def promote_to_admin(
    user_id: str = Body(..., embed=True),
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    """Promote a user to ADMIN role. Only existing admins can do this."""
    if current_user.role != 'ADMIN':
        raise HTTPException(status_code=403, detail="Only admins can promote users")
    
    q = await db.execute(select(User).where(User.id == UUID(user_id)))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    user.role = 'ADMIN'
    await db.commit()
    await db.refresh(user)
    
    return {
        "user_id": str(user.id),
        "email": user.email,
        "role": user.role
    }


@app.post('/admin/setup', response_model=Token)
async def admin_setup(
    payload: UserCreate,
    db: AsyncSession = Depends(get_db)
):
    """
    Setup the first admin user. Only works if no admin users exist yet.
    This endpoint allows initialization without requiring an existing admin.
    """
    # Check if any admin exists
    q = await db.execute(select(User).where(User.role == 'ADMIN'))
    admin_exists = q.scalars().first() is not None
    
    if admin_exists:
        raise HTTPException(status_code=403, detail="Admin already exists. Use /admin/promote instead")
    
    # Check if user already exists
    q = await db.execute(select(User).where(User.email == payload.email))
    existing = q.scalars().first()
    if existing:
        # Promote existing user to admin
        existing.role = 'ADMIN'
        await db.commit()
        await db.refresh(existing)
        access_token = create_access_token(str(existing.id))
        refresh_token = create_refresh_token(str(existing.id))
        return Token(access_token=access_token, refresh_token=refresh_token)
    
    # Create new admin user
    user = User(
        email=payload.email,
        password_hash=get_password_hash(payload.password),
        role='ADMIN'
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)
    access_token = create_access_token(str(user.id))
    refresh_token = create_refresh_token(str(user.id))
    return Token(access_token=access_token, refresh_token=refresh_token)


@app.post('/sessions/{session_id}/override-attendance')
async def override_attendance(
    session_id: str,
    student_id: str,
    override_status: str,
    justification: str,
    db: AsyncSession = Depends(get_db),
    current_user=Depends(require_current_user)
):
    """Admin override attendance for a student."""
    if current_user.role != 'ADMIN':
        raise HTTPException(status_code=403, detail="Only admins can override attendance")
    
    # Find existing attendance record
    from uuid import UUID as UUID_type
    try:
        student_uuid = UUID_type(student_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid student ID format")

    q = await db.execute(
        select(Attendance).where(
            (Attendance.session_id == session_id) &
            (Attendance.student_id == student_uuid)
        )
    )
    attendance = q.scalars().first()
    session_id_var.set(session_id)
    if not attendance:
        raise HTTPException(status_code=404, detail="Attendance record not found")
    
    # Create override record
    from .models import AttendanceOverride
    override = AttendanceOverride(
        attendance_id=attendance.id,
        admin_id=current_user.id,
        original_status=attendance.status,
        override_status=AttendanceStatus[override_status],
        justification=justification
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
            "justification": justification
        }
    )
    
    return {
        "attendance_id": str(attendance.id),
        "original_status": override.original_status.value,
        "new_status": attendance.status.value,
        "justification": justification
    }
