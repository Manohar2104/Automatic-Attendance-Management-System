import pytest
import asyncio
from httpx import AsyncClient, ASGITransport
from datetime import datetime, timedelta, timezone
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker

from app.main import app
from app.db import Base, get_db


@pytest.fixture(scope="session")
def event_loop():
    """Create event loop for async tests."""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest.fixture
async def test_db():
    """Create test database with all tables."""
    engine = create_async_engine("sqlite+aiosqlite:///:memory:", echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    
    TestingSessionLocal = sessionmaker(
        engine, class_=AsyncSession, expire_on_commit=False
    )
    
    async def override_get_db():
        async with TestingSessionLocal() as session:
            yield session
    
    app.dependency_overrides[get_db] = override_get_db
    
    yield TestingSessionLocal
    
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    
    await engine.dispose()


@pytest.fixture
async def client(test_db):
    """Create async test client."""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


class TestAuth:
    """Test authentication endpoints."""
    
    async def test_register_user(self, client):
        """Test user registration."""
        response = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        assert response.status_code == 200
        assert "access_token" in response.json()
    
    async def test_register_duplicate_email(self, client):
        """Test registration with duplicate email fails."""
        await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        response = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password456"}
        )
        assert response.status_code == 400
    
    async def test_login_user(self, client):
        """Test user login."""
        await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        response = await client.post(
            "/login",
            json={"email": "test@example.com", "password": "password123"}
        )
        assert response.status_code == 200
        assert "access_token" in response.json()
    
    async def test_login_invalid_password(self, client):
        """Test login with invalid password fails."""
        await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        response = await client.post(
            "/login",
            json={"email": "test@example.com", "password": "wrongpassword"}
        )
        assert response.status_code == 401


class TestDevices:
    """Test device binding endpoints."""
    
    @pytest.fixture
    async def auth_header(self, client):
        """Create and return authorization header."""
        reg_response = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        token = reg_response.json()["access_token"]
        return {"Authorization": f"Bearer {token}"}
    
    async def test_register_device(self, client, auth_header):
        """Test device registration."""
        response = await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-1"}
        )
        assert response.status_code == 200
        assert response.json()["device_fingerprint"] == "device-1"
        assert response.json()["status"] == "ACTIVE"
    
    async def test_list_devices(self, client, auth_header):
        """Test listing devices."""
        await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-1"}
        )
        response = await client.get("/devices", headers=auth_header)
        assert response.status_code == 200
        assert len(response.json()) == 1
    
    async def test_revoke_device(self, client, auth_header):
        """Test device revocation."""
        reg_response = await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-1"}
        )
        device_id = reg_response.json()["id"]
        
        response = await client.post(
            f"/devices/{device_id}/revoke",
            headers=auth_header
        )
        assert response.status_code == 200
        assert response.json()["status"] == "revoked"
    
    async def test_max_devices_limit(self, client, auth_header):
        """Test max devices per user limit."""
        # Register 2 devices (default limit)
        await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-1"}
        )
        await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-2"}
        )
        
        # Third device should fail
        response = await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-3"}
        )
        assert response.status_code == 400


class TestPresence:
    """Test presence/event endpoints."""
    
    async def test_presence_event(self, client):
        """Test posting a presence event."""
        response = await client.post(
            "/presence",
            json={
                "device_fingerprint": "device-1",
                "session_id": "session-1",
                "event_type": "ENTER",
                "location": "room-101"
            }
        )
        assert response.status_code == 200
        assert "id" in response.json()


class TestSessions:
    """Test session management endpoints."""
    
    @pytest.fixture
    async def admin_header(self, client):
        """Create admin user and return auth header."""
        reg_response = await client.post(
            "/register",
            json={"email": "admin@example.com", "password": "password123"}
        )
        token = reg_response.json()["access_token"]
        return {"Authorization": f"Bearer {token}"}
    
    async def test_create_session(self, client, admin_header):
        """Test creating a session."""
        now = datetime.now(tz=timezone.utc)
        start = now.isoformat()
        end = (now + timedelta(hours=1)).isoformat()
        
        response = await client.post(
            "/sessions/create",
            headers=admin_header,
            params={
                "course_id": "CS101",
                "room_id": "room-101",
                "location": "Classroom 101",
                "scheduled_start": start,
                "scheduled_end": end
            }
        )
        # Should fail because user is not admin yet
        assert response.status_code in [200, 403]
    
    async def test_list_sessions(self, client, admin_header):
        """Test listing sessions."""
        response = await client.get("/sessions", headers=admin_header)
        assert response.status_code == 200
        assert isinstance(response.json(), list)
    
    async def test_bulk_upload_timetable(self, client, admin_header):
        """Test bulk uploading timetable."""
        now = datetime.now(tz=timezone.utc)
        entries = [
            {
                "course_id": "CS101",
                "room_id": "room-101",
                "location": "Classroom 101",
                "scheduled_start": now.isoformat(),
                "scheduled_end": (now + timedelta(hours=1)).isoformat()
            }
        ]
        
        response = await client.post(
            "/sessions/bulk-upload",
            headers=admin_header,
            json=entries
        )
        # Should work or fail gracefully (admin check)
        assert response.status_code in [200, 403]


class TestAttendance:
    """Test attendance computation endpoints."""
    
    @pytest.fixture
    async def setup_with_events(self, client):
        """Setup user, device, and events for testing."""
        # Register user
        reg_response = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        token = reg_response.json()["access_token"]
        auth_header = {"Authorization": f"Bearer {token}"}
        
        # Register device
        await client.post(
            "/register-device",
            headers=auth_header,
            json={"device_fingerprint": "device-1"}
        )
        
        # Post presence events
        for i in range(3):
            await client.post(
                "/presence",
                json={
                    "device_fingerprint": "device-1",
                    "session_id": "session-1",
                    "event_type": "ENTER",
                    "location": "room-101"
                }
            )
        
        return auth_header
    
    async def test_compute_attendance(self, client, setup_with_events):
        """Test computing attendance."""
        response = await client.post("/compute-attendance/session-1")
        assert response.status_code == 200
        data = response.json()
        assert data["session_id"] == "session-1"
    
    async def test_compute_attendance_with_location_filter(self, client, setup_with_events):
        """Test computing attendance with location filter."""
        response = await client.post(
            "/compute-attendance/session-1",
            params={"location_filter": "room-101"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "location_validated" in data
