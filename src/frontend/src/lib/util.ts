export function formatMs(ms: number | undefined | null): string {
  if (ms == null || Number.isNaN(ms)) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

export function formatScore(score: number | undefined | null): string {
  if (score == null || Number.isNaN(score)) return '-'
  return score.toFixed(4)
}

export function clip(text: string, maxLen = 220): string {
  const cleaned = text.replace(/\s+/g, ' ').trim()
  if (cleaned.length <= maxLen) return cleaned
  return `${cleaned.slice(0, maxLen)}…`
}

export function safeString(value: unknown): string {
  if (value == null) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
