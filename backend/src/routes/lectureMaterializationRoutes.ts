import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import {
  getLectureMaterializationErrorMessage,
  getLectureMaterializationErrorStatus,
  materializeDay,
  materializeRange,
  materializeWeek
} from '../services/lectureMaterializationService';

const router = express.Router();

function sendError(res: express.Response, error: unknown) {
  return res.status(getLectureMaterializationErrorStatus(error)).json({ error: getLectureMaterializationErrorMessage(error) });
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

router.post('/internal/materialization/run', async (req, res) => {
  try {
    if (!requireInternalRole(req, res)) {
      return;
    }

    const academicDate = typeof req.body?.academicDate === 'string' ? req.body.academicDate : '';
    const academicWeekStart = typeof req.body?.academicWeekStart === 'string' ? req.body.academicWeekStart : '';
    const timetableEntryIds = Array.isArray(req.body?.timetableEntryIds) ? req.body.timetableEntryIds : [];

    if (timetableEntryIds.length > 0) {
      const result = await materializeRange(academicDate, timetableEntryIds, 'MANUAL_TRIGGER');
      return res.status(201).json(result);
    }

    if (academicWeekStart) {
      const result = await materializeWeek(academicWeekStart, 'MANUAL_TRIGGER');
      return res.status(200).json({
        academicWeekStart: result.academicWeekStart,
        runs: result.runs,
        generatedCount: result.runs.reduce((sum, item) => sum + item.generatedCount, 0),
        materializationSource: 'MANUAL_TRIGGER'
      });
    }

    if (!academicDate) {
      return res.status(400).json({ error: 'MISSING_ACADEMIC_DATE' });
    }

    const result = await materializeDay(academicDate, 'MANUAL_TRIGGER');
    return res.status(201).json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;