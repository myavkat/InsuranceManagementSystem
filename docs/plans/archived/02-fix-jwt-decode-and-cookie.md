# Plan: Fix JWT Decode and Auth Cookie (login/page.tsx)

**Source:** Sprint 8 code review — findings 1, 11

---

## Objective

Fix two bugs in the login page:
- `atob()` fails to decode base64url-encoded JWT payloads, corrupting user info
- `setAuthCookie` omits the `Secure` flag, allowing JWT transmission over unencrypted HTTP

---

## Severity

🔴 **Critical + Medium** — JWT decode failure silently corrupts user state; missing Secure flag leaks credentials.

---

## Files to Read First

| File | Why |
|------|-----|
| `frontend/src/app/(auth)/login/page.tsx` | The file being fixed (182 lines) |
| `frontend/src/lib/store/auth-store.ts` | To verify `UserInfo` type shape matches expectations |
| `frontend/src/middleware.ts` | To confirm middleware decodes `auth_token` with `decodeURIComponent` (cookie is URL-encoded) |
| `frontend/src/lib/api/server-fetch.ts` | To confirm serverFetch reads the `Authorization` header (set by middleware from cookie) |

---

## Steps

### Finding 1 — Fix: `atob()` cannot decode base64url (line 27)

**The bug**: JWTs use **base64url** encoding (RFC 7519), where `-` replaces `+`, `_` replaces `/`, and `=` padding is omitted. The browser built-in `atob()` decodes standard base64 only — it does NOT understand base64url. When the JWT payload contains base64url-specific characters (`-` or `_`), `atob()` either produces garbage or throws. The catch block at line 28 returns `null`, causing `userFromToken()` (line 37) to return empty strings for `userId`, `username`, and `email`. The login appears to succeed (token is stored) but the user object is blank, breaking every component that reads auth state.

**The fix**: Convert base64url to standard base64 before calling `atob()`. The standard conversion is:
1. Replace `-` with `+`
2. Replace `_` with `/`
3. Pad with `=` to make length a multiple of 4

**Exact change** — Replace the `decodeJwtPayload` function (lines 23-31):
```typescript
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    return JSON.parse(atob(parts[1]));
  } catch {
    return null;
  }
}
```
with:
```typescript
function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    // Convert base64url → base64 (RFC 7519 §2, RFC 4648 §5)
    const base64 = parts[1]
      .replace(/-/g, "+")
      .replace(/_/g, "/");
    // atob requires padding to a multiple of 4
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}
```

---

### Finding 2 — Fix: Missing `Secure` flag on auth cookie (line 50)

**The bug**: `setAuthCookie` at line 50 sets the `auth_token` cookie with `path=/; max-age=...; SameSite=Lax` but **without the `Secure` flag**. Without `Secure`, the browser will send this cookie on unencrypted HTTP connections to the same domain. In production behind HTTPS, if a user manually visits `http://example.com` (or an attacker MITM-redirects them), the JWT is transmitted in plaintext over the network.

**The fix**: Add the `Secure` flag to the cookie string.

**Exact change** — Replace line 50:
```typescript
document.cookie = `auth_token=${encodeURIComponent(token)}; path=/; max-age=${expiresIn}; SameSite=Lax`;
```
with:
```typescript
document.cookie = `auth_token=${encodeURIComponent(token)}; path=/; max-age=${expiresIn}; SameSite=Lax; Secure`;
```

Also update `clearAuthCookie` at line 54 for consistency (though `Secure` on a deletion cookie is less critical):
```typescript
document.cookie = "auth_token=; path=/; max-age=0; SameSite=Lax; Secure";
```

---

## Acceptance Criteria

- [x] Login with a JWT whose payload contains base64url characters (`-`, `_`) — `userFromToken` correctly extracts `userId`, `username`, `email`, `roles`
- [x] Login with a standard base64 JWT (no url-characters) still works (no regression)
- [x] Existing tests for login pass; manually verify login flow with a real backend JWT
- [x] Cookie is set with `Secure` flag visible in browser DevTools → Application → Cookies
- [x] Logout (`clearAuthCookie`) still clears the cookie successfully

---

## Dependencies

None — this plan is self-contained.

However, if you are also working on **08-fix-bff-architecture.md** (server-fetch.ts) or **04-fix-middleware-auth-bypass.md** (middleware.ts), they share the auth token flow: login → cookie → middleware → serverFetch header. Test the full chain after each change.
