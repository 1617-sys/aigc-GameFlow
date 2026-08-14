const TOKEN_KEY = 'gameflow_token'

export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  save: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY)
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (options.body) headers.set('Content-Type', 'application/json')
  const token = session.token()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { ...options, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code >= 400) {
    if (response.status === 401 || response.status === 403) session.clear()
    throw new Error(payload?.msg || `请求失败（${response.status}）`)
  }
  return payload?.data
}

export const api = {
  register: (username, password) => request('/user/register', {
    method: 'POST', body: JSON.stringify({ username, password })
  }),
  login: (username, password) => request('/user/login', {
    method: 'POST', body: JSON.stringify({ username, password })
  }),
  providers: () => request('/api/generation/providers'),
  tasks: () => request('/api/generation/jobs'),
  taskStatuses: (taskUuids) => request('/api/generation/jobs/statuses', {
    method: 'POST', body: JSON.stringify({ taskUuids })
  }),
  events: (uuid) => request(`/api/generation/jobs/${uuid}/events`),
  submit: (form, key) => request('/api/generation/jobs', {
    method: 'POST',
    headers: { 'Idempotency-Key': key },
    body: JSON.stringify(form)
  }),
  cancel: (uuid) => request(`/api/generation/jobs/${uuid}/cancel`, { method: 'POST' }),
  retry: (uuid) => request(`/api/generation/jobs/${uuid}/retry`, { method: 'POST' }),
  image: async (uuid) => {
    const response = await fetch(`/api/generation/jobs/${uuid}/image`, {
      headers: { Authorization: `Bearer ${session.token()}` }
    })
    if (!response.ok) throw new Error('图片读取失败')
    return URL.createObjectURL(await response.blob())
  }
}
