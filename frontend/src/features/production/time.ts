import dayjs, { type Dayjs } from 'dayjs'

// The API speaks plant clock time without a zone: "2026-10-23T08:30:00".

export const DATE_TIME = 'DD-MM-YYYY HH:mm'

/** dayjs -> "2026-10-23T08:30:00" (what the server expects for a LocalDateTime). */
export const toLocalDateTime = (d: Dayjs) => d.second(0).millisecond(0).format('YYYY-MM-DDTHH:mm:ss')

/** "2026-10-23T08:30:00" -> "08:30" */
export const clock = (iso?: string) => (iso ? dayjs(iso).format('HH:mm') : '–')

/** "23 Oct, 08:30" */
export const dayClock = (iso?: string) => (iso ? dayjs(iso).format('DD MMM, HH:mm') : '–')

/** 9 h 05 m between two times (or until now). */
export function duration(start: string, end?: string): string {
  const minutes = Math.max(0, (end ? dayjs(end) : dayjs()).diff(dayjs(start), 'minute'))
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h} h ${String(m).padStart(2, '0')} m` : `${m} m`
}

/** "20:00:00" -> "20:00" */
export const hhmm = (time?: string) => (time ? time.slice(0, 5) : '–')
