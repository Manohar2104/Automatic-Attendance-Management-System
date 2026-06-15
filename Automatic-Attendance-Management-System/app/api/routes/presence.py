from fastapi import APIRouter
from pydantic import BaseModel

from app.services.presence_service import PresenceService

router = APIRouter()
service = PresenceService()


class PresenceRequest(BaseModel):
    student_id: int
    family: str  # classroom/room identifier
    sensor_data: dict


@router.post("/sessions/{session_id}/presence")
def mark_presence(session_id: int, req: PresenceRequest):

    result = service.validate_presence(
        session_id=session_id,
        student_id=req.student_id,
        family=req.family,
        sensor_data=req.sensor_data
    )

    return {
        "session_id": session_id,
        "student_id": req.student_id,
        "present": result["present"],
        "confidence": result["confidence"],
        "reason": result["reason"]
    }