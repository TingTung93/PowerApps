// Pure parse/serialize/validation for the receiving-locations chip editor.
// The persisted format is a pipe-delimited string; this module never touches the DOM.

export function parseLocations(pipeString) {
  if (!pipeString) return []
  return String(pipeString).split('|').map(value => value.trim()).filter(Boolean)
}

export function serializeLocations(array) {
  if (!array) return ''
  return array.map(value => String(value).trim()).filter(Boolean).join('|')
}

export function addLocation(array, candidate) {
  const list = Array.isArray(array) ? array.slice() : []
  const value = String(candidate == null ? '' : candidate).trim()
  if (value.length === 0) return { ok: false, list, error: 'Enter a location name.' }
  if (value.includes('|')) return { ok: false, list, error: 'Location names cannot contain the "|" character.' }
  if (list.some(existing => existing.toLowerCase() === value.toLowerCase())) {
    return { ok: false, list, error: 'That location already exists.' }
  }
  const next = list.concat(value)
  if (serializeLocations(next).length > 500) {
    return { ok: false, list, error: 'The combined locations exceed 500 characters.' }
  }
  return { ok: true, list: next, error: '' }
}
