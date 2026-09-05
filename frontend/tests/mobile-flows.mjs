import assert from 'node:assert/strict';
import path from 'node:path';
import { KEY, UID } from './mobile-fixtures.mjs';
import { externalEndpoints } from '../src/utils/externalApiGuide.js';

export async function runMobileFlows({ page, context, baseURL, inspect, artifacts, issues, requests, setExternalFailure }) {
  const go = async (route, width = 390, height = 844) => {
    await page.setViewportSize({ width, height });
    await page.goto(baseURL + route, { waitUntil: 'networkidle' });
  };
  const fit = async (locator, label) => {
    // Visibility is true during enter animations; wait for layout and viewport resize to settle.
    const nestedBox = locator.locator('.el-message-box');
    const target = await nestedBox.count() ? nestedBox : locator;
    let box;
    for (let attempt = 0; attempt < 30; attempt++) {
      box = await target.boundingBox(); const viewport = page.viewportSize();
      if (box && box.x >= -1 && box.y >= -1 && box.x + box.width <= viewport.width + 1 && box.y + box.height <= viewport.height + 1) return;
      await page.waitForTimeout(50);
    }
    assert.fail(`${label} must fit viewport: ${JSON.stringify(box)} / ${JSON.stringify(page.viewportSize())}`);
  };
  const dialog = async () => {
    const panel = page.locator('.el-dialog:visible').last(); await panel.waitFor();
    await fit(panel, 'dialog');
    for (const button of await panel.locator('.el-dialog__footer button:visible').all()) {
      await fit(button, 'dialog footer button');
      if (await button.isEnabled()) await button.click({ trial: true });
    }
    return panel;
  };
  const run = async (name, task) => {
    try { await task(); console.log(`FLOW PASS: ${name}`); }
    catch (error) { issues.push({ page: name, kind: 'interaction', error: error.message }); console.log(`FLOW FAIL: ${name}: ${error.message}`); }
    finally { setExternalFailure(null); await page.screenshot({ path: path.join(artifacts, `flow-${name}.png`) }).catch(() => {}); }
  };

  await run('mobile-navigation', async () => {
    await go('/dashboard', 320);
    await page.getByRole('button', { name: '打开主导航' }).click();
    const menu = page.locator('.sidebar.mobile-visible'); await menu.waitFor();
    assert.equal(await page.getByRole('button', { name: '打开在线客服' }).isVisible(), false, 'floating support must not cover navigation');
    await menu.getByRole('menuitem', { name: '个人中心', exact: true }).click();
    await page.waitForURL('**/profile');
    assert.equal(await page.locator('.mobile-overlay').count(), 0);
    await page.getByRole('button', { name: '开通API密钥', exact: true }).click({ trial: true });
  });

  await run('one-time-key-and-rotation', async () => {
    await go('/profile', 320, 568);
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.getByRole('button', { name: '开通API密钥', exact: true }).click();
    await page.getByRole('dialog', { name: '开通确认' }).getByRole('button', { name: '确定', exact: true }).click();
    let panel = await dialog();
    assert.equal(await panel.locator('.issued-key-value').innerText(), KEY);
    assert.equal(await panel.locator('.issued-key-value').evaluate((e) => e.scrollWidth <= e.clientWidth), true, 'key wraps without truncation');
    await panel.getByRole('button', { name: '复制完整 APIKey', exact: true }).click();
    assert.equal(await page.evaluate(() => navigator.clipboard.readText()), KEY);
    await panel.getByRole('button', { name: '我已安全保存' }).click();
    await page.locator('.issued-key-value').waitFor({ state: 'hidden' });
    assert.ok(!(await page.locator('body').innerText()).includes(KEY));
    await page.getByRole('button', { name: '轮换 APIKey', exact: true }).click();
    const prompt = page.getByRole('dialog', { name: '确认轮换 APIKey' }); await prompt.waitFor(); await fit(prompt, 'rotation prompt');
    await prompt.getByPlaceholder('当前登录密码').fill('test-password');
    await prompt.getByRole('button', { name: '验证并轮换' }).click();
    panel = await dialog();
    // Smaller height simulates landscape/keyboard pressure. Footer must remain reachable.
    await page.setViewportSize({ width: 390, height: 400 });
    await dialog();
    await panel.getByRole('button', { name: '复制完整 APIKey' }).click();
    await panel.getByRole('button', { name: '我已安全保存' }).click();
    assert.equal(requests.filter((r) => r.path === '/api/api-keys/rotate').length, 1);
    assert.ok(!(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).includes(KEY));
  });

  await run('api-guide-all-seven-endpoints', async () => {
    await go('/api-guide', 320);
    await page.getByPlaceholder('请输入用户 UUID').fill(UID);
    await page.getByPlaceholder('请输入完整 APIKey，非密钥前缀').fill(KEY);
    for (const endpoint of externalEndpoints) {
      const card = page.locator(`[data-api="${endpoint.path}"]`);
      await card.locator('.api-card-header').click();
      for (const field of endpoint.fields) await card.getByPlaceholder(field.description, { exact: true }).fill(field.example);
      const before = requests.filter((r) => r.path.startsWith('/api/external/')).length;
      await card.getByRole('button', { name: `测试${endpoint.title}`, exact: true }).click();
      if (endpoint.confirmation) {
        const confirmation = page.getByRole('dialog', { name: '真实业务操作确认' });
        await fit(confirmation, 'write confirmation');
        await confirmation.getByRole('button', { name: '取消', exact: true }).click();
        assert.equal(requests.filter((r) => r.path.startsWith('/api/external/')).length, before, 'cancel must not execute a write');
        await card.getByRole('button', { name: `测试${endpoint.title}`, exact: true }).click();
        await confirmation.getByRole('button', { name: '确认提交' }).click();
      }
      await card.locator('.api-result').waitFor();
      const request = requests.filter((r) => r.path.startsWith('/api/external/')).at(-1);
      assert.equal(request.path, `/api/external/${endpoint.path}`); assert.equal(request.query, '');
      assert.equal(request.headers.authorization, undefined); assert.equal(request.headers.cookie, undefined);
      assert.equal(new URLSearchParams(request.body).get('api_key'), KEY);
      assert.ok(!(await card.locator('.api-examples').innerText()).includes(KEY));
      await inspect(`api-${endpoint.path}-expanded`);
      await card.locator('.api-card-header').click();
    }
    // Also execute with an empty in-memory access JWT. No refresh operation may be attempted.
    const balance = await page.evaluate(async ({ UID, KEY }) => {
      const auth = await import('/src/utils/authSession.js'); const api = await import('/src/api/external.js');
      const old = auth.accessToken.value; auth.accessToken.value = '';
      try { return (await api.getMoney({ uid: UID, api_key: KEY })).data.money; }
      finally { auth.accessToken.value = old; }
    }, { UID, KEY });
    assert.equal(balance, 500);
    const card = page.locator('[data-api="getmoney"]'); await card.locator('.api-card-header').click();
    for (const failure of [{ status: 401, code: -205, message: '密钥无效测试' }, { status: 429, code: -109, message: '限流测试' }, { status: 503, code: -118, message: 'Redis不可用测试' }]) {
      setExternalFailure(failure);
      const count = requests.length;
      await card.getByRole('button', { name: '测试查询余额', exact: true }).click();
      await page.waitForFunction((text) => document.querySelector('[data-api="getmoney"] .api-result')?.textContent.includes(text), failure.message);
      assert.equal(new URL(page.url()).pathname, '/api-guide');
      assert.equal(requests.slice(count).filter((r) => r.path.startsWith('/api/external/')).length, 1);
      assert.equal(requests.slice(count).filter((r) => r.path.includes('/auth/refresh')).length, 0);
    }
    setExternalFailure(null);
    await page.getByRole('button', { name: '清空凭证和结果' }).click();
    assert.equal(await page.getByPlaceholder('请输入完整 APIKey，非密钥前缀').inputValue(), '');
    assert.equal(await page.locator('.api-result').count(), 0);
    assert.ok(!(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).includes(KEY));
  });

  for (const route of ['/orders', '/admin/orders']) await run(`filter-${route.replaceAll('/', '-')}`, async () => {
    await go(route, 390, 568);
    await page.getByRole('button', { name: '筛选条件', exact: true }).click();
    const drawer = page.locator('.el-drawer:visible'); await drawer.waitFor(); await fit(drawer, 'filter drawer');
    for (const button of await drawer.locator('.el-drawer__footer button').all()) { await fit(button, 'drawer footer'); await button.click({ trial: true }); }
    await drawer.getByRole('button', { name: '查询', exact: true }).click();
    await drawer.waitFor({ state: 'hidden' });
    await inspect(`filter-${route.replaceAll('/', '-')}`);
  });

  for (const [route, button] of [
    ['/orders', '新建订单'], ['/admin/platforms', '添加平台'], ['/admin/platforms', '一键导入'], ['/admin/categories', '添加分类'],
    ['/admin/api-providers', '添加接口'], ['/admin/cards', '生成卡密'], ['/admin/announcements', '发布公告'], ['/admin/variables', '添加变量'],
  ]) await run(`dialog-${route.replaceAll('/', '-')}-${button}`, async () => {
    await go(route, 320, 568);
    await page.getByRole('button', { name: button, exact: true }).click();
    const panel = await dialog();
    await inspect(`dialog-${button}`, true);
    const cancel = panel.getByRole('button', { name: '取消', exact: true });
    if (await cancel.count()) await cancel.click();
    else await panel.getByRole('button', { name: /Close this dialog|关闭此对话框/ }).click();
  });

  await run('logs-date-picker', async () => {
    await go('/logs', 320, 568);
    await page.getByPlaceholder('搜索日志内容').fill('回归');
    await page.getByRole('button', { name: '搜索', exact: true }).click();
    assert.equal(await page.locator('.log-mobile-item').count(), 1);
    await page.locator('input.el-range-input[placeholder="开始时间"]').click();
    const picker = page.locator('.el-picker__popper:visible'); await picker.waitFor(); await fit(picker, 'date range popper');
    for (const button of await picker.locator('.el-picker-panel__footer button').all()) await fit(button, 'date footer');
    await page.getByRole('heading', { name: '操作日志' }).click();
  });

  await run('recharge-actions', async () => {
    await go('/recharge', 320);
    await page.locator('.amount-option').last().click();
    await page.getByPlaceholder('请输入充值金额').fill('100');
    await page.getByRole('button', { name: '立即充值', exact: true }).click({ trial: true });
    await inspect('recharge-alipay', true);
    await page.getByRole('tab', { name: '卡密充值', exact: true }).click();
    await inspect('recharge-card', true);
  });

  await run('support-portrait-landscape', async () => {
    await go('/dashboard', 320);
    await page.getByRole('button', { name: '打开在线客服', exact: true }).click();
    const chat = page.locator('.chat-window'); await chat.waitFor(); await fit(chat, 'support window');
    await page.getByRole('button', { name: '最小化客服' }).click();
    assert.equal(await page.getByRole('button', { name: '打开在线客服' }).count(), 0, 'only one minimized launcher');
    await page.getByRole('button', { name: '恢复客服对话' }).click();
    await page.setViewportSize({ width: 844, height: 390 }); await fit(chat, 'landscape support window');
    await page.getByPlaceholder('请输入您的问题...').fill('测试输入');
    const send = page.getByRole('button', { name: '发送', exact: true }); await fit(send, 'support send'); await send.click({ trial: true });
    await page.getByRole('button', { name: '关闭客服' }).click();
  });

  await run('captcha-narrow-auth-forms', async () => {
    for (const route of ['/login', '/register']) {
      await go(route, 320, 568);
      const captcha = page.locator('[data-test-captcha]'); await captcha.waitFor(); await fit(captcha, 'compact captcha');
      await inspect(`${route}-captcha`);
    }
  });
}
