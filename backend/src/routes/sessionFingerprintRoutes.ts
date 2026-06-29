import express from 'express';
import { authMiddleware } from '../middleware/authMiddleware';
import {
  getSessionReferenceFingerprint,
  storeSessionReferenceFingerprint
} from '../services/sessionFingerprintService';

const router = express.Router();

function sendError(res: express.Response, error: any) {
  const statusCode = error?.statusCode || 500;
  const errorCode = error?.message || 'internal_error';
  return res.status(statusCode).json({ error: errorCode });
}

function getTeacherId(req: express.Request) {
  return (req as any).user?.id as string | undefined;
}

router.post('/:id/fingerprint', authMiddleware, async (req, res) => {
  try {
    const teacherId = getTeacherId(req);
    const result = await storeSessionReferenceFingerprint(
      req.params.id,
      teacherId || '',
      req.body?.fingerprint_data
    );

    return res.status(200).json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

router.get('/:id/fingerprint', authMiddleware, async (req, res) => {
  try {
    const teacherId = getTeacherId(req);
    const result = await getSessionReferenceFingerprint(req.params.id, teacherId || '');

    if (!result) {
      return res.status(404).json({ error: 'REFERENCE_FINGERPRINT_NOT_FOUND' });
    }

    return res.json(result);
  } catch (error) {
    return sendError(res, error);
  }
});

export default router;