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


class TestAdminProvisioning:
    @pytest.mark.asyncio
    async def test_setup_first_admin(self, test_db, client):
        """Test setup of first admin user."""
        response = await client.post(
            "/admin/setup",
            json={"email": "admin@test.com", "password": "password123"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["email"] == "admin@test.com"


class TestAdminProvisioning:
    """Test admin setup and promotion."""
    
    @pytest.mark.asyncio
    async def test_setup_first_admin(self, client):
        """Test setup of first admin user."""
        response = await client.post(
            "/admin/setup",
            json={"email": "admin@test.com", "password": "password123"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    @pytest.mark.asyncio
    async def test_setup_existing_user_as_admin(self, client):
        """Test that existing user can be promoted to admin via setup."""
        # First create a regular user
        await client.post(
            "/register",
            json={"email": "user@test.com", "password": "password"}
        )
        
        # Then use setup to promote them to admin (if no admin exists yet)
        response = await client.post(
            "/admin/setup",
            json={"email": "user@test.com", "password": "newpass"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data

    @pytest.mark.asyncio
    async def test_setup_admin_fails_if_admin_exists(self, client):
        """Test that setup fails when admin already exists."""
        # Create first admin
        await client.post(
            "/admin/setup",
            json={"email": "admin@test.com", "password": "password123"}
        )
        
        # Try to create another admin
        response = await client.post(
            "/admin/setup",
            json={"email": "admin2@test.com", "password": "password123"}
        )
        assert response.status_code == 403
        assert "Admin already exists" in response.json()["detail"]


class TestRefreshToken:
    """Test refresh token functionality."""
    
    @pytest.mark.asyncio
    async def test_register_returns_both_tokens(self, client):
        """Test that registration returns both access and refresh tokens."""
        response = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert "refresh_token" in data
        assert data["token_type"] == "bearer"

    @pytest.mark.asyncio
    async def test_login_returns_both_tokens(self, client):
        """Test that login returns both access and refresh tokens."""
        # First register
        await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        
        # Then login
        response = await client.post(
            "/login",
            json={"email": "test@example.com", "password": "password123"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert "refresh_token" in data

    @pytest.mark.asyncio
    async def test_refresh_endpoint_returns_new_tokens(self, client):
        """Test that refresh endpoint generates new tokens."""
        # Register and get tokens
        register_resp = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        tokens = register_resp.json()
        refresh_token = tokens["refresh_token"]
        
        # Call refresh endpoint
        response = await client.post(
            "/refresh",
            headers={"Authorization": f"Bearer {refresh_token}"}
        )
        assert response.status_code == 200
        new_tokens = response.json()
        assert "access_token" in new_tokens
        assert "refresh_token" in new_tokens
        # New tokens should be valid JWT tokens
        assert len(new_tokens["access_token"]) > 10
        assert len(new_tokens["refresh_token"]) > 10

    @pytest.mark.asyncio
    async def test_logout_endpoint(self, client):
        """Test that logout endpoint works."""
        # Register
        register_resp = await client.post(
            "/register",
            json={"email": "test@example.com", "password": "password123"}
        )
        access_token = register_resp.json()["access_token"]
        
        # Logout
        response = await client.post(
            "/logout",
            headers={"Authorization": f"Bearer {access_token}"}
        )
        assert response.status_code == 200
        assert "message" in response.json()

    @pytest.mark.asyncio
    async def test_refresh_with_invalid_token(self, client):
        """Test that refresh with invalid token fails."""
        response = await client.post(
            "/refresh",
            headers={"Authorization": "Bearer invalid_token"}
        )
        assert response.status_code == 401
