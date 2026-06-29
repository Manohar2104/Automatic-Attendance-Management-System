import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import {
  deleteTimetable,
  getTimetable,
  getTimetableErrorMessage,
  getTimetableErrorStatus,
  listTimetables,
  updateTimetable,
  uploadTimetable
} from '../services/timetableService';

const router = express.Router();

function sendError(res: express.Response, error: unknown) {
  return res.status(getTimetableErrorStatus(error)).json({ error: getTimetableErrorMessage(error) });
}

function requireRoles(req: express.Request, res: express.Response, allowedRoles: string[]) {
  const roles = (req as any).user?.roles || [];
  if (!allowedRoles.some((role) => roles.includes(role))) {
    res.status(403).json({ error: 'insufficient_role' });
    return false;
  }

  return true;
}

router.use(authMiddleware);

router.post('/timetables/weekly', async (req, res) => {
  try {
    if (!requireRoles(req, res, ['TEACHER', 'ADMIN'])) {
      return;
    }

    const result = await uploadTimetable((req as any).user.id, req.body);
    return res.status(201).json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/timetables', async (req, res) => {
  try {
    if (!requireRoles(req, res, ['TEACHER', 'ADMIN'])) {
      return;
    }

    const result = await listTimetables({
      academicWeekStart: typeof req.query.academicWeekStart === 'string' ? req.query.academicWeekStart : undefined,
      status: typeof req.query.status === 'string' ? req.query.status : undefined
    });

    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/admin/timetables', async (req, res) => {
  try {
    if (!requireRoles(req, res, ['ADMIN'])) {
      return;
    }

    const result = await listTimetables({
      academicWeekStart: typeof req.query.academicWeekStart === 'string' ? req.query.academicWeekStart : undefined,
      status: typeof req.query.status === 'string' ? req.query.status : undefined
    });

    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/timetables/:id', async (req, res) => {
  try {
    if (!requireRoles(req, res, ['TEACHER', 'ADMIN'])) {
      return;
    }

    const result = await getTimetable(req.params.id, (req as any).user.id, (req as any).user.roles?.includes('ADMIN'));
    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.patch('/timetables/:id', async (req, res) => {
  try {
    if (!requireRoles(req, res, ['TEACHER', 'ADMIN'])) {
      return;
    }

    const result = await updateTimetable(req.params.id, (req as any).user.id, req.body, (req as any).user.roles?.includes('ADMIN'));
    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.delete('/timetables/:id', async (req, res) => {
  try {
    if (!requireRoles(req, res, ['TEACHER', 'ADMIN'])) {
      return;
    }

    const result = await deleteTimetable(req.params.id, (req as any).user.id, (req as any).user.roles?.includes('ADMIN'));
    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;