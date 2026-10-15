import assert from 'node:assert/strict';

// 弹窗视口几何断言。
//
// 背景：页面根元素带有 .main-content > * 的 fluent-enter 动画，
// animation-fill-mode: both 让 transform 永久保持 translate3d(0,0,0)，
// 页面根因此成为 position: fixed 后代的包含块；main.el-main.main-content
// 还叠加了 perspective 与 backdrop-filter。两者都会让 el-overlay 脱离视口，
// 弹窗底部被页面内容盒裁切。因此断言 overlay 覆盖整个视口，而不只是
// 「弹窗打开了」。
//
// 允许 1px 容差：设备像素比与滚动条取整会带来亚像素误差。

export const VIEWPORT_TOLERANCE = 1;

// 报告故障时使用的标准视口集合。1280x577 是线上报告的裁切尺寸。
export const OVERLAY_VIEWPORTS = [
  { name: 'reported-1280x577', width: 1280, height: 577 },
  { name: 'desktop-1440x900', width: 1440, height: 900 },
  { name: 'portrait-390x844', width: 390, height: 844 },
  { name: 'landscape-844x390', width: 844, height: 390 },
];

// 等待入场/离场过渡结束，避免在动画中途测量。
export async function settleDialog(page) {
  await page.waitForFunction(
    () => !document.querySelector('.dialog-fade-enter-active, .dialog-fade-leave-active'),
  );
}

// 取元素相对视口的矩形，取整后便于比较。
async function rectOf(locator) {
  const box = await locator.boundingBox();
  assert.ok(box, '目标元素必须有可见的包围盒');
  return {
    x: Math.round(box.x),
    y: Math.round(box.y),
    width: Math.round(box.width),
    height: Math.round(box.height),
  };
}

// 断言弹窗已脱离页面容器、overlay 覆盖视口、弹窗本身落在视口内。
//
// dialog：Playwright locator，指向 .el-dialog / .el-drawer 根节点。
// viewport：{ name, width, height }，用于生成可读的失败信息。
export async function assertViewportOverlay(dialog, viewport) {
  const label = viewport.name || `${viewport.width}x${viewport.height}`;

  // 1. 弹窗必须已 Teleport 出被 transform 的页面容器。
  assert.equal(
    await dialog.evaluate((el) => !!el.closest('.main-content')),
    false,
    `${label}: 弹窗必须脱离 .main-content（否则 fixed 定位按页面内容盒计算）`,
  );

  // 2. overlay 必须覆盖整个视口——这是本次缺陷的核心断言。
  const overlay = dialog.page().locator('.el-overlay').filter({ has: dialog }).first();
  const overlayRect = await rectOf(overlay);
  assert.ok(
    Math.abs(overlayRect.x) <= VIEWPORT_TOLERANCE &&
      Math.abs(overlayRect.y) <= VIEWPORT_TOLERANCE &&
      Math.abs(overlayRect.width - viewport.width) <= VIEWPORT_TOLERANCE &&
      Math.abs(overlayRect.height - viewport.height) <= VIEWPORT_TOLERANCE,
    `${label}: overlay 必须等于视口 [0, 0, ${viewport.width}, ${viewport.height}]，实际 ` +
      `[${overlayRect.x}, ${overlayRect.y}, ${overlayRect.width}, ${overlayRect.height}]`,
  );

  // 3. 弹窗本身必须完整落在视口内。
  const dialogRect = await rectOf(dialog);
  assert.ok(
    dialogRect.y >= -VIEWPORT_TOLERANCE &&
      dialogRect.y + dialogRect.height <= viewport.height + VIEWPORT_TOLERANCE,
    `${label}: 弹窗必须完整落在视口内，实际 ${JSON.stringify(dialogRect)}`,
  );

  return { overlayRect, dialogRect };
}

// 断言长表单弹窗可滚动，且底部主操作按钮始终可见可点。
//
// dialog：Playwright locator，指向弹窗根节点。
// actionName：主操作按钮的可访问名称。
export async function assertDialogActionReachable(dialog, viewport, actionName) {
  const label = viewport.name || `${viewport.width}x${viewport.height}`;
  const body = dialog.locator('.el-dialog__body, .el-drawer__body').first();
  const scrollable = await body.evaluate((el) => el.scrollHeight > el.clientHeight);
  if (scrollable) {
    await body.evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });
  }
  const action = dialog.getByRole('button', { name: actionName, exact: true });
  await action.waitFor({ state: 'visible' });
  const box = await action.boundingBox();
  assert.ok(
    box && box.y >= -VIEWPORT_TOLERANCE && box.y + box.height <= viewport.height + VIEWPORT_TOLERANCE,
    `${label}: 主操作按钮必须留在视口内，实际 ${JSON.stringify(box)}`,
  );
  assert.equal(await action.isEnabled(), true, `${label}: 主操作按钮必须可点击`);
  return { scrollable };
}
