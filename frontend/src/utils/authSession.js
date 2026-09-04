import { ref } from "vue";

export const accessToken = ref("");
export const sessionUserInfo = ref(readStoredUserInfo());

let refreshPromise = null;

// Remove credentials left by pre-P0 clients. Access credentials now live only in memory.
for (const key of ["token", "tokenTime", "refreshToken", "refreshTokenTime"]) {
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
  for (const key of ["token", "tokenTime", "refreshToken", "refreshTokenTime"]) {
    localStorage.removeItem(key);
  }
}

export function getAccessToken() {
  return accessToken.value;
}

export function isAccessTokenExpired(leewaySeconds = 20) {
  const token = accessToken.value;
  if (!token) return true;
  try {
    const part = token.split(".")[1];
    const normalized = part.replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=")));
    return !payload.exp || payload.exp * 1000 <= Date.now() + leewaySeconds * 1000;
  } catch {
    return true;
  }
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
