import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import { submitHeartbeat } from '../services/heartbeatService';

const router = express.Router();

router.post('/', authMiddleware, async (req, res) => {
  try {
    const studentId = (req as any).user?.id;
    const result = await submitHeartbeat(studentId, {
      sessionId: req.body?.sessionId,
      wifiFingerprint: req.body?.wifiFingerprint,
      sequenceNumber: req.body?.sequenceNumber,
      deviceFingerprint: req.body?.deviceFingerprint,
      timestamp: req.body?.timestamp
    });

    if (!result.accepted) {
      return res.status(result.statusCode).json({
        error: result.errorCode,
        message: result.message
      });
    }

    return res.status(200).json({
      heartbeat: result.heartbeat,
      runningPresenceScore: result.runningPresenceScore,
      confidenceScore: result.confidenceScore,
      classificationResult: result.classificationResult
    });
  } catch (error) {
    const statusCode = (error as any)?.statusCode || 500;
    const errorCode = (error as Error)?.message || 'internal_error';
    return res.status(statusCode).json({ error: errorCode });
  }
});

export default router;
