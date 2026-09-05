/** Third-party API transport: no JWT, cookies, refresh, retries or secrets in URLs. */
const baseURL = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/+$/, '');

export async function requestExternal(endpoint, data) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30000);
  try {
    const response = await fetch(`${baseURL}/external/${endpoint}`, {
      method: 'POST',
      credentials: 'omit',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: new URLSearchParams(Object.entries(data).filter(([, value]) => value != null)),
      signal: controller.signal,
    });
    const result = await response.json().catch(() => null);
    if (!response.ok || result?.code !== 1) {
      const error = new Error(result?.message || `请求失败（HTTP ${response.status}）`);
      // Only safe response metadata, never fetch options containing the key/password.
      error.response = {
        status: response.status,
        data: result || { message: error.message },
        retryAfter: response.headers.get('Retry-After'),
      };
      throw error;
    }
    return result;
  } finally {
    clearTimeout(timeout);
  }
}

export const getMoney = (data) => requestExternal('getmoney', data);
export const getPlatforms = (data) => requestExternal('get-platforms', data);
export const queryCourses = (data) => requestExternal('query-courses', data);
export const createOrder = (data) => requestExternal('add', data);
export const queryOrders = (data) => requestExternal('chadan', data);
export const queryProgress = (data) => requestExternal('query-progress', data);
export const retryOrder = (data) => requestExternal('budan', data);
