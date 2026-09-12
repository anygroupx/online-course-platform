import { computed, ref } from "vue";

export const accessToken = ref("");
export const sessionUserInfo = ref(readStoredUserInfo());

// LoginResponse publishes uid. Retain userId only for older in-memory sessions.
const sessionIdentity = computed(() => sessionUserInfo.value?.uid || sessionUserInfo.value?.userId || null);
export const hasAuthenticatedSession = computed(() => !!(accessToken.value && sessionIdentity.value));

// Invalidate private UI state on a real session/permission change, not on token rotation,
// balance updates or nickname changes. The server remains the authorization authority.
export const authSessionScope = computed(() => {
  const info = sessionUserInfo.value;
  return JSON.stringify([
    hasAuthenticatedSession.value,
    sessionIdentity.value,
    readAccessTokenClaims(accessToken.value)?.sid || null,
    info?.role ?? null,
    info?.isAdmin === true,
    Array.isArray(info?.permissions) ? [...new Set(info.permissions)].sort() : [],
    info?.mustChangePassword === true,
    info?.mfaRequired === true,
    info?.mfaEnabled === true,
  ]);
});

let refreshPromise = null;

// Remove credentials left by pre-P0 clients. Access credentials now live only in memory.
for (const key of ["token", "tokenTime", "refreshToken", "refreshTokenTime", "api_test_key", "api_test_uid"]) {
  localStorage.removeItem(key);
}
for (const key of ["token_expire_minutes", "refresh_token_expire_days", "auto_refresh_token_enabled"]) {
  localStorage.removeItem(key);
}

function readStoredUserInfo() {
  try {
    return JSON.parse(localStorage.getItem("userInfo") || "null");
  } catch {
    localStorage.removeItem("userInfo");
    return null;
  }
}

export function applyAuthSession(data) {
  if (!data?.token) {
    throw new Error("Authentication response did not contain an access token");
  }
  accessToken.value = data.token;
  const { token: _token, refreshToken: _refreshToken, ...publicInfo } = data;
  sessionUserInfo.value = publicInfo;
  localStorage.setItem("userInfo", JSON.stringify(publicInfo));
}

export function clearAuthSession() {
  accessToken.value = "";
  sessionUserInfo.value = null;
  localStorage.removeItem("userInfo");
  for (const key of ["token", "tokenTime", "refreshToken", "refreshTokenTime", "api_test_key", "api_test_uid"]) {
    localStorage.removeItem(key);
  }
}

export function getAccessToken() {
  return accessToken.value;
}

function readAccessTokenClaims(token) {
  try {
    const part = token.split(".")[1];
    const normalized = part.replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=")));
  } catch {
    return null;
  }
}

export function isAccessTokenExpired(leewaySeconds = 20) {
  const payload = readAccessTokenClaims(accessToken.value);
  return !Number.isFinite(payload?.exp) || payload.exp * 1000 <= Date.now() + leewaySeconds * 1000;
}

export function csrfToken() {
  const prefix = "course_csrf=";
  const item = document.cookie.split(";").map((v) => v.trim()).find((v) => v.startsWith(prefix));
  return item ? decodeURIComponent(item.slice(prefix.length)) : "";
}

export async function refreshAccessSession() {
  if (refreshPromise) return refreshPromise;
  refreshPromise = doRefresh().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function doRefresh() {
  const csrf = csrfToken();
  if (!csrf) throw new Error("Refresh session unavailable");
  const apiBase = import.meta.env.VITE_API_BASE_URL || "/api";
  const response = await fetch(`${apiBase}/auth/refresh`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": csrf,
    },
    body: "{}",
  });
  const body = await response.json().catch(() => null);
  if (!response.ok || body?.code !== 1 || !body?.data?.token) {
    clearAuthSession();
    throw new Error("Refresh session rejected");
  }
  applyAuthSession(body.data);
  return body.data.token;
}
