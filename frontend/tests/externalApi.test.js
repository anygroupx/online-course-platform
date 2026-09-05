import test from 'node:test';
import assert from 'node:assert/strict';
import * as api from '../src/api/external.js';
import { externalEndpoints, externalEndpointUrl, externalExamples } from '../src/utils/externalApiGuide.js';

for (const endpoint of externalEndpoints) {
  test(`${endpoint.path} sends form credentials in the body without JWT, cookies or duplicate /api`, async (t) => {
    const calls = [];
    t.mock.method(globalThis, 'fetch', async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify({ code: 1, data: {} }), { status: 200 }); });
    const data = { uid: 'test-uuid', api_key: 'a+b&c=d', pass: '中 文+&password' };
    await api[endpoint.id](data);
    assert.equal(calls.length, 1);
    const { url, options } = calls[0];
    assert.equal(url, `/api/external/${endpoint.path}`);
    assert.equal(options.method, 'POST');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.headers.Authorization, undefined);
    assert.match(options.headers['Content-Type'], /application\/x-www-form-urlencoded/);
    assert.deepEqual(Object.fromEntries(options.body), data);
    assert.ok(!url.includes(data.api_key));
  });
}
for (const status of [401, 403, 429, 503]) {
  test(`HTTP ${status} preserves safe response detail without refreshing sessions or retrying a write`, async (t) => {
    let count = 0;
    t.mock.method(globalThis, 'fetch', async () => { count++; return new Response(JSON.stringify({ code: -205, message: '受控错误', errorId: 'public-error-id' }), { status, headers: { 'Retry-After': '10' } }); });
    await assert.rejects(api.createOrder({ api_key: 'never-leak-this', pass: 'private-password' }), (error) => {
      assert.equal(error.response.status, status);
      assert.equal(error.response.retryAfter, '10');
      assert.equal(error.response.data.errorId, 'public-error-id');
      assert.ok(!JSON.stringify(error).includes('never-leak-this'));
      return true;
    });
    assert.equal(count, 1);
  });
}
test('business failures and non-JSON proxy errors are not reported as success', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ code: -1, message: '业务失败' }), { status: 200 }));
  await assert.rejects(api.getMoney({}), /业务失败/);
  globalThis.fetch = async () => new Response('<h1>upstream error</h1>', { status: 502 });
  await assert.rejects(api.getMoney({}), /HTTP 502/);
});
test('base paths and all four code examples contain only placeholders', () => {
  for (const base of ['/api', '/api/', 'https://api.example/v2/api/']) {
    for (const endpoint of externalEndpoints) {
      const url = externalEndpointUrl(base, endpoint.path);
      assert.ok(!url.includes('/api/api/'));
      assert.ok(url.endsWith(`/external/${endpoint.path}`));
      const examples = externalExamples(endpoint, url);
      assert.equal(examples.length, 4);
      for (const { code } of examples) {
        assert.ok(code.includes('YOUR_UID') && code.includes('YOUR_API_KEY'));
        assert.ok(code.includes('api_key'));
      }
      assert.ok(examples[0].code.includes('--data-urlencode'));
      assert.ok(examples[3].code.includes("$result['message']"));
    }
  }
});
