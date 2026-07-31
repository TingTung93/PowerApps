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
  lookup: payload => request('/api/lookup', { method: 'POST', body: JSON.stringify(payload) }),
  configure: payload => request('/api/configure', { method: 'POST', body: JSON.stringify(payload) }),
  assignRecipient: payload => request('/api/recipient', { method: 'POST', body: JSON.stringify(payload) }),
  assignRecipients: payload => request('/api/recipients', { method: 'POST', body: JSON.stringify(payload) }),
  voidPackage: payload => request('/api/void', { method: 'POST', body: JSON.stringify(payload) }),
  correctPackage: payload => request('/api/correct', { method: 'POST', body: JSON.stringify(payload) }),
  resolveConflict: payload => request('/api/conflict/resolve', { method: 'POST', body: JSON.stringify(payload) }),
  manifest: payload => request('/api/manifest', { method: 'POST', body: JSON.stringify(payload) }),
  reprintManifest: payload => request('/api/manifest/reprint', { method: 'POST', body: JSON.stringify(payload) }),
  report: payload => request('/api/report', { method: 'POST', body: JSON.stringify(payload) }),
  reportRange: payload => request('/api/report/range', { method: 'POST', body: JSON.stringify(payload) }),
  preferences: payload => request('/api/preferences', { method: 'POST', body: JSON.stringify(payload) }),
  saveSharedSettings: payload => request('/api/settings/shared', { method: 'POST', body: JSON.stringify(payload) }),
  rollbackSharedSettings: () => request('/api/settings/shared/rollback', { method: 'POST', body: '{}' }),
  retryPending: () => request('/api/recovery/retry', { method: 'POST', body: '{}' }),
  rebuildProjection: () => request('/api/recovery/rebuild', { method: 'POST', body: '{}' }),
  exportDiagnostics: () => request('/api/diagnostics/export', { method: 'POST', body: '{}' }),
  finishSession: payload => request('/api/session/finish', { method: 'POST', body: JSON.stringify(payload) }),
  shutdown: () => request('/api/shutdown', { method: 'POST', body: '{}' })
}
