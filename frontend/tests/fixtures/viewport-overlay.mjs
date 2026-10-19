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
// el-dialog 用 dialog-fade；ElMessageBox 用 fade-in-linear（动画挂在
// 其包装层 .el-overlay-message-box 上），两者都要等完再测量。
export async function settleDialog(page) {
  await page.waitForFunction(
    () => !document.querySelector(
      '.dialog-fade-enter-active, .dialog-fade-leave-active, '
      + '.fade-in-linear-enter-active, .fade-in-linear-leave-active',
    ),
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

// 断言 ElMessageBox 的宽度受 EP 上限（--el-messagebox-width，默认 420px）约束，
// 且完整落在视口内、不把文档撑出横向滚动。
//
// 背景：responsive.scss 曾用 calc(100vw - …) 裸视口约束整体替换 EP 的
// max-width:var(--el-messagebox-width)（同特异度、后加载者胜），宽屏下弹窗
// 被拉伸到接近满宽（1280 视口实测 1237px）。修复后应为
// min(--el-messagebox-width, 视口约束)——只收缩不放大。
//
// messageBox：Playwright locator，指向 .el-message-box[role=dialog] 所在的
//   .el-overlay-message-box 包装层（EP 2.11.5 中 role 挂在包装层，
//   .el-message-box 是其内层节点，此处自动下探）。
// viewport：{ name, width, height }，用于生成可读的失败信息。
export async function assertMessageBoxViewport(messageBox, viewport) {
  const label = viewport.name || `${viewport.width}x${viewport.height}`;
  const box = messageBox.locator('.el-message-box').first();
  assert.ok(
    (await box.count()) > 0,
    `${label}: 定位器下必须存在 .el-message-box 节点`,
  );

  // 1. 计算宽度必须 <= 420px + 容差。这一条同时表达「视口约束没有把
  //    420px 上限挤掉」——缺陷版 calc(100vw - …) 在 1280 视口下是 1256px，
  //    远超上限。用计算样式宽（布局宽）而非 boundingBox，避开过渡动画。
  const computedWidth = await box.evaluate(
    (el) => Number.parseFloat(getComputedStyle(el).width),
  );
  assert.ok(
    computedWidth <= 420 + VIEWPORT_TOLERANCE,
    `${label}: .el-message-box 计算宽度 ${computedWidth}px 必须不超过 `
      + `420px + ${VIEWPORT_TOLERANCE}px 容差（EP --el-messagebox-width 上限被视口约束挤掉时即为此故障）`,
  );

  // 2. 弹窗必须完整落在视口内（左右边距 >= 0），文档不得出现横向滚动。
  const rect = await rectOf(box);
  assert.ok(
    rect.x >= -VIEWPORT_TOLERANCE && rect.x + rect.width <= viewport.width + VIEWPORT_TOLERANCE,
    `${label}: .el-message-box 必须落在视口内，实际 [x=${rect.x}, width=${rect.width}] `
      + `（视口宽 ${viewport.width}）`,
  );
  const scrollWidth = await box.evaluate(
    () => document.documentElement.scrollWidth,
  );
  assert.ok(
    scrollWidth <= viewport.width + VIEWPORT_TOLERANCE,
    `${label}: document.scrollWidth ${scrollWidth}px 不得超过视口宽 ${viewport.width}px（横向溢出）`,
  );

  return { computedWidth, rect, scrollWidth };
}
