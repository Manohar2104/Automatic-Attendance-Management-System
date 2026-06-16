from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import datetime


class UserCreate(BaseModel):
    email: EmailStr
    password: str


class DeviceRegister(BaseModel):
    device_fingerprint: str


class PresenceEvent(BaseModel):
    device_fingerprint: str
    session_id: Optional[str] = None
    location: Optional[str] = None
    event_type: str = "ENTER"


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class HealthCheck(BaseModel):
    status: str
    now: datetime
