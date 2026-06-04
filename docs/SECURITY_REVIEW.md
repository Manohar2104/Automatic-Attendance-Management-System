# Phase 3 — Security Review

This document captures the threat model, mitigations, and recommended security controls related to Phase 3 authentication.

## Threat Model Summary
- Threats: token theft (access and refresh), replay, brute-force credential attacks, device impersonation, privilege escalation, leaked signing keys.

## Controls and Mitigations

- Transport & Storage
  - Enforce HTTPS for all endpoints; HSTS enabled.
  - Mark auth cookies `Secure; HttpOnly; SameSite=Strict` (or Lax for UX tradeoffs).
  - Protect refresh tokens in secure storage (mobile) or HttpOnly cookies (web).

- Token Hardening
  - Short-lived access tokens (10–15m) and rotating refresh tokens.
  - Hash refresh tokens server-side; do not store raw tokens.
  - Use asymmetric signing (RS256/ES256) with `kid` in JWT header; publish JWKS for verification.

- Key Management
  - Store signing keys in a secure vault (KMS/HSM). Rotate keys regularly and maintain old keys until all tokens signed by them expire.
  - Rotate application-level peppers used for hashing stored tokens.

- Revocation & Detection
  - Implement revoked-token denylist for access tokens (`jti`) and server-side allowlist for refresh tokens.
  - Detect suspicious patterns (multiple refresh attempts across devices, refresh using revoked tokens) and escalate (revoke all tokens, notify user).

- Rate Limiting & Lockouts
  - Per-IP and per-account rate limits for login and token endpoints.
  - Account lockout policy with email notification.

- Device Attestation
  - Support optional device attestation for mobile clients (Play Integrity, DeviceCheck) for high-value operations.

- MFA
  - Require or offer 2FA for privileged roles (ADMIN, STAFF) and optionally for TEACHER accounts.

## Operational Recommendations
- Audit Logging: log token issue, refresh, revoke, login failures (store minimal PII in logs).
- Monitoring: alert on sudden spikes of refresh failures or token re-use patterns.
- Incident Response: a playbook to rotate keys, revoke active tokens, and communicate with affected users.

## Testing
- CI security tests: verify signed tokens are verifiable against published JWKS.
- Penetration testing: focus on token theft flows, refresh replay, and privilege escalation.

## Developer Guidance
- Never log tokens or raw credentials.
- Sanitize all user-provided device metadata before storage.
- Use vetted crypto libraries; avoid home-grown crypto.

## Notes on Compliance
- For systems handling sensitive PII or governed by regulation, ensure data residency, encryption-at-rest, and access logging meet relevant requirements.

---
End of Security Review.
