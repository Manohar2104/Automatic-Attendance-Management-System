from app.services.find3_engine import Find3Engine

engine = Find3Engine(data_folder="./data")


class PresenceService:
    """
    Handles WiFi-based attendance validation using Find3.
    """

    def __init__(self):
        self.engine = engine

    def validate_presence(
        self,
        session_id: int,
        student_id: int,
        family: str,
        sensor_data: dict
    ):
        """
        Returns whether student is present or not.
        """

        result = self.engine.classify(family, sensor_data)

        if not result["success"]:
            return {
                "present": False,
                "confidence": 0,
                "reason": result.get("error", "classification_failed")
            }

        analysis = result["result"]

        # 🔥 IMPORTANT: adapt depending on find3 output structure
        # Usually includes "confidence" or "probability"
        confidence = analysis.get("confidence", 0)

        # threshold logic (your design)
        if confidence >= 0.70:
            return {
                "present": True,
                "confidence": confidence,
                "reason": "matched_room"
            }

        return {
            "present": False,
            "confidence": confidence,
            "reason": "low_confidence"
        }