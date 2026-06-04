import { hashPassword, verifyPassword } from '../src/auth/passwordService';

describe('passwordService', () => {
  it('hashes and verifies password', async () => {
    const pw = 'correct horse battery staple';
    const hash = await hashPassword(pw);
    const ok = await verifyPassword(pw, hash);
    expect(ok).toBe(true);
  });
});
