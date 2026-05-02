import { ref } from 'vue'
import { validateToken } from '../services/authService'

const TOKEN_KEY = 'jwt_token'

const isAuthenticated = ref(false)
const isValidated = ref(false)

async function checkAuth(): Promise<void> {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    const valid = await validateToken(token)
    isAuthenticated.value = valid
  } else {
    isAuthenticated.value = false
  }
  isValidated.value = true
}

checkAuth()

export function useAuth() {
  function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY)
  }

  function setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token)
    isAuthenticated.value = true
  }

  function clearToken(): void {
    localStorage.removeItem(TOKEN_KEY)
    isAuthenticated.value = false
  }

  async function isLoggedIn(): Promise<boolean> {
    if (!isValidated.value) {
      await checkAuth()
    }
    return isAuthenticated.value
  }

  return {
    getToken,
    setToken,
    clearToken,
    isLoggedIn,
    isAuthenticated
  }
}