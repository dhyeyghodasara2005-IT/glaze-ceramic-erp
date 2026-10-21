// The product's name and colours in ONE place. Rename the app by changing `name` here.

export const BRAND = {
  name: 'Glaze',
  product: 'Ceramic ERP',
} as const

/**
 * Brand colour scale (terracotta / fired clay). 600 is the main button colour.
 * The same values are mirrored as CSS variables in styles.css (--brand-*).
 */
export const COLORS = {
  brand50: '#FBF3EE',
  brand100: '#F6E3D7',
  brand200: '#EDC6AE',
  brand400: '#D9773F',
  brand500: '#C2622D',
  brand600: '#A84A23',
  brand700: '#8A3B1C',
  brand800: '#6E2A12',
  ink: '#1F1714', // text and dark sidebar
  inkSoft: '#6B5E57', // secondary text
  canvas: '#F6F2EE', // page background
  line: '#EEE6DF', // borders
} as const
