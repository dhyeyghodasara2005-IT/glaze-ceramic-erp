import { isAxiosError } from 'axios'
import type { FormInstance } from 'antd'
import type { ApiError } from './types'

/** Pulls our backend's ApiError JSON out of a failed request, if there is one. */
export function getApiError(err: unknown): ApiError | undefined {
  if (isAxiosError(err) && err.response?.data && typeof err.response.data === 'object') {
    return err.response.data as ApiError
  }
  return undefined
}

export function errorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err) && !err.response) return 'Cannot reach the server. Is the backend running?'
  return getApiError(err)?.message ?? fallback
}

/**
 * Shows backend validation errors (400 fieldErrors) under the matching form inputs.
 * Works because form field names match the Java request record field names.
 * Returns true if any field error was shown.
 */
export function applyFieldErrors(form: FormInstance, err: unknown): boolean {
  const fieldErrors = getApiError(err)?.fieldErrors
  if (!fieldErrors) return false
  form.setFields(Object.entries(fieldErrors).map(([name, message]) => ({ name, errors: [message] })))
  return true
}
