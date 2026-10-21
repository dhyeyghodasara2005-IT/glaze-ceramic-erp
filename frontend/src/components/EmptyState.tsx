import type { ReactNode } from 'react'

/** Friendly "nothing here yet" block with a little tile illustration and an optional button. */
export default function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <div className="empty-art" aria-hidden>
        {Array.from({ length: 9 }, (_, i) => (
          <span key={i} />
        ))}
      </div>
      <div className="empty-title">{title}</div>
      {hint && <div className="empty-hint">{hint}</div>}
      {action}
    </div>
  )
}
