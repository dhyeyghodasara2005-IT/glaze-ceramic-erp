import { useEffect, useState } from 'react'

/** Re-renders the component every `ms` so "milling for 9 h 05 m" keeps counting. */
export function useNow(ms = 60_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), ms)
    return () => clearInterval(timer)
  }, [ms])
  return now
}
