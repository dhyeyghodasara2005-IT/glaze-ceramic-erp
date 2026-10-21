import type { ReactNode } from 'react'
import { motion, type Variants } from 'motion/react'

/**
 * Small animation building blocks (library: "motion", formerly Framer Motion).
 * Keep animations short (0.2-0.5s) and subtle: they should guide the eye, not slow people down.
 * MotionConfig reducedMotion="user" in main.tsx turns them off for people who prefer less motion.
 */

export const EASE = [0.22, 1, 0.36, 1] as const

/** Fades + slides content up a little when it appears. */
export function FadeIn({ children, delay = 0, y = 12, className }: {
  children: ReactNode
  delay?: number
  y?: number
  className?: string
}) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: EASE, delay }}
    >
      {children}
    </motion.div>
  )
}

const container: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06, delayChildren: 0.05 } },
}

const item: Variants = {
  hidden: { opacity: 0, y: 14, scale: 0.98 },
  show: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.45, ease: EASE } },
}

/** Children wrapped in <StaggerItem> appear one after another. */
export function Stagger({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={container} initial="hidden" animate="show">
      {children}
    </motion.div>
  )
}

export function StaggerItem({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={item}>
      {children}
    </motion.div>
  )
}
