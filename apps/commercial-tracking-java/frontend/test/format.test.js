import assert from 'node:assert/strict'
import { formatDate, configureTimeFormat } from '../src/format.js'

// A fixed instant. Assertions are host-zone/locale independent: they compare the two
// preference outputs against each other rather than asserting a literal clock string.
const iso = '2026-08-04T21:30:45Z'

configureTimeFormat('12h')
const twelve = formatDate(iso)
configureTimeFormat('24h')
const twentyFour = formatDate(iso)

assert.equal(twelve.includes(':45'), false, '12h output has no seconds')
assert.equal(twentyFour.includes(':45'), false, '24h output has no seconds')
assert.notEqual(twelve, twentyFour, '12h and 24h render the same instant differently')

// Default / unknown preference falls back to 12h behavior.
configureTimeFormat(undefined)
assert.equal(formatDate(iso), twelve, 'default preference matches 12h output')
configureTimeFormat('anything-else')
assert.equal(formatDate(iso), twelve, 'unknown preference matches 12h output')

// Empty and unparseable inputs are handled without throwing.
assert.equal(formatDate(''), '—', 'empty value renders the em dash')
assert.equal(formatDate('not-a-date'), 'not-a-date', 'unparseable value passes through')

// Compact mode also honors the toggle and drops seconds.
configureTimeFormat('12h')
const compact12 = formatDate(iso, true)
configureTimeFormat('24h')
const compact24 = formatDate(iso, true)
assert.equal(compact12.includes(':45'), false, 'compact 12h has no seconds')
assert.equal(compact24.includes(':45'), false, 'compact 24h has no seconds')
assert.notEqual(compact12, compact24, 'compact mode differs by preference')

console.log('FormatTest: PASS')
