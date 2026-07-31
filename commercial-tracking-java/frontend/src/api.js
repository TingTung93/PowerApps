const token = window.__COMMERCIAL_TRACKING_TOKEN__

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    cache: 'no-store',
    headers: {
      'Content-Type': 'application/json',
      'X-Session-Token': token,
      ...(options.headers || {})
    }
  })
  const body = await response.json()
  if (!response.ok) throw new Error(body.message || `Request failed (${response.status})`)
  return body
}

export const api = {
  state: () => request('/api/state'),
  scan: payload => request('/api/scan', { method: 'POST', body: JSON.stringify(payload) }),
  configure: payload => request('/api/configure', { method: 'POST', body: JSON.stringify(payload) }),
  assignRecipient: payload => request('/api/recipient', { method: 'POST', body: JSON.stringify(payload) }),
  voidPackage: payload => request('/api/void', { method: 'POST', body: JSON.stringify(payload) }),
  manifest: payload => request('/api/manifest', { method: 'POST', body: JSON.stringify(payload) }),
  shutdown: () => request('/api/shutdown', { method: 'POST', body: '{}' })
}
