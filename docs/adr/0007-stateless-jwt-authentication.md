# 7. Stateless JWT authentication

- **Status:** Accepted
- **Date:** 2026-11-12

## Context

Purchases must be tied to a real customer to enforce one unit per customer, and the reference
design took the user id from the URL, so anyone could buy as anyone. The backend must scale
horizontally without sharing session state.

## Decision

The backend issues HS256-signed JWTs on login or registration and validates them as an OAuth2
resource server using Spring Security's built-in Nimbus support (no extra JWT library). Tokens
carry the user id (`sub`), email, display name and roles, and are checked for issuer and expiry
on every request. Controllers obtain the caller through `@AuthenticatedUser`, which reads the
validated subject, never a request parameter.

## Consequences

- No server-side sessions: any instance can serve any request.
- Tokens cannot be revoked before they expire (2 hours by default). Acceptable for this
  application; a deny list or short-lived tokens with refresh would address it if needed.
- CSRF protection is unnecessary because authentication uses bearer tokens, not cookies. The
  frontend keeps the session in `localStorage`, with guarded access.
- The signing secret must be at least 32 bytes and set through `JWT_SECRET` outside local
  development.
