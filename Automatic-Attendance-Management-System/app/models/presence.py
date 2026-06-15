from sqlalchemy import Column, Integer, String
from app.db.base import Base

class PresenceSubmission(Base):
    __tablename__ = "presence_submissions"

    id = Column(Integer, primary_key=True)
    session_id = Column(Integer)
    student_id = Column(Integer)
    confidence = Column(String)