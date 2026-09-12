import test from "node:test";
import assert from "node:assert/strict";
import { statusCheckTime, statusCheckDisplay, statusCheckLabels } from "../src/utils/serviceStatusCheck.js";

test("check timestamps use Beijing time independent of browser/host timezone", () => {
  const timezone = process.env.TZ;
  try {
    for (const zone of ["UTC", "America/Los_Angeles", "Asia/Tokyo"]) {
      process.env.TZ = zone;
      assert.deepEqual(statusCheckTime("2026-09-11T10:20:30.123456789"), {
        text: "2026-09-11 10:20:30", datetime: "2026-09-11T10:20:30+08:00",
      });
      assert.equal(statusCheckTime("2026-09-11T02:20:30Z").text, "2026-09-11 10:20:30");
      assert.equal(statusCheckTime("2026-09-10T19:20:30-07:00").text, "2026-09-11 10:20:30");
      assert.equal(statusCheckTime("2026-09-11T00:00:00+08:00").text, "2026-09-11 00:00:00");
    }
  } finally {
    if (timezone === undefined) delete process.env.TZ;
    else process.env.TZ = timezone;
  }
});

test("invalid timestamps do not normalize an impossible date or expose raw text", () => {
  for (const value of [null, undefined, 0, [], {}, "", "secret", "<script>alert(1)</script>",
    "2026-02-30T10:20:30", "2026-09-11T24:00:00", "2026-13-11T10:20:30", "2026-09-11T10:60:30",
    "2026-09-11T10:20:30+08:99", "2026-09-11T10:20:30+14:01", "2026-09-11T10:20:30+24:00",
    "2026-09-11", "2026-09-11T10:20", " 2026-09-11T10:20:30", "2026-09-11T10:20:30.1234567890"]) {
    assert.equal(statusCheckTime(value), null);
  }
  assert.equal(statusCheckTime("2028-02-29T00:00:00").text, "2028-02-29 00:00:00");
});

test("legacy and malformed check metadata are optional and never manufacture successful checks", () => {
  for (const value of [null, undefined, false, "yes", [], {}, { checkedAt: "2026-09-11T00:00:00" }, { delayed: "false" }]) {
    assert.equal(statusCheckDisplay(value), null);
  }
  assert.deepEqual(statusCheckDisplay({ checkedAt: null, delayed: false }), { time: null, delayed: false });
  assert.deepEqual(statusCheckDisplay({ checkedAt: "invalid", delayed: true }), { time: null, delayed: true });
});

test("delay flag preserves last successful check and ignores business/attempt timestamps and internal fields", () => {
  const result = statusCheckDisplay({ checkedAt: "2026-09-11T10:20:30", delayed: true,
    updateTime: "2026-09-12T10:20:30", statusCheckAttemptAt: "2026-09-13T10:20:30", statusCheckToken: "private" });
  assert.deepEqual(result, { time: { text: "2026-09-11 10:20:30", datetime: "2026-09-11T10:20:30+08:00" }, delayed: true });
  assert.deepEqual(statusCheckDisplay({ updateTime: "2026-09-12T10:20:30", delayed: false }), { time: null, delayed: false });
});

test("internship checks describe a plan state, never a count or proof of attendance", () => {
  assert.deepEqual(statusCheckLabels("sxdk_tw"), {
    region: "计划状态核对情况",
    heading: "最近核对计划状态",
    note: "仅核对计划状态，考勤结果请查看执行记录。",
    delayed: "暂未取得最新计划状态，已保留此前结果。",
  });
  for (const type of [undefined, null, "flash", "heisha", "jiguang", "wuxin", "other"])
    assert.equal(statusCheckLabels(type).heading, "最近核对");
  assert.ok(Object.isFrozen(statusCheckLabels("sxdk_tw")));
});
