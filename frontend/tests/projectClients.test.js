import test from "node:test";
import assert from "node:assert/strict";
import {
  clientUnitsValid,
  clientActionAllowed,
  clientState,
} from "../src/utils/projectClients.js";
test("source customer units have exact bounded decimals and zero is only allowed during opening", () => {
  assert.equal(clientUnitsValid("0", true), true);
  assert.equal(clientUnitsValid("0"), false);
  for (const input of ["0.000001", "100000", "99999.999999"])
    assert.equal(clientUnitsValid(input), true);
  for (const input of [
    "-1",
    "1e5",
    "1.0000001",
    "100000.000001",
    "Infinity",
    "NaN",
    "",
  ])
    assert.equal(clientUnitsValid(input, true), false);
});
test("suspended customers can return local funds but closed customers cannot; large balances can be returned in chunks", () => {
  assert.equal(
    clientActionAllowed({ status: "SUSPENDED", balance: "0.25" }, "WITHDRAW"),
    true,
  );
  assert.equal(
    clientActionAllowed({ status: "SUSPENDED", balance: "0.25" }, "TOP_UP"),
    false,
  );
  assert.equal(
    clientActionAllowed(
      { status: "ACTIVE", balance: "200000.000000" },
      "WITHDRAW",
    ),
    true,
  );
  assert.equal(
    clientActionAllowed({ status: "CLOSED", balance: "1" }, "WITHDRAW"),
    false,
  );
  assert.equal(
    clientActionAllowed({ status: "ACTIVE", balance: "0.000000" }, "WITHDRAW"),
    false,
  );
  assert.equal(clientActionAllowed(null, "WITHDRAW"), false);
  assert.equal(clientState("STALE"), "已变化 · 未扣款");
});
