import { useEffect, useState } from 'react'

/**
 * Returns `value`, but only after it has stopped changing for `delay` ms.
 * Used by search: we call the server once the user pauses typing, not on every key press.
 */
export function useDebounced<T>(value: T, delay = 180): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer) // a new key press cancels the previous timer
  }, [value, delay])
  return debounced
}
