import asyncio
import uuid
import datetime
from app.config import settings
from app.models import Base, User, RoleEnum, Session, SessionStatus
from app.db import get_engine, get_sessionmaker

async def main():
    DATABASE_URL = settings.database_url
    print(f"Connecting to database: {DATABASE_URL}")
    engine = get_engine()
    SessionLocal = get_sessionmaker()
    
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        
    async with SessionLocal() as db:
        from sqlalchemy import select
        
        # Check if we have users
        q_users = await db.execute(select(User).limit(1))
        if q_users.scalars().first() is None:
            # Create users
            from app.auth import get_password_hash
            admin = User(
                email="admin@example.com",
                password_hash=get_password_hash("admin123"),
                role=RoleEnum.ADMIN
            )
            faculty = User(
                email="faculty@example.com",
                password_hash=get_password_hash("faculty123"),
                role=RoleEnum.FACULTY
            )
            student = User(
                email="student@example.com",
                password_hash=get_password_hash("student123"),
                role=RoleEnum.STUDENT
            )
            db.add_all([admin, faculty, student])
            await db.commit()
            print("Seeded test users (admin@example.com / admin123, student@example.com / student123).")
            
        # Get/create session
        q_sessions = await db.execute(select(Session))
        sessions = q_sessions.scalars().all()
        
        if not sessions:
            now = datetime.datetime.now(datetime.timezone.utc)
            session = Session(
                course_id="CS101",
                room_id="sim-room-101",
                location="Room 101",
                scheduled_start=now - datetime.timedelta(hours=1),
                scheduled_end=now + datetime.timedelta(hours=2),
                status=SessionStatus.ACTIVE,
                actual_start=now - datetime.timedelta(hours=1)
            )
            db.add(session)
            await db.commit()
            await db.refresh(session)
            sessions = [session]
            print("Created a new active session.")
            
        print("\n=== ACTIVE SESSIONS IN DATABASE ===")
        for s in sessions:
            print(f"Session ID: {s.id} | Course: {s.course_id} | Location: {s.location} | Status: {s.status.value}")
        print("===================================\n")
        
    await engine.dispose()

if __name__ == "__main__":
    asyncio.run(main())
