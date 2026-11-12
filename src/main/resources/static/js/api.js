export async function request(path, options = {}) {
  let response;
  try { response = await fetch(path, options); }
  catch { throw new Error('无法连接服务，请确认后端已启动。'); }
  let data = null;
  try { data = await response.json(); } catch { /* 404 画像可能没有响应体。 */ }
  if (!response.ok) throw new Error(data?.error || data?.answer || `请求失败（${response.status}）`);
  return data;
}
export const post = (path, body) => request(path, {
  method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
});
export const put = (path, body) => request(path, {
  method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
});
export function profileUrl(userId) { return `/api/profiles/${encodeURIComponent(userId)}`; }
