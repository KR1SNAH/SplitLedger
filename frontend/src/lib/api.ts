import type { AuthResponse, ProblemDetail } from './types'

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed (${problem.status})`)
    this.status = problem.status
    this.fieldErrors = problem.errors ?? {}
  }

  static async from(res: Response): Promise<ApiError> {
    try {
      const body = (await res.json()) as ProblemDetail
      return new ApiError({ ...body, status: res.status })
    } catch {
      return new ApiError({ status: res.status })
    }
  }
}

// The access token lives only in memory; the refresh token is an HttpOnly cookie the browser manages.
let accessToken: string | null = null
let refreshInFlight: Promise<AuthResponse | null> | null = null
let onSessionEnded: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

/** Called when a refresh fails mid-session so the UI can return to the login screen. */
export function setSessionEndedHandler(handler: (() => void) | null) {
  onSessionEnded = handler
}

/**
 * Exchanges the refresh cookie for a new access token. Concurrent callers share one request:
 * the backend rotates refresh tokens and treats a replayed token as theft, so two parallel
 * refreshes would log the user out.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  refreshInFlight ??= fetch('/api/auth/refresh', { method: 'POST' })
    .then(async (res) => (res.ok ? ((await res.json()) as AuthResponse) : null))
    .catch(() => null)
    .then((session) => {
      setAccessToken(session?.accessToken ?? null)
      return session
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  json?: unknown
}

export async function api<T>(path: string, { json, headers, ...init }: RequestOptions = {}): Promise<T> {
  const send = () =>
    fetch(path, {
      ...init,
      headers: {
        ...(json !== undefined && { 'Content-Type': 'application/json' }),
        ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
        ...headers,
      },
      body: json !== undefined ? JSON.stringify(json) : undefined,
    })

  let res = await send()

  // Access token expired: refresh once and retry. Auth endpoints report their own 401s.
  if (res.status === 401 && accessToken && !path.startsWith('/api/auth/')) {
    const session = await refreshSession()
    if (!session) {
      onSessionEnded?.()
      throw await ApiError.from(res)
    }
    res = await send()
  }

  if (!res.ok) throw await ApiError.from(res)
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
