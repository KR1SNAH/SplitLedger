import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from './api'

/**
 * Maps backend validation errors onto form fields. Returns a message for the form-level alert
 * when the error isn't tied to a specific field.
 */
export function applyServerErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>): string | null {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.'

  const fields = Object.entries(error.fieldErrors)
  fields.forEach(([field, message]) => setError(field as Path<T>, { type: 'server', message }))
  return fields.length > 0 ? null : error.message
}
