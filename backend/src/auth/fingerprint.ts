import crypto from 'crypto';
import { Request } from 'express';

export function generateFingerprint(req: Request) {
  const ua = req.get('user-agent') || '';
  const accept = req.get('accept') || '';
  const lang = req.get('accept-language') || '';
  const ip = req.ip || req.get('x-forwarded-for') || '';
  const raw = `${ua}|${accept}|${lang}|${ip}`;
  const hash = crypto.createHash('sha256').update(raw).digest('hex');
  return { hash, ua, accept, lang, ip };
}
