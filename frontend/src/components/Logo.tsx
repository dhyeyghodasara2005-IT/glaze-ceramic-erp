import { useId } from 'react'
import { BRAND } from '../brand'

/**
 * The Glaze mark: a fired-clay tile made of four smaller tiles, with a glossy
 * glaze sheen across it. Pure SVG, so it is sharp at any size and needs no image file.
 */
export function LogoMark({ size = 32 }: { size?: number }) {
  // useId keeps gradient ids unique when the logo appears twice on one page.
  const id = useId().replace(/:/g, '')
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" role="img" aria-label={BRAND.name}>
      <defs>
        <linearGradient id={`${id}-body`} x1="6" y1="4" x2="42" y2="44" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#E08A50" />
          <stop offset="0.55" stopColor="#A84A23" />
          <stop offset="1" stopColor="#6E2A12" />
        </linearGradient>
        <linearGradient id={`${id}-sheen`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#fff" stopOpacity="0" />
          <stop offset="0.5" stopColor="#fff" stopOpacity="0.45" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
        <clipPath id={`${id}-clip`}>
          <rect x="4" y="4" width="40" height="40" rx="11" />
        </clipPath>
      </defs>
      <rect x="4" y="4" width="40" height="40" rx="11" fill={`url(#${id}-body)`} />
      <rect x="12.5" y="12.5" width="10.5" height="10.5" rx="3" fill="#FFF4EC" />
      <rect x="25" y="12.5" width="10.5" height="10.5" rx="3" fill="#FFF4EC" fillOpacity="0.55" />
      <rect x="12.5" y="25" width="10.5" height="10.5" rx="3" fill="#FFF4EC" fillOpacity="0.55" />
      <rect x="25" y="25" width="10.5" height="10.5" rx="3" fill="#FFF4EC" fillOpacity="0.3" />
      <g clipPath={`url(#${id}-clip)`}>
        <path d="M-2 26 L26 -2 L34 -2 L6 26 Z" fill={`url(#${id}-sheen)`} />
      </g>
    </svg>
  )
}

/** Mark + "Glaze" wordmark. `tone="light"` is for dark backgrounds (sidebar, login panel). */
export default function Logo({ size = 32, tone = 'dark', showProduct = true }: {
  size?: number
  tone?: 'dark' | 'light'
  showProduct?: boolean
}) {
  return (
    <span className={`logo logo-${tone}`}>
      <LogoMark size={size} />
      <span className="logo-text">
        <span className="logo-name">{BRAND.name}</span>
        {showProduct && <span className="logo-product">{BRAND.product}</span>}
      </span>
    </span>
  )
}
