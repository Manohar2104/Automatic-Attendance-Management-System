import { Request, Response, NextFunction } from 'express';
import { hasPermission } from '../auth/rbacService';

export function requirePermission(permission: string) {
  return async function (req: Request, res: Response, next: NextFunction) {
    const user = (req as any).user;
    if (!user) return res.status(401).json({ error: 'unauthenticated' });
    const ok = await hasPermission(user.id, permission);
    if (!ok) return res.status(403).json({ error: 'forbidden' });
    next();
  };
}
