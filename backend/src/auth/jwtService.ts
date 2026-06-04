import { SignJWT, jwtVerify } from 'jose';
import { createSecretKey, randomUUID } from 'crypto';

const JWT_SECRET = process.env.JWT_SECRET;
const ACCESS_EXPIRES = process.env.ACCESS_EXPIRES || '15m';

if (!JWT_SECRET) throw new Error('JWT_SECRET environment variable must be set — no insecure fallback allowed');

const key = createSecretKey(Buffer.from(JWT_SECRET, 'utf8'));

export async function signAccessToken(userId: string, roles: string[] = []) {
  const jti = randomUUID();
  const jwt = await new SignJWT({ sub: userId, roles })
    .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
    .setIssuedAt()
    .setExpirationTime(ACCESS_EXPIRES)
    .setJti(jti)
    .sign(key as any);
  return { token: jwt, jti };
}

export async function verifyAccessToken(token: string) {
  const { payload } = await jwtVerify(token, key as any, {
    algorithms: ['HS256']
  });
  return payload as any;
}
