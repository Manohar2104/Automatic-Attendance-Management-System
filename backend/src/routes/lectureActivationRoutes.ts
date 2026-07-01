import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import {
  evaluateTodayLectures,
  getLectureActivationErrorMessage,
  getLectureActivationErrorStatus,
  getTodayLectureInstances,
  runActivationEngine,
} from '../services/lectureActivationService';

const router = express.Router();

function sendError(res: express.Response, error: unknown) {
  return res.status(getLectureActivationErrorStatus(error)).json({ error: getLectureActivationErrorMessage(error) });
}

function requireInternalRole(req: express.Request, res: express.Response) {
  const roles = (req as any).user?.roles || [];
  if (!roles.includes('INTERNAL')) {
    res.status(403).json({ error: 'insufficient_role' });
    return false;
  }

  return true;
}

router.use(authMiddleware);

router.post('/internal/lecture-activation/run', async (req, res) => {
  try {
    if (!requireInternalRole(req, res)) {
      return;
    }

    const result = await runActivationEngine();
    return res.status(200).json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/internal/lecture-activation/status', async (req, res) => {
  try {
    if (!requireInternalRole(req, res)) {
      return;
    }

    const today = await getTodayLectureInstances();
    const snapshot = await evaluateTodayLectures();
    return res.status(200).json({
      ...snapshot,
      todayLectureInstances: today.lectureInstances,
      engineReady: true
    });
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;
