import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import {
  getDailyRegistrationErrorMessage,
  getDailyRegistrationErrorStatus,
  getDailyRegistrationStatus,
  registerDailyRegistration
} from '../services/dailyRegistrationService';

const router = express.Router();

function sendError(res: express.Response, error: unknown) {
  return res.status(getDailyRegistrationErrorStatus(error)).json({ error: getDailyRegistrationErrorMessage(error) });
}

function requireStudentRole(req: express.Request, res: express.Response) {
  const roles = (req as any).user?.roles || [];
  if (!roles.includes('STUDENT')) {
    res.status(403).json({ error: 'insufficient_role' });
    return false;
  }

  return true;
}

router.use(authMiddleware);

router.post('/daily-registration', async (req, res) => {
  try {
    if (!requireStudentRole(req, res)) {
      return;
    }

    const result = await registerDailyRegistration({
      studentId: (req as any).user.id,
      mode: req.body?.mode
    });

    return res.status(201).json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/daily-registration/status', async (req, res) => {
  try {
    if (!requireStudentRole(req, res)) {
      return;
    }

    const result = await getDailyRegistrationStatus((req as any).user.id);
    if (!result) {
      return res.status(404).json({ error: 'DAILY_REGISTRATION_NOT_FOUND' });
    }

    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/daily-registration', async (req, res) => {
  try {
    if (!requireStudentRole(req, res)) {
      return;
    }

    const result = await getDailyRegistrationStatus((req as any).user.id);
    if (!result) {
      return res.status(404).json({ error: 'DAILY_REGISTRATION_NOT_FOUND' });
    }

    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;