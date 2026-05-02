<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../services/authService'
import { useAuth } from '../composables/useAuth'

defineOptions({
  name: 'LoginView',
})

const router = useRouter()
const { setToken } = useAuth()

const email = ref('')
const password = ref('')
const emailError = ref('')
const passwordError = ref('')
const isLoading = ref(false)

const isEmailValid = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email.value)
})

const isPasswordValid = computed(() => {
  return password.value.length >= 6
})

function validateForm(): boolean {
  emailError.value = ''
  passwordError.value = ''

  if (!email.value) {
    emailError.value = 'Email is required'
    return false
  }

  if (!isEmailValid.value) {
    emailError.value = 'Please enter a valid email address'
    return false
  }

  if (!password.value) {
    passwordError.value = 'Password is required'
    return false
  }

  if (!isPasswordValid.value) {
    passwordError.value = 'Password must be at least 6 characters'
    return false
  }

  return true
}

async function handleLogin() {
  if (!validateForm()) {
    return
  }

  isLoading.value = true

  try {
    const response = await login(email.value, password.value)
    setToken(response.token)
    router.push('/dashboard')
  } catch (error) {
    console.error('Login failed:', error)
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-100">
    <div class="bg-white p-8 rounded-lg shadow-md w-full max-w-md">
      <div class="text-center mb-6">
        <img src="/banner_logo.png" alt="logo" class="h-16 mx-auto mb-4" />
        <h1 class="text-2xl font-bold text-gray-800">Sign In</h1>
      </div>

      <form @submit.prevent="handleLogin" class="space-y-4">
        <div>
          <label for="email" class="block text-sm font-medium text-gray-700 mb-1">Email</label>
          <input
            id="email"
            v-model="email"
            type="email"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="emailError ? 'border-red-500' : 'border-gray-300'"
            placeholder="Enter your email"
          />
          <p v-if="emailError" class="mt-1 text-sm text-red-500">{{ emailError }}</p>
        </div>

        <div>
          <label for="password" class="block text-sm font-medium text-gray-700 mb-1"
            >Password</label
          >
          <input
            id="password"
            v-model="password"
            type="password"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="passwordError ? 'border-red-500' : 'border-gray-300'"
            placeholder="Enter your password"
          />
          <p v-if="passwordError" class="mt-1 text-sm text-red-500">{{ passwordError }}</p>
        </div>

        <button
          type="submit"
          :disabled="isLoading"
          class="w-full bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ isLoading ? 'Signing in...' : 'Sign In' }}
        </button>
      </form>
    </div>
  </div>
</template>
