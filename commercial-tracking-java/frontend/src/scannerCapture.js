export const DEFAULT_SCANNER_SETTINGS = Object.freeze({
  completionMode: 'automatic',
  terminator: 'Enter',
  idleDelayMs: 120,
  structuredIdleDelayMs: 600,
  burstThresholdMs: 50,
  minimumLength: 6,
  duplicateWindowMs: 750
})

// ANSI MH10 message envelope header and control separators found in FedEx / GS1 2D labels.
const ANSI_ENVELOPE = '[)>'
const GS1_SYMBOLOGY = /^\](?:C1|d2|Q1|e0|C0)/
const GROUP_SEPARATOR = ''
const RECORD_SEPARATOR = ''
const END_OF_TRANSMISSION = ''

export class ScannerCapture {
  constructor(settings = {}) {
    this.settings = { ...DEFAULT_SCANNER_SETTINGS, ...settings }
    this.reset()
    this.lastSubmission = { value: '', at: -Infinity }
  }

  reset() {
    this.startedAt = 0
    this.lastCharacterAt = 0
    this.characterCount = 0
    this.qualifyingGaps = 0
    this.edited = false
  }

  character(at, currentLength) {
    if (!this.startedAt || !this.lastCharacterAt || at - this.lastCharacterAt > this.settings.burstThresholdMs) {
      this.startedAt = at
      this.characterCount = 1
      this.qualifyingGaps = 0
    } else {
      this.characterCount++
      this.qualifyingGaps++
    }
    this.lastCharacterAt = at
    this.edited = false
    return this.isScannerBurst(currentLength + 1)
  }

  edit() {
    this.edited = true
    this.lastCharacterAt = 0
  }

  paste(at, value) {
    this.startedAt = at
    this.lastCharacterAt = at
    this.characterCount = value.length
    this.qualifyingGaps = Math.max(0, value.length - 1)
    this.edited = false
    return value.length >= this.settings.minimumLength
  }

  isTerminator(key) {
    return key === this.settings.terminator ||
      (this.settings.terminator === 'Enter' && (key === 'Enter' || key === 'NumpadEnter'))
  }

  shouldCompleteForTerminator(value) {
    return this.settings.completionMode !== 'manual' && value.length > 0
  }

  shouldCompleteAfterIdle(now, value) {
    if (this.settings.completionMode !== 'automatic' || this.edited) return false
    if (this.isStructured(value)) {
      // A structured 2D label (FedEx ANSI MH10, GS1) streams as several separator-delimited
      // segments whose inter-field pauses routinely exceed the short idle window — completing
      // on that window truncates the label ("parses early"). The [)> envelope, a symbology
      // identifier, or an embedded separator already proves this is a scanner, not a person
      // typing, so the burst heuristic is unnecessary here: complete the instant the label
      // terminates (EOT), otherwise only after a long settle so the whole label is captured.
      return this.isTerminated(value) || now - this.lastCharacterAt >= this.structuredSettleMs()
    }
    // A plain linear barcode: the burst heuristic separates a scan from human typing.
    return this.isScannerBurst(value.length) && now - this.lastCharacterAt >= this.settings.idleDelayMs
  }

  // The structured settle must never fall below the workstation's configured quiet interval —
  // a scanner calibrated with longer pauses (idleDelayMs up to 2000ms) would otherwise submit a
  // multi-part label before it is done.
  structuredSettleMs() {
    return Math.max(this.settings.structuredIdleDelayMs, this.settings.idleDelayMs)
  }

  isStructured(value) {
    if (!value) return false
    return value.startsWith(ANSI_ENVELOPE) || GS1_SYMBOLOGY.test(value) ||
      value.includes(END_OF_TRANSMISSION) || value.endsWith(GROUP_SEPARATOR) || value.endsWith(RECORD_SEPARATOR)
  }

  isTerminated(value) {
    return value.includes(END_OF_TRANSMISSION)
  }

  completionDelayMs(value) {
    return this.isStructured(value) && !this.isTerminated(value)
      ? this.structuredSettleMs() : this.settings.idleDelayMs
  }

  isScannerBurst(length) {
    return length >= this.settings.minimumLength && this.characterCount >= this.settings.minimumLength &&
      this.qualifyingGaps >= this.settings.minimumLength - 1
  }

  accept(value, at) {
    if (!value) return false
    if (value === this.lastSubmission.value && at - this.lastSubmission.at < this.settings.duplicateWindowMs) return false
    this.lastSubmission = { value, at }
    this.reset()
    return true
  }
}

export function recommendScannerSettings(samples) {
  if (!samples || samples.length < 3) throw new Error('Three scanner samples are required.')
  const terminators = samples.map(sample => sample.terminator).filter(Boolean)
  const sameTerminator = terminators.length === 3 && terminators.every(value => value === terminators[0])
  const maxGap = Math.max(...samples.map(sample => sample.maxGapMs || 0))
  if (sameTerminator) return { completionMode: 'terminator', terminator: terminators[0], idleDelayMs: Math.max(120, maxGap * 3) }
  return { completionMode: 'automatic', terminator: 'Enter', idleDelayMs: Math.min(1000, Math.max(120, maxGap * 3)) }
}
