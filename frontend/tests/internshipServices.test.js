import test from "node:test";
import assert from "node:assert/strict";
import {
  internshipProjects,
  internshipAdviceItems,
  applyInternshipAdvice,
  internshipFields,
  newInternshipSchedule,
  internshipReportTypes,
  internshipCalendarText,
  beijingToday,
  internshipEndDateDisabled,
  eligibleInternshipDates,
  internshipReportRangeError,
} from "../src/utils/internshipServices.js";
import {
  nativeProductSupported,
  serviceFormFields,
} from "../src/utils/serviceCommerce.js";
test("all nine plaintext internship projects have independent native product bindings", () => {
  assert.equal(Object.keys(internshipProjects).length, 9);
  for (const project of Object.keys(internshipProjects))
    assert.equal(nativeProductSupported("sxdk_tw", project, project), true);
  assert.equal(nativeProductSupported("sxdk_tw", "zxjy", "gxy"), false);
});
test("internship form never forwards arbitrary upstream routing or stale plugin credentials", () => {
  const input = {
    account: "本人",
    password: "private",
    lat: "28.1",
    lng: "112.1",
    url: "https://injected.example",
    key: "secret",
    act: "addOrder",
    authCode: "stale",
    runType: "3",
  };
  assert.deepEqual(serviceFormFields("sxdk_tw", input), {
    account: "本人",
    password: "private",
    lat: "28.1",
    lng: "112.1",
  });
  assert.equal(internshipFields(input, true).account, undefined);
});
test("each new schedule is independent and does not preselect a paid end date", () => {
  const a = newInternshipSchedule(),
    b = newInternshipSchedule();
  a.weekdays.push(7);
  a.reportLengths.day.minSize = 500;
  assert.equal(b.reportLengths.day.minSize, 0);
  assert.equal(b.endDate, "");
  assert.deepEqual(b.weekdays, [1, 2, 3, 4, 5]);
});
test("report actions respect qzt and gxy original form restrictions", () => {
  assert.deepEqual(internshipReportTypes("qzt"), ["周报", "月报"]);
  assert.ok(internshipReportTypes("gxy").includes("上下班打卡"));
  assert.equal(internshipReportTypes("zxjy").includes("打卡"), false);
});
test("calendar label describes a plan without implying completion", () => {
  assert.equal(
    internshipCalendarText({ ...newInternshipSchedule(), weekdays: [1, 3, 5] }),
    "周一 / 三 / 五 · 08:00:00 – 18:00:00",
  );
});

test("calendar uses Beijing midnight and a 365-day inclusive booking horizon", () => {
  const now = new Date("2026-09-07T16:01:00Z");
  assert.equal(beijingToday(now), "2026-09-08");
  assert.equal(internshipEndDateDisabled(new Date(2026, 8, 7), now), true);
  assert.equal(internshipEndDateDisabled(new Date(2027, 8, 7), now), false);
  assert.equal(internshipEndDateDisabled(new Date(2027, 8, 8), now), true);
});
test("report ranges cannot bridge unpaid dates or offer future purchased dates", () => {
  const dates = eligibleInternshipDates(
    {
      schedule: { endDate: "2026-09-10" },
      paidDates: ["2026-09-07", "2026-09-05", "2026-09-08"],
    },
    new Date("2026-09-07T05:00:00Z"),
  );
  assert.deepEqual(dates, ["2026-09-05", "2026-09-07"]);
  assert.match(
    internshipReportRangeError(
      { startDate: "2026-09-05", endDate: "2026-09-07" },
      dates,
    ),
    /未购买/,
  );
  assert.equal(
    internshipReportRangeError(
      { startDate: "2026-09-05", endDate: "2026-09-05" },
      dates,
    ),
    "",
  );
  assert.match(
    internshipReportRangeError(
      { startDate: "2026-02-30", endDate: "2026-02-30" },
      dates,
    ),
    /有效/,
  );
});

test("lookup advice stays optional, labels explicit false and never becomes a paid schedule automatically",()=>{
 const original=newInternshipSchedule();const advice={checkInTime:"09:15:00",weekdays:[1,5,7],dailyReport:false,weeklyReport:true,endDate:"2026-09-30"};
 assert.equal(internshipAdviceItems(advice).find(x=>x.key==='dailyReport').value,'不需要');
 const result=applyInternshipAdvice(original,advice,new Date('2026-09-08T04:00:00Z'));
 assert.equal(original.checkInTime,'08:00:00');assert.equal(original.endDate,'');assert.deepEqual(result.weekdays,[1,5,7]);assert.equal(result.weeklyReport,true);assert.equal(result.endDate,'2026-09-30');assert.equal(result.runMode,1);
});
test("missing and invalid advice preserves user choices, out-of-range dates are never silently billed",()=>{
 const current={...newInternshipSchedule(),dailyReport:true,endDate:'2026-09-20'};
 const result=applyInternshipAdvice(current,{checkInTime:'25:00:00',weekdays:[0,7],dailyReport:null,endDate:'2099-01-01',runMode:3},new Date('2026-09-08T04:00:00Z'));
 assert.deepEqual(result,current);assert.notEqual(result,current);
 assert.equal(applyInternshipAdvice(current,{endDate:'2026-02-30'},new Date('2026-09-08T04:00:00Z')).endDate,current.endDate);
});
