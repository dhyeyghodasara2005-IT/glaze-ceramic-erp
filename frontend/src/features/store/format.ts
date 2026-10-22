import dayjs from 'dayjs'

// Indian number style everywhere in the store: 1,23,456.50

/** ₹ 1,23,456.50 (always 2 decimals); negative: −₹ 4,000.00 */
export function money(value?: number | null): string {
  if (value == null) return '–'
  const text = Math.abs(value).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  return (value < 0 ? '−₹ ' : '₹ ') + text
}

/** 1,250.5 with at most the unit's decimals; trailing zeros dropped. */
export function qty(value?: number | null, decimals = 3): string {
  if (value == null) return '–'
  return value.toLocaleString('en-IN', { maximumFractionDigits: decimals })
}

/** "22 Oct 2026" */
export const shortDate = (iso?: string) => (iso ? dayjs(iso).format('DD MMM YYYY') : '–')

/** "22 Oct 2026, 4:05 pm" */
export const dateTime = (iso?: string) => (iso ? dayjs(iso).format('DD MMM YYYY, h:mm a') : '–')

/** How many decimal places a number has: 10.25 -> 2, 10 -> 0. */
export function decimalsOf(value: number): number {
  const text = String(value)
  if (text.includes('e-')) return Number(text.split('e-')[1])
  return text.includes('.') ? text.split('.')[1].length : 0
}
