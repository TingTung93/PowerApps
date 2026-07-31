export const DEFAULT_SCANNER_SETTINGS = Object.freeze({
  completionMode: 'automatic',
  terminator: 'Enter',
  idleDelayMs: 120,
  burstThresholdMs: 50,
  minimumLength: 6,
  duplicateWindowMs: 750
})

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
    return this.settings.completionMode === 'automatic' && !this.edited &&
      this.isScannerBurst(value.length) && now - this.lastCharacterAt >= this.settings.idleDelayMs
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
