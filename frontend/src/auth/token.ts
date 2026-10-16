let accessToken = ''

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token: string) {
  accessToken = token
}

export function getRefreshToken() {
  return localStorage.getItem('huiyi_refresh') || ''
}

export function setRefreshToken(token: string) {
  if (token) {
    localStorage.setItem('huiyi_refresh', token)
  } else {
    localStorage.removeItem('huiyi_refresh')
  }
}

export function getWorkspaceId() {
  return localStorage.getItem('huiyi_workspace_id') || ''
}

export function setWorkspaceId(id: string | number | null | undefined) {
  if (id === null || id === undefined || id === '') {
    localStorage.removeItem('huiyi_workspace_id')
    return
  }
  localStorage.setItem('huiyi_workspace_id', String(id))
}

export function clearSession() {
  accessToken = ''
  localStorage.removeItem('huiyi_refresh')
  localStorage.removeItem('huiyi_workspace_id')
}
