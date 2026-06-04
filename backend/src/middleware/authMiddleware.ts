import { Request, Response, NextFunction } from 'express';
import { verifyAccessToken } from '../auth/jwtService';

export async function authMiddleware(req: Request, res: Response, next: NextFunction) {
  try {
    const h = req.headers.authorization;
    if (!h || !h.startsWith('Bearer ')) return res.status(401).json({ error: 'missing_token' });
    const token = h.slice('Bearer '.length);
    const payload = await verifyAccessToken(token);
    // attach user info
    (req as any).user = { id: payload.sub, roles: payload.roles || [] };
    next();
  } catch (err) {
    return res.status(401).json({ error: 'invalid_token' });
  }
}
