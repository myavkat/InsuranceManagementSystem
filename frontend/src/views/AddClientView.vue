<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProfessions, type Profession } from '../services/professionService'
import { getCities, type City } from '../services/cityService'
import { createClient } from '../services/clientService'

defineOptions({
  name: 'AddClientView',
})

const router = useRouter()

const firstName = ref('')
const lastName = ref('')
const tcNo = ref('')
const phoneNumber = ref('')
const birthDate = ref('')
const professionId = ref<number | null>(null)
const cityId = ref<number | null>(null)

const professions = ref<Profession[]>([])
const cities = ref<City[]>([])
const isLoading = ref(false)

const firstNameError = ref('')
const lastNameError = ref('')
const tcNoError = ref('')
const phoneNumberError = ref('')
const birthDateError = ref('')
const professionIdError = ref('')
const cityIdError = ref('')

onMounted(async () => {
  const [professionsData, citiesData] = await Promise.all([getProfessions(), getCities()])
  professions.value = professionsData
  cities.value = citiesData
})

function validateForm(): boolean {
  let isValid = true

  firstNameError.value = ''
  lastNameError.value = ''
  tcNoError.value = ''
  phoneNumberError.value = ''
  birthDateError.value = ''
  professionIdError.value = ''
  cityIdError.value = ''

  if (!firstName.value.trim()) {
    firstNameError.value = 'First name is required'
    isValid = false
  }

  if (!lastName.value.trim()) {
    lastNameError.value = 'Last name is required'
    isValid = false
  }

  if (!tcNo.value.trim()) {
    tcNoError.value = 'TC number is required'
    isValid = false
  } else if (!/^\d{11}$/.test(tcNo.value)) {
    tcNoError.value = 'TC number must be exactly 11 digits'
    isValid = false
  }

  if (!phoneNumber.value.trim()) {
    phoneNumberError.value = 'Phone number is required'
    isValid = false
  } else if (!/^\+?[\d\s()-]{10,}$/.test(phoneNumber.value)) {
    phoneNumberError.value = 'Please enter a valid phone number'
    isValid = false
  }

  if (!birthDate.value) {
    birthDateError.value = 'Birth date is required'
    isValid = false
  } else {
    const birth = new Date(birthDate.value)
    const tomorrow = new Date()
    tomorrow.setHours(0, 0, 0, 0)
    tomorrow.setDate(tomorrow.getDate() + 1)

    if (birth >= tomorrow) {
      birthDateError.value = 'Birth date must be before tomorrow'
      isValid = false
    }
  }

  if (!professionId.value) {
    professionIdError.value = 'Please select a profession'
    isValid = false
  }

  if (!cityId.value) {
    cityIdError.value = 'Please select a city'
    isValid = false
  }

  return isValid
}

async function handleSubmit() {
  if (!validateForm()) {
    return
  }

  isLoading.value = true

  try {
    const response = await createClient({
      firstName: firstName.value,
      lastName: lastName.value,
      tcNo: tcNo.value,
      phoneNumber: phoneNumber.value,
      birthDate: birthDate.value,
      professionId: professionId.value!,
      cityId: cityId.value!,
    })

    if (response.success && response.clientId) {
      firstName.value = ''
      lastName.value = ''
      tcNo.value = ''
      phoneNumber.value = ''
      birthDate.value = ''
      professionId.value = null
      cityId.value = null

      router.push(`/clients/${response.clientId}`)
    }
  } catch (error) {
    console.error('Failed to create client:', error)
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold mb-6">Add Client</h1>

    <form @submit.prevent="handleSubmit" class="bg-white rounded-lg shadow p-6 max-w-2xl">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label for="firstName" class="block text-sm font-medium text-gray-700 mb-1"
            >First Name</label
          >
          <input
            id="firstName"
            v-model="firstName"
            type="text"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="firstNameError ? 'border-red-500' : 'border-gray-300'"
            placeholder="Enter first name"
          />
          <p v-if="firstNameError" class="mt-1 text-sm text-red-500">{{ firstNameError }}</p>
        </div>

        <div>
          <label for="lastName" class="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
          <input
            id="lastName"
            v-model="lastName"
            type="text"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="lastNameError ? 'border-red-500' : 'border-gray-300'"
            placeholder="Enter last name"
          />
          <p v-if="lastNameError" class="mt-1 text-sm text-red-500">{{ lastNameError }}</p>
        </div>

        <div>
          <label for="tcNo" class="block text-sm font-medium text-gray-700 mb-1"
            >TC Number</label
          >
          <input
            id="tcNo"
            v-model="tcNo"
            type="text"
            maxlength="11"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="tcNoError ? 'border-red-500' : 'border-gray-300'"
            placeholder="Enter 11-digit TC number"
          />
          <p v-if="tcNoError" class="mt-1 text-sm text-red-500">{{ tcNoError }}</p>
        </div>

        <div>
          <label for="phoneNumber" class="block text-sm font-medium text-gray-700 mb-1"
            >Phone Number</label
          >
          <input
            id="phoneNumber"
            v-model="phoneNumber"
            type="tel"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="phoneNumberError ? 'border-red-500' : 'border-gray-300'"
            placeholder="+90 555 123 4567"
          />
          <p v-if="phoneNumberError" class="mt-1 text-sm text-red-500">{{ phoneNumberError }}</p>
        </div>

        <div>
          <label for="birthDate" class="block text-sm font-medium text-gray-700 mb-1"
            >Birth Date</label
          >
          <input
            id="birthDate"
            v-model="birthDate"
            type="date"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="birthDateError ? 'border-red-500' : 'border-gray-300'"
          />
          <p v-if="birthDateError" class="mt-1 text-sm text-red-500">{{ birthDateError }}</p>
        </div>

        <div>
          <label for="profession" class="block text-sm font-medium text-gray-700 mb-1"
            >Profession</label
          >
          <select
            id="profession"
            v-model="professionId"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="professionIdError ? 'border-red-500' : 'border-gray-300'"
          >
            <option :value="null" disabled>Select profession</option>
            <option v-for="profession in professions" :key="profession.id" :value="profession.id">
              {{ profession.name }}
            </option>
          </select>
          <p v-if="professionIdError" class="mt-1 text-sm text-red-500">{{ professionIdError }}</p>
        </div>

        <div>
          <label for="city" class="block text-sm font-medium text-gray-700 mb-1"
            >Residence City</label
          >
          <select
            id="city"
            v-model="cityId"
            class="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="cityIdError ? 'border-red-500' : 'border-gray-300'"
          >
            <option :value="null" disabled>Select city</option>
            <option v-for="city in cities" :key="city.id" :value="city.id">
              {{ city.name }}
            </option>
          </select>
          <p v-if="cityIdError" class="mt-1 text-sm text-red-500">{{ cityIdError }}</p>
        </div>
      </div>

      <div class="mt-6">
        <button
          type="submit"
          :disabled="isLoading"
          class="w-full bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {{ isLoading ? 'Adding Client...' : 'Add Client' }}
        </button>
      </div>
    </form>
  </div>
</template>