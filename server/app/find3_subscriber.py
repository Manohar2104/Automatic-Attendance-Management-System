import asyncio
import json
import logging
from typing import Optional
import websockets

from .config import settings
from .db import get_sessionmaker
from .models import Event, EventType, DeviceBinding, Session as DbSession, SessionStatus
import datetime
from sqlalchemy import select, and_

log = logging.getLogger("find3_subscriber")


class Find3Subscriber:
    def __init__(self, ws_url: str):
        self.ws_url = ws_url
        self._task: Optional[asyncio.Task] = None
        self._stop = asyncio.Event()

    async def start(self):
        if not self.ws_url:
            log.info("find3 ws url not configured; subscriber disabled")
            return
        log.info("starting find3 subscriber")
        try:
            await self._wait_for_db_ready()
        except Exception:
            log.warning("DB readiness check failed; starting subscriber anyway")
        self._task = asyncio.create_task(self._run())

    async def stop(self):
        if self._task:
            self._stop.set()
            await self._task

    async def _run(self):
        while not self._stop.is_set():
            try:
                async with websockets.connect(self.ws_url) as ws:
                    log.info("connected to find3 ws")
                    async for msg in ws:
                        try:
                            data = json.loads(msg)
                        except Exception:
                            continue
                        await self._handle_find3_message(data)
            except Exception as e:
                log.exception('find3 subscriber error, reconnecting in 5s')
                await asyncio.sleep(5)

    async def _handle_find3_message(self, data: dict):
        """Parse real find3 WebSocket payload and persist events."""
        sensors = data.get('sensors', {})
        guesses = data.get('guesses', [])

        if not sensors:
            log.debug("No sensors field in find3 message")
            return

        device_fingerprint = sensors.get('d') or sensors.get('device')
        family = sensors.get('f')
        timestamp_ms = sensors.get('t')
        location = sensors.get('l') or (guesses[0].get('location') if guesses else None)

        if not device_fingerprint or not family:
            log.debug("Missing device or family in find3 message")
            return

        full_fingerprint = f"{family}:{device_fingerprint}"
        event_type = 'ENTER'

        await self._persist_event(full_fingerprint, None, location, event_type, timestamp_ms)

    async def _persist_event(self, device_fingerprint, session_id, location, event_type, timestamp_ms=None):
        if not device_fingerprint:
            return
        SessionLocal = get_sessionmaker()
        async with SessionLocal() as db:
            try:
                q = await db.execute(DeviceBinding.__table__.select().where(DeviceBinding.device_fingerprint == device_fingerprint))
                row = q.first()
                user_id = row.user_id if row else None

                # Resolve session_id from active sessions at this location if not provided
                if not session_id and location:
                    sess_q = await db.execute(
                        select(DbSession).where(
                            and_(
                                DbSession.location == location,
                                DbSession.status == SessionStatus.ACTIVE
                            )
                        )
                    )
                    active_sess = sess_q.scalars().first()
                    if active_sess:
                        session_id = str(active_sess.id)

                ts = datetime.datetime.fromtimestamp(timestamp_ms / 1000, tz=datetime.timezone.utc) if timestamp_ms else datetime.datetime.now(datetime.timezone.utc)

                ev = Event(
                    user_id=user_id,
                    session_id=session_id,
                    type=EventType.ENTER if event_type == 'ENTER' else EventType.LEAVE,
                    location=location,
                    timestamp=ts
                )
                db.add(ev)

                if row:
                    await db.execute(DeviceBinding.__table__.update().where(DeviceBinding.device_fingerprint == device_fingerprint).values(last_seen_at=ts, status='ACTIVE'))
                await db.commit()
            except Exception as e:
                log.warning("skipping persist_event due to DB error: %s", e)
                try:
                    await db.rollback()
                except Exception:
                    pass
                return

    async def _wait_for_db_ready(self, timeout: int = 10):
        SessionLocal = get_sessionmaker()
        start = asyncio.get_event_loop().time()
        while True:
            try:
                async with SessionLocal() as db:
                    await db.execute(DeviceBinding.__table__.select().limit(1))
                    return
            except Exception:
                if asyncio.get_event_loop().time() - start > timeout:
                    raise
                await asyncio.sleep(0.5)


subscriber: Optional[Find3Subscriber] = None

def init_subscriber(app):
    global subscriber
    ws = getattr(settings, 'find3_ws_url', None)
    if not ws:
        return
    subscriber = Find3Subscriber(ws)
    app.state.find3_subscriber = subscriber

async def start_subscriber(app):
    if getattr(app.state, 'find3_subscriber', None):
        await app.state.find3_subscriber.start()

async def stop_subscriber(app):
    if getattr(app.state, 'find3_subscriber', None):
        await app.state.find3_subscriber.stop()
