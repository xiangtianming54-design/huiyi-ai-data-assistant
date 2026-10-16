import { defineStore } from 'pinia'
import { authApi, type WorkspaceItem } from '../api'
import { clearSession, getRefreshToken, getWorkspaceId, setAccessToken, setRefreshToken, setWorkspaceId } from '../auth/token'
import { useChatStore } from './chat'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    username: '' as string,
    displayName: '' as string,
    workspaces: [] as WorkspaceItem[],
    workspaceId: getWorkspaceId()
  }),
  actions: {
    applyTokens(accessToken: string, refreshToken: string, defaultWorkspaceId?: number) {
      setAccessToken(accessToken)
      setRefreshToken(refreshToken)
      if (defaultWorkspaceId && !getWorkspaceId()) {
        setWorkspaceId(defaultWorkspaceId)
        this.workspaceId = String(defaultWorkspaceId)
      }
    },
    async login(username: string, password: string) {
      const response = await authApi.login(username, password)
      const data = response.data.data
      this.applyTokens(data.accessToken, data.refreshToken, data.defaultWorkspaceId)
      await this.loadProfile()
    },
    async register(username: string, password: string, displayName: string) {
      const response = await authApi.register(username, password, displayName)
      const data = response.data.data
      this.applyTokens(data.accessToken, data.refreshToken, data.defaultWorkspaceId)
      await this.loadProfile()
    },
    async loadProfile() {
      const me = await authApi.me()
      this.username = me.data.data.username
      this.displayName = me.data.data.displayName
      const listed = await authApi.workspaces()
      this.workspaces = listed.data.data.map((item) => ({
        ...item,
        workspaceId: item.workspaceId ?? item.id
      }))
      if (!this.workspaceId && this.workspaces[0]?.workspaceId) {
        this.switchWorkspace(this.workspaces[0].workspaceId as number)
      }
    },
    switchWorkspace(id: number) {
      setWorkspaceId(id)
      this.workspaceId = String(id)
      useChatStore().resetConversation()
    },
    async logout() {
      try {
        await authApi.logout(getRefreshToken())
      } catch {
        // ignore
      }
      clearSession()
      this.username = ''
      this.displayName = ''
      this.workspaces = []
      this.workspaceId = ''
      useChatStore().resetConversation()
    }
  }
})
