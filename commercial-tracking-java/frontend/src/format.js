// Shared date formatting for records and timestamps, using the operator's locale.
const dateTime = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' })
const shortTime = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })

export function formatDate(value, compact = false) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return compact ? shortTime.format(parsed) : dateTime.format(parsed)
}
