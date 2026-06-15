import os
import sys
from typing import Dict, Any, Optional

# ✅ FIXED PATH based on your structure
BASE_DIR = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "../../external/find3/server/ai/src")
)

sys.path.append(BASE_DIR)

from learn import AI


class Find3Engine:
    """
    Wrapper around find3 AI engine (local ML inference).
    Used instead of HTTP calls to find3 container.
    """

    def __init__(self, data_folder: str = "./data"):
        self.data_folder = data_folder
        self._cache = {}  # family -> AI model

        os.makedirs(self.data_folder, exist_ok=True)

    def _get_model(self, family: str) -> AI:
        """
        Load or reuse AI model for a given family (room/location).
        """
        if family in self._cache:
            return self._cache[family]

        ai = AI(family, self.data_folder)
        model_path = os.path.join(self.data_folder, f"{family}.find3.ai")

        if os.path.exists(model_path):
            ai.load(model_path)

        self._cache[family] = ai
        return ai

    def learn(self, family: str, csv_file: str) -> Dict[str, Any]:
        """
        Train model using CSV fingerprint data.
        """
        ai = self._get_model(family)

        file_path = os.path.join(self.data_folder, csv_file)

        if not os.path.exists(file_path):
            return {"success": False, "message": f"CSV not found: {file_path}"}

        ai.learn(file_path)
        ai.save(os.path.join(self.data_folder, f"{family}.find3.ai"))

        return {"success": True, "message": "Model trained successfully"}

    def classify(self, family: str, sensor_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Predict location from WiFi sensor data.
        """
        ai = self._get_model(family)

        try:
            result = ai.classify(sensor_data)
            return {
                "success": True,
                "result": result
            }
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }

    def get_model(self, family: str) -> Optional[AI]:
        """
        Expose raw model if needed (admin/debug use).
        """
        return self._cache.get(family)