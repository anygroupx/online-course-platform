import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';
import { createServer } from 'vite';
import { UID, KEY, user, order, routes, fixture } from './mobile-fixtures.mjs';
import { runMobileFlows } from './mobile-flows.mjs';

// Actual router/store/pages/styles; every API request is intercepted, including writes.
const artifacts = process.env.SCREENSHOT_DIR || '/tmp/course-mobile-integration';
await fs.mkdir(artifacts, { recursive: true });
const html = (await fs.readFile('index.html', 'utf8')).replace('<script type="module" src="/src/main.js"></script>', `<script type="module">
import { applyAuthSession } from '/src/utils/authSession.js';
if (!['/login','/register','/guest-order','/privacy-policy','/service-agreement'].includes(location.pathname)) {
  applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature', ...${JSON.stringify(user)}});
}
await import('/src/main.js');
</script>`);
const server = await createServer({ logLevel: 'error', server: { host: '127.0.0.1', port: 0, strictPort: false }, plugins: [{
  name: 'mobile-test-page', configureServer(vite) { vite.middlewares.use(async (req, res, next) => {
    if (!routes.includes(new URL(req.url, 'http://test').pathname)) return next();
    res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(await vite.transformIndexHtml(req.url, html));
  }); },
}] });
const issues = []; const unknown = new Set(); const writes = []; const requests = []; let browser; let externalFailure = null;
try {
  await server.listen();
  const baseURL = `http://127.0.0.1:${server.httpServer.address().port}`;
  browser = await chromium.launch({ executablePath: process.env.BROWSER_PATH || (existsSync('/snap/bin/chromium') ? '/snap/bin/chromium' : undefined), headless: true, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, reducedMotion: 'reduce' });
  await context.route('**/*', async (route) => {
    const request = route.request(); const url = new URL(request.url());
    if (url.origin !== baseURL) {
      if (url.hostname === 'challenges.cloudflare.com') return route.fulfill({ contentType: 'application/javascript', body: `window.turnstile = { render(element, options) { const widget = document.createElement('button'); widget.type='button'; widget.textContent='本地人机验证占位'; widget.dataset.testCaptcha='true'; widget.style.cssText='display:block;width:'+ (options.size==='compact'?'150px':'300px')+';height:65px;'; element.append(widget); return element; }, remove(element) { element.replaceChildren(); }, reset() {} };` });
      if (url.hostname === 'acg.yaohud.cn') return route.fulfill({ status: 204, body: '' });
      unknown.add(`NETWORK ${url.origin}`); return route.abort();
    }
    if (!url.pathname.startsWith('/api/')) return route.continue();
    if (url.pathname.startsWith('/api/api/')) unknown.add(`DUPLICATE API PREFIX ${url.pathname}`);
    requests.push({ path: url.pathname, query: url.search, method: request.method(), headers: request.headers(), body: request.postData() });
    if (request.method() !== 'GET') writes.push(url.pathname);
    if (url.pathname.startsWith('/api/external/') && externalFailure) return route.fulfill({ status: externalFailure.status, headers: { 'Content-Type': 'application/json', 'Retry-After': '9' }, body: JSON.stringify({ code: externalFailure.code, message: externalFailure.message, errorId: 'test-error-id' }) });
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 1, message: '操作成功', data: fixture(url, request, unknown) }) });
  });
  const page = await context.newPage(); page.setDefaultTimeout(8000); page.setDefaultNavigationTimeout(45000);
  page.on('pageerror', (error) => issues.push({ page: new URL(page.url()).pathname, kind: 'runtime', error: error.message }));
  const inspect = async (name, screenshot = false) => {
    const failures = await page.evaluate(() => {
      const width = window.innerWidth;
      const roots = [document.documentElement, document.body, document.querySelector('main.el-main')].filter(Boolean);
      const found = roots.filter((e) => e.scrollWidth > e.clientWidth + 2).map((e) => ({ kind: 'overflow', target: e.tagName, size: `${e.scrollWidth}/${e.clientWidth}` }));
      for (const el of document.querySelectorAll('#app button, main.el-main .el-input, main.el-main .el-select, main.el-main .el-pagination, main.el-main .el-date-editor, [role="dialog"] button')) {
        const r = el.getBoundingClientRect();
        if (!r.width || !r.height || getComputedStyle(el).visibility === 'hidden' || el.closest('.el-table, pre, .el-tabs__nav-scroll, .sidebar:not(.mobile-visible)')) continue;
        let clipped = r.left < -2 || r.right > width + 2;
        for (let parent = el.parentElement; parent && !clipped; parent = parent.parentElement) {
          if (['hidden', 'clip', 'auto', 'scroll'].includes(getComputedStyle(parent).overflowX)) {
            const bounds = parent.getBoundingClientRect();
            clipped = r.left < bounds.left - 2 || r.right > bounds.right + 2;
          }
        }
        if (clipped) found.push({ kind: 'clipped-control', target: `${el.className}: ${el.textContent.trim().slice(0, 30)}`, x: r.x, right: r.right });
      }
      return found;
    });
    issues.push(...failures.map((failure) => ({ page: name, ...failure })));
    if (screenshot || failures.length) await page.screenshot({ path: path.join(artifacts, `${name.replaceAll('/', '_')}-${page.viewportSize().width}.png`) });
    console.log(`${name} @${page.viewportSize().width}: ${failures.length ? JSON.stringify(failures) : 'layout OK'}`);
  };
  const widths = process.env.MOBILE_WIDTHS ? process.env.MOBILE_WIDTHS.split(',').map(Number) : [320, 390, 768, 1440];
  const selected = process.env.MOBILE_INTERACTIONS_ONLY ? [] : process.env.MOBILE_ROUTES ? process.env.MOBILE_ROUTES.split(',') : routes;
  for (const width of widths) {
    await page.setViewportSize({ width, height: width >= 1000 ? 1000 : 844 });
    for (const route of selected) {
      await page.goto(baseURL + route, { waitUntil: 'networkidle' });
      await page.waitForTimeout(100);
      await inspect(route);
    }
  }
  if (process.env.MOBILE_FLOWS !== '0') await runMobileFlows({ page, context, baseURL, inspect, artifacts, issues, requests, setExternalFailure: (value) => { externalFailure = value; } });
  await fs.writeFile(path.join(artifacts, 'report.json'), JSON.stringify({ issues, unknown: [...unknown], writes }, null, 2));
  console.log(`Survey complete: ${selected.length * widths.length} route/viewport checks. Issues: ${issues.length}; unknown requests: ${[...unknown].join(', ')}`);
  if (!process.env.MOBILE_AUDIT) { assert.deepEqual(issues, []); assert.deepEqual([...unknown], []); }
} finally {
  if (browser) await browser.close();
  await server.close();
}
