const FALLBACK_CLIENT_CONFIG = Object.freeze({
  branding: Object.freeze({
    siteName: "二开台",
    siteKeywords: "网课,在线教育",
    siteDescription: "专业的在线网课服务平台",
  }),
  session: Object.freeze({
    autoRefreshEnabled: true,
  }),
});

let currentConfig = FALLBACK_CLIENT_CONFIG;

function stringValue(value, fallback) {
  return typeof value === "string" ? value : fallback;
}

export function normalizeClientConfig(value) {
  const branding = value?.branding;
  const session = value?.session;
  return Object.freeze({
    branding: Object.freeze({
      siteName: stringValue(branding?.siteName, FALLBACK_CLIENT_CONFIG.branding.siteName),
      siteKeywords: stringValue(branding?.siteKeywords, FALLBACK_CLIENT_CONFIG.branding.siteKeywords),
      siteDescription: stringValue(
        branding?.siteDescription,
        FALLBACK_CLIENT_CONFIG.branding.siteDescription
      ),
    }),
    session: Object.freeze({
      autoRefreshEnabled:
        typeof session?.autoRefreshEnabled === "boolean"
          ? session.autoRefreshEnabled
          : FALLBACK_CLIENT_CONFIG.session.autoRefreshEnabled,
    }),
  });
}

export function applyClientConfig(value) {
  currentConfig = normalizeClientConfig(value);
  return currentConfig;
}

export function resetClientConfig() {
  currentConfig = FALLBACK_CLIENT_CONFIG;
  return currentConfig;
}

export function getClientConfigSnapshot() {
  return currentConfig;
}

export function isClientAutoRefreshEnabled() {
  return currentConfig.session.autoRefreshEnabled;
}

export { FALLBACK_CLIENT_CONFIG };
