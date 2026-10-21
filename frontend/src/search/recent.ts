// The last few things opened from the search palette, remembered in this browser only.

export interface RecentItem {
  key: string // unique: "design:5" or "page:/masters/sizes"
  title: string
  subtitle?: string
  url: string
  type: string // "page" or a search type like "design"
}

const STORAGE_KEY = 'erp.recent'
const MAX = 6

export function loadRecent(): RecentItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as RecentItem[]) : []
  } catch {
    return []
  }
}

export function pushRecent(item: RecentItem) {
  try {
    const list = [item, ...loadRecent().filter((r) => r.key !== item.key)].slice(0, MAX)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch {
    /* storage blocked: not important */
  }
}
