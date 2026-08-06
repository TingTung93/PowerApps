// Shared date formatting for records and timestamps, using the operator's locale and host zone.
// A module-level preference toggles 12h vs 24h; seconds are never displayed.
let hour12 = true

function buildDateTime() {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12 })
}

function buildShortTime() {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', hour12 })
}

let dateTime = buildDateTime()
let shortTime = buildShortTime()

// Called when shared settings load/refresh. pref === '24h' selects 24-hour time; anything else is 12-hour.
export function configureTimeFormat(pref) {
  hour12 = pref !== '24h'
  dateTime = buildDateTime()
  shortTime = buildShortTime()
}

export function formatDate(value, compact = false) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return compact ? shortTime.format(parsed) : dateTime.format(parsed)
}
