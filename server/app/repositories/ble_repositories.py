"""BLE database repositories.

These repositories expose database access only. Business rules belong in
services or engines that consume them.
"""

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..models import (
    BleAttendance,
    BleAttendanceStatus,
    BleObservation,
    BleRegisteredDevice,
    BleSession,
    BleSessionStatus,
)


class BleSessionRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, session: BleSession) -> BleSession:
        self.db.add(session)
        await self.db.flush()
        await self.db.refresh(session)
        return session

    async def get_by_id(self, session_id):
        result = await self.db.execute(select(BleSession).where(BleSession.id == session_id))
        return result.scalars().first()

    async def list_by_course(self, course_id):
        result = await self.db.execute(
            select(BleSession)
            .where(BleSession.course_id == course_id)
            .order_by(BleSession.start_time.desc())
        )
        return result.scalars().all()

    async def list_by_teacher(self, teacher_id):
        result = await self.db.execute(
            select(BleSession)
            .where(BleSession.teacher_id == teacher_id)
            .order_by(BleSession.start_time.desc())
        )
        return result.scalars().all()

    async def update_status(self, session_id, status: BleSessionStatus, end_time=None):
        result = await self.db.execute(select(BleSession).where(BleSession.id == session_id))
        session = result.scalars().first()
        if not session:
            return None
        session.status = status
        if end_time is not None:
            session.end_time = end_time
        await self.db.flush()
        await self.db.refresh(session)
        return session


class BleRegisteredDeviceRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, device: BleRegisteredDevice) -> BleRegisteredDevice:
        self.db.add(device)
        await self.db.flush()
        await self.db.refresh(device)
        return device

    async def get_by_id(self, device_id):
        result = await self.db.execute(
            select(BleRegisteredDevice).where(BleRegisteredDevice.id == device_id)
        )
        return result.scalars().first()

    async def get_by_student_id(self, student_id):
        result = await self.db.execute(
            select(BleRegisteredDevice).where(BleRegisteredDevice.student_id == student_id)
        )
        return result.scalars().first()

    async def get_by_anonymous_ble_id(self, anonymous_ble_id):
        result = await self.db.execute(
            select(BleRegisteredDevice).where(
                BleRegisteredDevice.anonymous_ble_id == anonymous_ble_id
            )
        )
        return result.scalars().first()

    async def get_by_device_hash(self, device_hash):
        result = await self.db.execute(
            select(BleRegisteredDevice).where(BleRegisteredDevice.device_hash == device_hash)
        )
        return result.scalars().first()

    async def upsert_for_student(
        self,
        *,
        student_id,
        anonymous_ble_id: str,
        public_identifier: str,
        device_hash: str,
    ) -> BleRegisteredDevice:
        result = await self.db.execute(
            select(BleRegisteredDevice).where(BleRegisteredDevice.student_id == student_id)
        )
        existing = result.scalars().first()
        if existing is None:
            device = BleRegisteredDevice(
                student_id=student_id,
                anonymous_ble_id=anonymous_ble_id,
                public_identifier=public_identifier,
                device_hash=device_hash,
            )
            self.db.add(device)
            await self.db.flush()
            await self.db.refresh(device)
            return device

        existing.anonymous_ble_id = anonymous_ble_id
        existing.public_identifier = public_identifier
        existing.device_hash = device_hash
        await self.db.flush()
        await self.db.refresh(existing)
        return existing

    async def list_by_student_id(self, student_id):
        result = await self.db.execute(
            select(BleRegisteredDevice)
            .where(BleRegisteredDevice.student_id == student_id)
            .order_by(BleRegisteredDevice.created_at.desc())
        )
        return result.scalars().all()

    async def list_all(self):
        result = await self.db.execute(
            select(BleRegisteredDevice).order_by(BleRegisteredDevice.created_at.asc())
        )
        return result.scalars().all()


class BleObservationRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, observation: BleObservation) -> BleObservation:
        self.db.add(observation)
        await self.db.flush()
        await self.db.refresh(observation)
        return observation

    async def create_many(self, observations: list[BleObservation]) -> list[BleObservation]:
        self.db.add_all(observations)
        await self.db.flush()
        for observation in observations:
            await self.db.refresh(observation)
        return observations

    async def get_by_id(self, observation_id):
        result = await self.db.execute(
            select(BleObservation).where(BleObservation.id == observation_id)
        )
        return result.scalars().first()

    async def get_by_session_and_student(self, session_id, student_id):
        result = await self.db.execute(
            select(BleObservation)
            .where(BleObservation.session_id == session_id)
            .where(BleObservation.student_id == student_id)
            .order_by(BleObservation.timestamp.desc())
        )
        return result.scalars().all()

    async def list_by_session(self, session_id):
        result = await self.db.execute(
            select(BleObservation)
            .where(BleObservation.session_id == session_id)
            .order_by(BleObservation.timestamp.asc())
        )
        return result.scalars().all()

    async def list_by_student(self, student_id):
        result = await self.db.execute(
            select(BleObservation)
            .where(BleObservation.student_id == student_id)
            .order_by(BleObservation.timestamp.desc())
        )
        return result.scalars().all()


class BleAttendanceRepository:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create(self, attendance: BleAttendance) -> BleAttendance:
        self.db.add(attendance)
        await self.db.flush()
        await self.db.refresh(attendance)
        return attendance

    async def get_by_id(self, attendance_id):
        result = await self.db.execute(
            select(BleAttendance).where(BleAttendance.id == attendance_id)
        )
        return result.scalars().first()

    async def get_by_session_and_student(self, session_id, student_id):
        result = await self.db.execute(
            select(BleAttendance)
            .where(BleAttendance.session_id == session_id)
            .where(BleAttendance.student_id == student_id)
        )
        return result.scalars().first()

    async def update(self, attendance: BleAttendance) -> BleAttendance:
        existing = await self.get_by_session_and_student(
            attendance.session_id, attendance.student_id
        )
        if existing is None:
            return await self.create(attendance)
        existing.status = attendance.status
        existing.first_seen = attendance.first_seen
        existing.last_seen = attendance.last_seen
        await self.db.flush()
        await self.db.refresh(existing)
        return existing

    async def list_by_session(self, session_id):
        result = await self.db.execute(
            select(BleAttendance)
            .where(BleAttendance.session_id == session_id)
            .order_by(BleAttendance.student_id.asc())
        )
        return result.scalars().all()

    async def upsert(self, attendance: BleAttendance) -> BleAttendance:
        existing = await self.get_by_session_and_student(
            attendance.session_id, attendance.student_id
        )
        if existing:
            existing.status = attendance.status
            existing.first_seen = attendance.first_seen
            existing.last_seen = attendance.last_seen
            await self.db.flush()
            await self.db.refresh(existing)
            return existing
        return await self.create(attendance)
