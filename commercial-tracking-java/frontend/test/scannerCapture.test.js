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
