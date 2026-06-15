from fastapi import FastAPI
from app.api.routes.presence import router as presence_router


app = FastAPI(title="Smart Attendance Registry")
app.include_router(presence_router)
@app.get("/health")
def health():
    return {"status": "ok"}