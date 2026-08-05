// Pure, Node-testable manifest eligibility derived from the `packages` array
// (which carries receivedDate, status, location, recipient, manifestId).
// The backend re-derives and validates independently; this drives the UI only.

export function inboundEligible(packages, { date, location }) {
  return packages.filter(pkg =>
    pkg.status !== 'VOIDED' &&
    !pkg.manifestId &&
    pkg.receivedDate === date &&
    (!location || pkg.location === location))
}

export function custodyEligible(packages, { date, recipient }) {
  return packages.filter(pkg =>
    (pkg.status === 'READY_FOR_PICKUP' || pkg.status === 'PICKED_UP') &&
    pkg.receivedDate === date &&
    (!recipient || pkg.recipient === recipient))
}

export function groupByRecipient(list) {
  const groups = new Map()
  const unassigned = []
  for (const pkg of list) {
    const recipient = (pkg.recipient || '').trim()
    if (!recipient) { unassigned.push(pkg); continue }
    if (!groups.has(recipient)) groups.set(recipient, [])
    groups.get(recipient).push(pkg)
  }
  const sorted = [...groups.keys()]
    .sort((a, b) => a.localeCompare(b))
    .map(recipient => ({ recipient, items: groups.get(recipient) }))
  if (unassigned.length) sorted.push({ recipient: 'Unassigned', items: unassigned })
  return sorted
}
