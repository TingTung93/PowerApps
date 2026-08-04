import assert from 'node:assert/strict'
import { ScannerCapture, recommendScannerSettings } from '../src/scannerCapture.js'

const automatic = new ScannerCapture()
let at = 1000
for (let i = 0; i < 12; i++) {
  automatic.character(at, i)
  at += 20
}
assert.equal(automatic.shouldCompleteAfterIdle(at + 120, '123456789012'), true, 'scanner burst completes after idle')

const human = new ScannerCapture()
at = 1000
for (let i = 0; i < 8; i++) {
  human.character(at, i)
  at += 180
}
assert.equal(human.shouldCompleteAfterIdle(at + 120, '12345678'), false, 'human typing does not auto-submit')

const edit = new ScannerCapture()
at = 1000
for (let i = 0; i < 8; i++) { edit.character(at, i); at += 15 }
edit.edit()
assert.equal(edit.shouldCompleteAfterIdle(at + 500, '1234567'), false, 'editing cancels auto-submit')

const terminator = new ScannerCapture({ completionMode: 'terminator', terminator: 'Tab' })
assert.equal(terminator.isTerminator('Tab'), true)
assert.equal(terminator.shouldCompleteForTerminator('ABC123'), true)
assert.equal(terminator.shouldCompleteAfterIdle(5000, 'ABC123'), false)

const debounce = new ScannerCapture()
assert.equal(debounce.accept('ABC123', 1000), true)
assert.equal(debounce.accept('ABC123', 1100), false, 'repeat submission is debounced')
assert.equal(debounce.accept('ABC123', 2000), true)

// Structure-aware completion: a long 2D label (FedEx ANSI MH10 / GS1) streams in
// multiple separator-delimited segments. The short idle window must not truncate it.
const openEnvelope = '[)>0131Z123456789012' // still streaming, no EOT terminator yet
const ansi = new ScannerCapture()
at = 1000
for (let i = 0; i < 12; i++) { ansi.character(at, i); at += 20 }
assert.equal(ansi.shouldCompleteAfterIdle(at + 120, openEnvelope), false, 'open ANSI envelope does not auto-submit on the short idle window')
assert.equal(ansi.shouldCompleteAfterIdle(at + 600, openEnvelope), true, 'open envelope completes after the long settle window when no terminator arrives')

const terminated = '[)>0131Z1234567890123456789011ZJANE DOE2.5LB'
const complete2d = new ScannerCapture()
at = 1000
for (let i = 0; i < 12; i++) { complete2d.character(at, i); at += 20 }
assert.equal(complete2d.shouldCompleteAfterIdle(at + 120, terminated), true, 'terminated ANSI envelope (EOT) auto-submits on the normal idle window')

const trailingSeparator = new ScannerCapture()
at = 1000
for (let i = 0; i < 12; i++) { trailingSeparator.character(at, i); at += 20 }
assert.equal(trailingSeparator.shouldCompleteAfterIdle(at + 120, '013103001234'), false, 'a value ending in a separator is mid-stream and does not auto-submit')

assert.equal(new ScannerCapture().completionDelayMs(openEnvelope), 600, 'open structured payload schedules the long settle delay')
assert.equal(new ScannerCapture().completionDelayMs(terminated), 120, 'terminated payload schedules the short idle delay')
assert.equal(new ScannerCapture().completionDelayMs('1Z999AA10123456784'), 120, 'a simple burst schedules the short idle delay')

assert.equal(new ScannerCapture().paste(1000, 'ABC123'), true, 'complete paste is accepted')
assert.equal(recommendScannerSettings([
  { terminator: 'Enter', maxGapMs: 25 },
  { terminator: 'Enter', maxGapMs: 30 },
  { terminator: 'Enter', maxGapMs: 28 }
]).completionMode, 'terminator')
assert.equal(recommendScannerSettings([
  { terminator: '', maxGapMs: 40 },
  { terminator: '', maxGapMs: 42 },
  { terminator: '', maxGapMs: 45 }
]).completionMode, 'automatic')

console.log('ScannerCaptureTest: PASS')
