import assert from 'node:assert/strict'
import { parseLocations, serializeLocations, addLocation } from '../src/locations.js'

// parse
assert.deepEqual(parseLocations('Main| Dock |Mailroom'), ['Main', 'Dock', 'Mailroom'])
assert.deepEqual(parseLocations(''), [])
assert.deepEqual(parseLocations(null), [])
assert.deepEqual(parseLocations('A||B|'), ['A', 'B'])

// serialize
assert.equal(serializeLocations(['Main', ' Dock ', '']), 'Main|Dock')
assert.equal(serializeLocations([]), '')
assert.equal(serializeLocations(null), '')

// addLocation: happy path (and trims)
let result = addLocation(['Main'], 'Dock')
assert.equal(result.ok, true)
assert.deepEqual(result.list, ['Main', 'Dock'])
assert.equal(result.error, '')
result = addLocation(['Main'], '  Dock  ')
assert.equal(result.ok, true)
assert.deepEqual(result.list, ['Main', 'Dock'])

// addLocation: empty rejected, original list preserved
result = addLocation(['Main'], '   ')
assert.equal(result.ok, false)
assert.deepEqual(result.list, ['Main'])
assert.match(result.error, /enter a location/i)

// addLocation: pipe forbidden
result = addLocation(['Main'], 'Ma|in')
assert.equal(result.ok, false)
assert.match(result.error, /cannot contain/i)

// addLocation: case-insensitive duplicate rejected
result = addLocation(['Main'], 'main')
assert.equal(result.ok, false)
assert.match(result.error, /already exists/i)

// addLocation: total serialized length capped at 500
const nearLimit = 'x'.repeat(499)
result = addLocation([nearLimit], 'yy') // 499 + 1 (pipe) + 2 = 502 > 500
assert.equal(result.ok, false)
assert.match(result.error, /500 characters/i)
result = addLocation([], 'x'.repeat(500)) // exactly 500 is allowed
assert.equal(result.ok, true)

// addLocation: tolerates a null/undefined starting array
result = addLocation(undefined, 'First')
assert.equal(result.ok, true)
assert.deepEqual(result.list, ['First'])

console.log('LocationsTest: PASS')
