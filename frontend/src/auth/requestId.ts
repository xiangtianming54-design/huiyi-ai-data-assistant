const REQUEST_ID_KEY = 'huiyi_last_request_id'
const RUN_ID_KEY = 'huiyi_last_run_id'

export function saveObservabilityIds(headers: Headers | Record<string, string> | undefined): void {
  if (!headers) {
    return
  }
  const requestId = header(headers, 'x-request-id')
  const runId = header(headers, 'x-run-id')
  if (requestId) {
    localStorage.setItem(REQUEST_ID_KEY, requestId)
  }
  if (runId) {
    localStorage.setItem(RUN_ID_KEY, runId)
  }
}

export function lastRequestId(): string | null {
  return localStorage.getItem(REQUEST_ID_KEY)
}

export function lastRunId(): string | null {
  return localStorage.getItem(RUN_ID_KEY)
}

function header(headers: Headers | Record<string, string>, name: string): string | undefined {
  if (headers instanceof Headers) {
    return headers.get(name) ?? undefined
  }
  const found = Object.entries(headers).find(([key]) => key.toLowerCase() === name)
  return found?.[1]
}
