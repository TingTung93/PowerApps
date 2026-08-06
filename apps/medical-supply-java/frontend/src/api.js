const token = window.__MEDSUPPLY_TOKEN__

async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    cache: 'no-store',
    headers: { 'Content-Type': 'application/json', 'X-Session-Token': token, ...(options.headers || {}) }
  })
  const body = await response.json()
  if (!response.ok) throw new Error(body.message || `Request failed (${response.status})`)
  return body
}

const post = (path, payload) => request(path, { method: 'POST', body: JSON.stringify(payload || {}) })

export const api = {
  state: () => request('/api/state'),
  chooseFolder: () => post('/api/choose-folder', {}),
  settings: payload => post('/api/settings', payload),
  distro: payload => post('/api/distro', payload),
  previewReceive: payload => post('/api/preview-receive', payload),
  itemHistory: payload => post('/api/item-history', payload),
  receive: payload => post('/api/receive', payload),
  pick: payload => post('/api/pick', payload),
  adjust: payload => post('/api/adjust', payload),
  archive: payload => post('/api/archive', payload),
  restore: payload => post('/api/restore', payload),
  archiveExpired: () => post('/api/archive-expired', {}),
  retireProduct: payload => post('/api/retire-product', payload),
  register: payload => post('/api/register', payload),
  gudid: payload => post('/api/gudid', payload),
  report: () => post('/api/report', {}),
  shutdown: () => post('/api/shutdown', {})
}
