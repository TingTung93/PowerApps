import assert from 'node:assert/strict'
import { inboundEligible, custodyEligible, groupByRecipient } from '../src/manifestEligibility.js'

const packages = [
  { trackingNumber: 'A', status: 'READY_FOR_PICKUP', receivedDate: '2026-08-04', location: 'Dock 1', recipient: 'Bob', manifestId: '' },
  { trackingNumber: 'B', status: 'VOIDED',           receivedDate: '2026-08-04', location: 'Dock 1', recipient: 'Bob', manifestId: '' },
  { trackingNumber: 'C', status: 'READY_FOR_PICKUP', receivedDate: '2026-08-03', location: 'Dock 1', recipient: 'Al',  manifestId: '' },
  { trackingNumber: 'D', status: 'PICKED_UP',        receivedDate: '2026-08-04', location: 'Dock 2', recipient: '',    manifestId: '' },
  { trackingNumber: 'E', status: 'READY_FOR_PICKUP', receivedDate: '2026-08-04', location: 'Dock 1', recipient: 'Al',  manifestId: 'MNF-1' }
]

assert.deepEqual(
  inboundEligible(packages, { date: '2026-08-04', location: 'Dock 1' }).map(p => p.trackingNumber),
  ['A'], 'inbound date+location')
assert.deepEqual(
  inboundEligible(packages, { date: '2026-08-04', location: '' }).map(p => p.trackingNumber).sort(),
  ['A', 'D'], 'inbound any location')

assert.deepEqual(
  custodyEligible(packages, { date: '2026-08-04', recipient: '' }).map(p => p.trackingNumber).sort(),
  ['A', 'D', 'E'], 'custody date, any recipient')
assert.deepEqual(
  custodyEligible(packages, { date: '2026-08-04', recipient: 'Al' }).map(p => p.trackingNumber),
  ['E'], 'custody recipient filter')

const groups = groupByRecipient(custodyEligible(packages, { date: '2026-08-04', recipient: '' }))
assert.deepEqual(groups.map(g => g.recipient), ['Al', 'Bob', 'Unassigned'], 'group order')
assert.deepEqual(groups.find(g => g.recipient === 'Unassigned').items.map(p => p.trackingNumber), ['D'], 'unassigned bucket')

console.log('ManifestEligibilityTest: PASS')
