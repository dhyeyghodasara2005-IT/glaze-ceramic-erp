import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Lets other places (the search palette, links) open a page's drawer through the URL:
 *   /masters/sizes?open=5   -> edit size #5
 *   /masters/sizes?new=1    -> "New size" form
 * The parameter is removed afterwards, so Back / refresh don't re-open it.
 *
 * - items:    the page's already-loaded list (simple masters), searched for the id
 * - fetchOne: for paged lists (designs, users), loads the record by id instead
 * - onOpen:   receives the record, or undefined for "new"
 */
export function useOpenFromUrl<T extends { id: number }>({ items, fetchOne, onOpen, enabled = true }: {
  items?: T[]
  fetchOne?: (id: number) => Promise<T>
  onOpen: (item?: T) => void
  /** false = ignore the URL (e.g. the user can't edit on this page) */
  enabled?: boolean
}) {
  const [params, setParams] = useSearchParams()

  useEffect(() => {
    const openId = Number(params.get('open'))
    const isNew = params.get('new') === '1'
    if (!openId && !isNew) return

    const clear = () => {
      const next = new URLSearchParams(params)
      next.delete('open')
      next.delete('new')
      setParams(next, { replace: true })
    }
    if (!enabled) {
      clear()
      return
    }
    if (isNew) {
      onOpen(undefined)
      clear()
      return
    }
    if (fetchOne) {
      clear() // clear first so a re-render doesn't fetch twice
      fetchOne(openId).then(onOpen).catch(() => undefined)
      return
    }
    if (items) {
      const found = items.find((i) => i.id === openId)
      if (found) onOpen(found)
      clear()
    }
    // wait while items are still loading
  }, [params, items, fetchOne, onOpen, enabled, setParams])
}
