import test from "node:test";
import assert from "node:assert/strict";
import { watch } from "vue";

const storage = new Map();
globalThis.localStorage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: (key) => storage.delete(key),
};
const { accessToken, sessionUserInfo, applyAuthSession, clearAuthSession,
  authSessionScope, hasAuthenticatedSession, isAccessTokenExpired } = await import("../src/utils/authSession.js");
const uid = "20000000-0000-4000-8000-000000000007";
const token = (overrides = {}) => `test.${Buffer.from(JSON.stringify({
  sub: uid, sid: "a".repeat(32), exp: Date.now() / 1000 + 3600, ...overrides,
})).toString("base64url")}.signature`;
const session = (overrides = {}) => ({ token: token(), uid, role: "USER", isAdmin: false, ...overrides });

test("the real public uid login contract activates an authenticated session", () => {
  clearAuthSession();
  assert.equal(hasAuthenticatedSession.value, false);
  applyAuthSession(session());
  assert.equal(Object.hasOwn(sessionUserInfo.value, "userId"), false);
  assert.equal(hasAuthenticatedSession.value, true);
  assert.equal(isAccessTokenExpired(), false);
});

test("same-family rotation and refreshed public profile data preserve private UI scope", () => {
  applyAuthSession(session({ permissions: ["order:read", "order:create"], balance: "10.00" }));
  const original = authSessionScope.value;
  const invalidations = [];
  const stop = watch(authSessionScope, (value) => invalidations.push(value), { flush: "sync" });
  applyAuthSession(session({ token: token({ exp: Date.now() / 1000 + 7200, jti: "rotated" }),
    permissions: ["order:create", "order:read"], balance: "9.84", nickname: "新昵称", rate: "0.65" }));
  stop();
  assert.equal(authSessionScope.value, original);
  assert.deepEqual(invalidations, [], "no transient invalidation while applyAuthSession updates refs");
  assert.equal(storage.has("token"), false);
  assert.equal(storage.has("refreshToken"), false);
  assert.ok(!storage.get("userInfo").includes("signature"));
});

test("logout, identity, family, and authorization changes still invalidate private context", () => {
  for (const change of [
    () => applyAuthSession(session({ uid: "20000000-0000-4000-8000-000000000008" })),
    () => applyAuthSession(session({ token: token({ sid: "b".repeat(32) }) })),
    () => applyAuthSession(session({ role: "ADMIN", isAdmin: true })),
    () => applyAuthSession(session({ permissions: ["order:update"] })),
    () => applyAuthSession(session({ mustChangePassword: true })),
    () => { sessionUserInfo.value = { ...sessionUserInfo.value, uid: "another-account" }; },
    clearAuthSession,
  ]) {
    applyAuthSession(session());
    const previous = authSessionScope.value;
    change();
    assert.notEqual(authSessionScope.value, previous);
  }
  assert.equal(hasAuthenticatedSession.value, false);
});

test("malformed and expired access claims fail closed", () => {
  for (const value of ["", "not-a-jwt", token({ exp: "invalid" }), token({ exp: 1 })]) {
    accessToken.value = value;
    assert.equal(isAccessTokenExpired(), true);
  }
  clearAuthSession();
});
