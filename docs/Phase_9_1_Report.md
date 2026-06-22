# Phase 9.1 Report — Authentication Foundation

## Phase Objective

Phase 9.1 exists to make the student app usable as a real authenticated client. It addresses the problem of keeping a student signed in securely, restoring that session on app relaunch, and letting the app sign out cleanly without exposing long-lived credentials.

## Features Implemented

- Login with email and password.
- JWT access token issuance.
- Refresh token issuance and rotation.
- Secure token persistence on Android.
- Session restoration on startup.
- Logout and refresh-token revocation.

## Technical Implementation Details

### Android Components

- `SplashScreen.kt` checks whether the app can restore a session before showing the main UI.
- `LoginScreen.kt` captures credentials and submits them through the auth view model.
- `DashboardScreen.kt` is the authenticated landing page.
- `AuthViewModel.kt` handles login state, token persistence, and session restoration.
- `AuthRepository.kt` wraps the auth API calls.
- `SecureTokenStorageImpl.kt` stores access and refresh tokens in encrypted preferences.

### Backend Components

- `backend/src/routes/authRoutes.ts` exposes `/auth/login`, `/auth/refresh`, `/auth/logout`, and `/auth/me`.
- `backend/src/auth/jwtService.ts` signs JWT access tokens.
- `backend/src/auth/refreshService.ts` issues, verifies, rotates, and revokes refresh tokens.
- `backend/src/auth/deviceService.ts` optionally binds devices on login.
- `backend/src/auth/lockoutService.ts` tracks failed logins and lockout state.
- `backend/src/middleware/authMiddleware.ts` protects authenticated requests such as `/auth/me`.

### Database Changes

No new Phase 9 schema was required. This phase uses the existing auth tables already present in the repository schema:

- `users`
- `refresh_tokens`
- `device_bindings`
- `account_locks`
- `security_events`

### APIs Created or Modified

- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /auth/me`

## Sequence Flow

1. The user enters credentials in the login screen.
2. The backend verifies the password and lockout state.
3. On success, the backend returns an access token and refresh token.
4. The Android app stores both tokens securely.
5. On launch, the splash screen checks whether token restoration succeeds.
6. When the access token expires, the refresh token can be exchanged for new tokens.
7. Logout clears the session by revoking the refresh token.

## Security Considerations

- Access and refresh tokens are separated so the access token can remain short-lived.
- Refresh tokens are rotated rather than reused.
- Token storage on Android uses encrypted shared preferences.
- Lockout handling prevents unlimited password guessing.
- Device binding is available for sessions that need a device footprint.

## Testing and Validation

- `backend/tests/auth.integration.test.ts` validates login, refresh, logout, token reuse, and `/auth/me`.
- The current backend test suite is green.
- The Android phase-9 validation report documents manual restore-session and logout flows on the student app.

## Issues Encountered

- The first concern was making session restoration reliable without exposing raw credentials.
- Refresh-token rotation needed to be explicit so stale tokens could not be reused.
- Android startup flow had to be structured so the app could decide between login and dashboard before rendering the main navigation tree.

## Current Status

- Login: ✅ Completed
- JWT access tokens: ✅ Completed
- Refresh tokens: ✅ Completed
- Session restoration: ✅ Completed
- Logout: ✅ Completed
