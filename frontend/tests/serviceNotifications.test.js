import test from "node:test";
import assert from "node:assert/strict";
import {
  notificationState,
  notificationCanChallenge,
  notificationTokenValid,
} from "../src/utils/serviceNotifications.js";
test("notification keys are opaque credentials, never arbitrary URLs, paths or query strings", () => {
  assert.equal(
    notificationTokenValid("privateShowdocKey0123456789abcdef"),
    true,
  );
  for (const value of [
    "https://push.showdoc.com.cn/server/api/push/key",
    "../abc012345678901",
    "abcdef012345678901?x=y",
    "abc012345678901%2f",
    "short",
    "x".repeat(129),
  ])
    assert.equal(notificationTokenValid(value), false);
});
test("receiver proof must precede notification enablement and an existing challenge cannot be resent", () => {
  const settings = {
    configured: true,
    deliveryAvailable: true,
    verified: false,
    challengeDeliveryId: null,
  };
  assert.equal(notificationCanChallenge(settings), true);
  assert.equal(
    notificationCanChallenge({ ...settings, challengeDeliveryId: "existing" }),
    false,
  );
  assert.equal(
    notificationCanChallenge({ ...settings, verified: true }),
    false,
  );
  assert.equal(
    notificationCanChallenge({ ...settings, deliveryAvailable: false }),
    false,
  );
  assert.equal(notificationState("ACCEPTED"), "ShowDoc 已受理");
  assert.equal(notificationState("UNKNOWN"), "发送结果未知");
});
