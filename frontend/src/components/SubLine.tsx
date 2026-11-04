import { Fragment } from 'react'

/**
 * The small grey line under a number in a list, e.g. "1,458 tiles in · −10 · 911 kWh".
 * A narrow column may break it between the parts but never inside one, so "911" never
 * leaves "kWh" and no line starts with a dot. Empty parts (false, null) are left out.
 */
export default function SubLine({ parts }: { parts: (string | false | null | undefined)[] }) {
  const shown = parts.filter((p): p is string => !!p)
  return (
    <div className="cell-sub">
      {shown.map((p, i) => (
        <Fragment key={i}>
          {i > 0 && ' '}
          <span className="keep">{i < shown.length - 1 ? `${p} ·` : p}</span>
        </Fragment>
      ))}
    </div>
  )
}
