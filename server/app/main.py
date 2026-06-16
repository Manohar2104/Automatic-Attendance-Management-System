from fastapi import FastAPI, Depends, HTTPException
from .schemas import HealthCheck, UserCreate, Token
from .db import engine, Base, get_db
from .config import settings
from .models import User
from .auth import get_password_hash, verify_password, create_access_token
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
import asyncio
import datetime
from .schemas import DeviceRegister, PresenceEvent
from .models import DeviceBinding, Event, EventType, Attendance, AttendanceStatus, AttendanceOverride
from sqlalchemy import insert
import uuid

app = FastAPI(title="Smart Attendance Registry")


@app.on_event("startup")
async def startup():
    # create tables if they don't exist
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


@app.get("/health", response_model=HealthCheck)
async def health(db: AsyncSession = Depends(get_db)):
    now = datetime.datetime.utcnow()
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
    token = create_access_token(str(user.id))
    return Token(access_token=token)


@app.post("/login", response_model=Token)
async def login(payload: UserCreate, db: AsyncSession = Depends(get_db)):
    q = await db.execute(select(User).where(User.email == payload.email))
    user = q.scalars().first()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token = create_access_token(str(user.id))
    return Token(access_token=token)


@app.post('/register-device')
async def register_device(payload: DeviceRegister, db: AsyncSession = Depends(get_db)):
    # naive device registration: attach to a user via token in header (not implemented here)
    # For now, just create a DeviceBinding without user association to allow testing
    binding = DeviceBinding(user_id=None, device_fingerprint=payload.device_fingerprint)
    db.add(binding)
    await db.commit()
    await db.refresh(binding)
    return {"id": str(binding.id), "device_fingerprint": binding.device_fingerprint}


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
    await db.commit()
    await db.refresh(ev)
    return {"id": str(ev.id)}


@app.post('/compute-attendance/{session_id}')
async def compute_attendance(session_id: str, db: AsyncSession = Depends(get_db)):
    # Very small confidence engine: counts ENTER events per user in session and assigns status
    q = await db.execute(select(Event).where(Event.session_id == session_id))
    events = q.scalars().all()

    counts: dict = {}
    for e in events:
        # ignore anonymous events (no bound user)
        if not e.user_id:
            continue
        if e.type == EventType.ENTER:
            counts.setdefault(e.user_id, 0)
            counts[e.user_id] += 1

    results = []
    for user_id, cnt in counts.items():
        raw_score = min(100, cnt * 10)
        status_enum = AttendanceStatus.PRESENT if raw_score >= 85 else AttendanceStatus.PARTIAL if raw_score >= 60 else AttendanceStatus.ABSENT
        attendance = Attendance(student_id=user_id, session_id=session_id, score=raw_score, status=status_enum)
        db.add(attendance)
        results.append({"user_id": str(user_id), "score": raw_score, "status": status_enum.value})
    await db.commit()
    return {"session_id": session_id, "results": results}
