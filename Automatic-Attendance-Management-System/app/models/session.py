from sqlalchemy import Column, Integer, String
from app.db.base import Base

class Session(Base):
    __tablename__ = "sessions"

    id = Column(Integer, primary_key=True)
    course_code = Column(String)
    room = Column(String)
    status = Column(String)  # ACTIVE / CLOSED