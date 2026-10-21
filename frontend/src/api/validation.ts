// Same patterns as ValidationPatterns.java / UserRules.java, so the user sees mistakes
// before submitting. The backend still checks everything - never trust the browser alone.

export const patterns = {
  code: /^[A-Za-z0-9_-]{2,20}$/,
  gstin: /^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$/,
  pan: /^[A-Za-z]{5}[0-9]{4}[A-Za-z]$/,
  pincode: /^[1-9][0-9]{5}$/,
  phone: /^[0-9+\- ]{7,20}$/,
  username: /^[A-Za-z0-9._-]{3,50}$/,
}

export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 72

/** Antd Form "normalize" helper: store codes upper-case as the user types. */
export const toUpper = (value?: string) => value?.toUpperCase()

/** Turns "" into undefined so optional fields are sent as missing, not as empty strings. */
export function blankToUndefined<T extends object>(values: T): T {
  const out = { ...values } as Record<string, unknown>
  for (const key of Object.keys(out)) {
    if (typeof out[key] === 'string' && (out[key] as string).trim() === '') out[key] = undefined
  }
  return out as T
}
