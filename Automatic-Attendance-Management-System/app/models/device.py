from sqlalchemy import Column, Integer, String
from app.db.base import Base

class Device(Base):
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True)
    student_id = Column(Integer)
    device_hash = Column(String, unique=True)