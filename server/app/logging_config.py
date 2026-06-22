import sys
import logging
import json
import traceback
from contextvars import ContextVar
from datetime import datetime, timezone

# Context variables for structured logging (async-safe)
request_id_var: ContextVar[str] = ContextVar("request_id", default="")
user_id_var: ContextVar[str] = ContextVar("user_id", default="")
session_id_var: ContextVar[str] = ContextVar("session_id", default="")
endpoint_var: ContextVar[str] = ContextVar("endpoint", default="")

class JSONFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        # Build core JSON structured fields
        log_data = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "request_id": request_id_var.get(),
            "user_id": user_id_var.get(),
            "session_id": session_id_var.get(),
            "endpoint": endpoint_var.get(),
        }

        # Handle exception tracebacks if present
        if record.exc_info:
            log_data["exception"] = "".join(traceback.format_exception(*record.exc_info))

        # Copy any specific extra fields passed to logger.info
        for key in ["latency_ms", "status_code", "client_ip", "method"]:
            if hasattr(record, key):
                log_data[key] = getattr(record, key)

        return json.dumps(log_data)

def setup_logging(log_level: int = logging.INFO):
    # Get the root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(log_level)
    
    # Remove all existing handlers (e.g. standard StreamHandlers)
    for handler in root_logger.handlers[:]:
        root_logger.removeHandler(handler)
        
    # Configure stdout JSON log handler
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JSONFormatter())
    root_logger.addHandler(handler)
    
    # Override Uvicorn loggers so they also format logs to JSON
    for logger_name in ("uvicorn", "uvicorn.access", "uvicorn.error"):
        uv_logger = logging.getLogger(logger_name)
        uv_logger.handlers = [handler]
        uv_logger.propagate = False
