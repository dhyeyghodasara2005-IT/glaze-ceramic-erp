import { useEffect, useRef } from 'react'
import { animate, useReducedMotion } from 'motion/react'

/** A number that counts up from 0 when it appears (e.g. dashboard totals), with up to `decimals` decimals. */
export default function CountUp({ value, duration = 1.1, decimals = 0 }: { value: number; duration?: number; decimals?: number }) {
  const ref = useRef<HTMLSpanElement>(null)
  const reduce = useReducedMotion()

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const format = (n: number) => n.toLocaleString('en-IN', { maximumFractionDigits: decimals, minimumFractionDigits: 0 })
    if (reduce) {
      el.textContent = format(value)
      return
    }
    // Animate the text directly (no React re-render per frame = smooth even on cheap phones).
    const controls = animate(0, value, {
      duration,
      ease: [0.22, 1, 0.36, 1],
      onUpdate: (n) => {
        el.textContent = format(n)
      },
    })
    return () => controls.stop()
  }, [value, duration, reduce, decimals])

  return <span ref={ref}>0</span>
}
