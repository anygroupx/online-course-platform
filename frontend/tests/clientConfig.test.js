import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  applyClientConfig,
  FALLBACK_CLIENT_CONFIG,
  getClientConfigSnapshot,
  isClientAutoRefreshEnabled,
  normalizeClientConfig,
  resetClientConfig,
} from "../src/utils/clientConfigState.js";

const source = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

test("client bootstrap state accepts only the typed public fields", () => {
  const normalized = normalizeClientConfig({
    branding: {
      siteName: "课程中心",
      siteKeywords: "课程,学习",
      siteDescription: "课程服务",
      systemNotice: "不应进入状态",
    },
    session: {
      autoRefreshEnabled: false,
      tokenExpireMinutes: 999,
    },
    userRegisterFee: 0,
  });

  assert.deepEqual(normalized, {
    branding: {
      siteName: "课程中心",
      siteKeywords: "课程,学习",
      siteDescription: "课程服务",
    },
    session: { autoRefreshEnabled: false },
  });
  assert.equal(Object.hasOwn(normalized, "userRegisterFee"), false);
  assert.equal(Object.hasOwn(normalized.session, "tokenExpireMinutes"), false);
});

test("missing or malformed bootstrap fields fall back without persistent storage", () => {
  applyClientConfig({ branding: { siteName: 123 }, session: { autoRefreshEnabled: "0" } });
  assert.deepEqual(getClientConfigSnapshot(), FALLBACK_CLIENT_CONFIG);
  assert.equal(isClientAutoRefreshEnabled(), true);

  applyClientConfig({
    branding: {
      siteName: "课程中心",
      siteKeywords: "课程",
      siteDescription: "说明",
    },
    session: { autoRefreshEnabled: false },
  });
  assert.equal(isClientAutoRefreshEnabled(), false);
  assert.deepEqual(resetClientConfig(), FALLBACK_CLIENT_CONFIG);
});

test("ordinary login and main layout never call the admin system config endpoint", () => {
  const userStore = source("../src/stores/user.js");
  const layout = source("../src/layouts/MainLayout.vue");
  for (const text of [userStore, layout]) {
    assert.doesNotMatch(text, /getSettings/);
    assert.doesNotMatch(text, /\/system\/config/);
  }
  assert.match(userStore, /useAppConfigStore\(\)\.ensureLoaded\(\)/);
  assert.match(layout, /appConfigStore\.ensureLoaded\(\)/);
});

test("bootstrap loading is public, silent, single-flight and not stored in localStorage", () => {
  const api = source("../src/api/clientConfig.js");
  const store = source("../src/stores/appConfig.js");
  const request = source("../src/utils/request.js");
  const router = source("../src/router/index.js");
  const settings = source("../src/views/Settings.vue");

  assert.match(api, /url: "\/client\/bootstrap"/);
  assert.match(api, /suppressGlobalError: true/);
  assert.match(request, /"\/client\/bootstrap"/);
  assert.match(store, /if \(loadPromise\) return loadPromise;/);
  assert.match(store, /resetClientConfig\(\)/);
  assert.doesNotMatch(request, /localStorage.*auto_refresh_token_enabled/);
  assert.doesNotMatch(router, /localStorage.*auto_refresh_token_enabled/);
  assert.doesNotMatch(settings, /localStorage/);
});
