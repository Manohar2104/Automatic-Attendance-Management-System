import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import { getStudentCurrentAttendance, listStudentAttendanceHistory } from '../services/studentAttendanceService';

const router = express.Router();

function sendError(res: express.Response, error: any) {
  const statusCode = error?.statusCode || 500;
  const errorCode = error?.message || 'internal_error';
  return res.status(statusCode).json({ error: errorCode });
}

router.get('/history', authMiddleware, async (req, res) => {
  try {
    const studentId = (req as any).user?.id;
    const limit = Number(req.query?.limit ?? 50);
    const records = await listStudentAttendanceHistory(studentId, limit);
    return res.json({ records });
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/current', authMiddleware, async (req, res) => {
  try {
    const studentId = (req as any).user?.id;
    const sessionId = typeof req.query?.sessionId === 'string' ? req.query.sessionId : '';

    if (!sessionId.trim()) {
      return res.status(400).json({ error: 'MISSING_SESSION_ID' });
    }

    const record = await getStudentCurrentAttendance(sessionId.trim(), studentId);
    if (!record) {
      return res.status(404).json({ error: 'ATTENDANCE_NOT_FOUND' });
    }

    return res.json({ record });
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;