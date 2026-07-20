from __future__ import annotations

from collections.abc import AsyncIterator

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.db import Base, get_db
from app.main import app
from app.attendance.ble.observation_tracking import cooldown_tracker


@pytest.fixture()
async def ble_engine():
    engine = create_async_engine(
        "sqlite+aiosqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)

    yield engine

    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.drop_all)
    await engine.dispose()


@pytest.fixture()
def ble_sessionmaker(ble_engine):
    return sessionmaker(bind=ble_engine, class_=AsyncSession, expire_on_commit=False)


@pytest.fixture()
async def ble_db_session(ble_sessionmaker) -> AsyncIterator[AsyncSession]:
    async with ble_sessionmaker() as session:
        yield session


@pytest.fixture()
async def ble_app_client(ble_sessionmaker):
    async def override_get_db():
        async with ble_sessionmaker() as session:
            yield session

    original_overrides = dict(app.dependency_overrides)
    app.dependency_overrides[get_db] = override_get_db

    transport = ASGITransport(app=app, raise_app_exceptions=False)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        yield client

    app.dependency_overrides.clear()
    app.dependency_overrides.update(original_overrides)


@pytest.fixture(autouse=True)
async def reset_ble_observation_trackers():
    await cooldown_tracker.reset()
    yield
    await cooldown_tracker.reset()
