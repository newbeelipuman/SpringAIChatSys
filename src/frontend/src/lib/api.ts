export type HttpMethod = 'GET' | 'POST' | 'DELETE'

const DEFAULT_BASE_URL = '/api'
const USER_STORAGE_KEY = 'demo.userId'
const AUTH_TOKEN_STORAGE_KEY = 'auth.token'
const DEFAULT_USER_ID = 'demo-user'

function resolveBaseUrl(): string {
  const fromEnv = (import.meta.env.VITE_API_BASE as string | undefined) ?? ''
  return fromEnv.trim() ? fromEnv.trim().replace(/\/$/, '') : DEFAULT_BASE_URL
}

export async function apiRequest<TResponse>(method: HttpMethod, path: string, body?: unknown): Promise<TResponse> {
  const baseUrl = resolveBaseUrl()
  const url = `${baseUrl}${path.startsWith('/') ? '' : '/'}${path}`

  const response = await fetch(url, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {}),
      'X-Demo-User': getDemoUserId(),
    },
    body: body ? JSON.stringify(body) : undefined,
  })

  if (response.ok) {
    const json = (await response.json()) as TResponse
    if (method === 'POST' || method === 'DELETE') {
      window.dispatchEvent(new CustomEvent('demo-audit-updated'))
    }
    return json
  }

  const contentType = response.headers.get('content-type') ?? ''
  const fallbackText = await response.text().catch(() => '')
  if (contentType.includes('application/json')) {
    let message = ''
    try {
      const json = JSON.parse(fallbackText) as { message?: string; error?: string }
      message = json.message ?? json.error ?? ''
    } catch {
      // ignore
    }
    if (message) {
      throw new Error(message)
    }
  }

  throw new Error(fallbackText || `HTTP ${response.status}`)
}

export function apiGet<TResponse>(path: string): Promise<TResponse> {
  return apiRequest<TResponse>('GET', path)
}

export function apiPost<TResponse>(path: string, body: unknown): Promise<TResponse> {
  return apiRequest<TResponse>('POST', path, body)
}

export function apiDelete<TResponse>(path: string): Promise<TResponse> {
  return apiRequest<TResponse>('DELETE', path)
}

export function getDemoUserId(): string {
  const value = localStorage.getItem(USER_STORAGE_KEY)?.trim()
  return value || DEFAULT_USER_ID
}

export function setDemoUserId(userId: string): string {
  const normalized = userId.trim() || DEFAULT_USER_ID
  localStorage.setItem(USER_STORAGE_KEY, normalized)
  return normalized
}

export function getAuthToken(): string {
  return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)?.trim() ?? ''
}

export function setAuthToken(token: string): void {
  if (token.trim()) {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token.trim())
  } else {
    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
  }
  window.dispatchEvent(new CustomEvent('auth-updated'))
  window.dispatchEvent(new CustomEvent('demo-audit-updated'))
}
