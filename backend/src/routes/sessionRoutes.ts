import express from 'express';
import {
  createSession,
  endSession,
  getSession,
  joinSession,
  listActiveSessions
} from '../services/sessionService';

const router = express.Router();

function sendError(res: express.Response, error: any) {
  const statusCode = error?.statusCode || 500;
  const errorCode = error?.message || 'internal_error';
  return res.status(statusCode).json({ error: errorCode });
}

// ============================================================================
// STUDENT-FACING APIS (Phase 5 — Student App deadline: 21 June)
// ============================================================================

router.get('/active', async (_req, res) => {
  try {
    const sessions = await listActiveSessions();
    return res.json({ sessions });
  } catch (error) {
    return sendError(res, error);
  }
});

router.post('/:id/join', async (req, res) => {
  try {
    const result = await joinSession({
      sessionId: req.params.id,
      studentId: req.body?.studentId,
      joinedAt: req.body?.joinedAt ? new Date(req.body.joinedAt) : undefined
    });
    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

// ============================================================================
// DEFERRED APIS (Teacher Dashboard phase — target Phase 6+)
// ============================================================================

// TODO(Phase 6 — Teacher Dashboard): implement POST /sessions/start
// Specification: POST /sessions/start creates a new session
// Body: { classroomId, courseName, presenceThresholdPresent?, presenceThresholdPartial? }
// Response: { session: { sessionId, status: 'ACTIVE', ... } }
// The internal createSession() service is ready; uncomment when Teacher Dashboard is prioritized.
router.post('/start', async (req, res) => {
  try {
    return res.status(501).json({ error: 'DEFERRED_FOR_TEACHER_DASHBOARD', message: 'POST /sessions/start is deferred to Phase 6+ (Teacher Dashboard)' });
  } catch (error) {
    return sendError(res, error);
  }
});

// TODO(Phase 6 — Teacher Dashboard): implement POST /sessions/:id/end
// Specification: POST /sessions/:id/end closes an active session
// Response: { session: { status: 'CLOSED', ... } }
// The internal endSession() service is ready; uncomment when Teacher Dashboard is prioritized.
router.post('/:id/end', async (req, res) => {
  try {
    return res.status(501).json({ error: 'DEFERRED_FOR_TEACHER_DASHBOARD', message: 'POST /sessions/:id/end is deferred to Phase 6+ (Teacher Dashboard)' });
  } catch (error) {
    return sendError(res, error);
  }
});

// TODO(Phase 6 — Teacher Dashboard or Student App detail screen): implement GET /sessions/:id
// Specification: GET /sessions/:id retrieves a single session's details
// Response: { session: { sessionId, status, startTime, presenceThresholds, ... } }
// The internal getSession() service is ready; uncomment if Student App requires session detail screen before join.
router.get('/:id', async (req, res) => {
  try {
    return res.status(501).json({ error: 'DEFERRED_FOR_REVIEW', message: 'GET /sessions/:id is deferred pending Student App requirements' });
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;
