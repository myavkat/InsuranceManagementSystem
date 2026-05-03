<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCities, type City } from '../services/cityService'
import { getProfessions, type Profession } from '../services/professionService'
import {
  updateClient,
  getClientDetail,
  type UpdateClientResponse,
} from '../services/clientService'

defineOptions({
  name: 'ClientDetailView',
})

const route = useRoute()
const router = useRouter()
const clientId = route.params.id

const city = ref<City | null>(null)
const profession = ref<Profession | null>(null)

const isEditing = ref(false)
const isLoading = ref(false)
const apiError = ref('')
const clientNotFound = ref(false)

const firstName = ref('')
const lastName = ref('')
const tcNo = ref('')
const phoneNumber = ref('')
const birthDate = ref('')
const professionId = ref<number | null>(null)
const cityId = ref<number | null>(null)

const professions = ref<Profession[]>([])
const cities = ref<City[]>([])

async function loadData() {
  const [citiesData, professionsData, clientData] = await Promise.all([
    getCities(),
    getProfessions(),
    getClientDetail(Number(clientId)),
  ])

  cities.value = citiesData
  professions.value = professionsData

  if (clientData) {
    firstName.value = clientData.firstName
    lastName.value = clientData.lastName
    tcNo.value = clientData.tcNo
    phoneNumber.value = clientData.phoneNumber
    birthDate.value = clientData.birthDate
    professionId.value = clientData.professionId
    cityId.value = clientData.cityId

    city.value = citiesData.find((c) => c.id === clientData.cityId) || null
    profession.value = professionsData.find((p) => p.id === clientData.professionId) || null
  } else {
    clientNotFound.value = true
  }
}

onMounted(loadData)

function startEdit() {
  isEditing.value = true
  apiError.value = ''
}

function cancelEdit() {
  isEditing.value = false
  apiError.value = ''
}

async function handleSave() {
  isLoading.value = true
  apiError.value = ''

  try {
    const response: UpdateClientResponse = await updateClient(Number(clientId), {
      firstName: firstName.value,
      lastName: lastName.value,
      tcNo: tcNo.value,
      phoneNumber: phoneNumber.value,
      birthDate: birthDate.value,
      professionId: professionId.value!,
      cityId: cityId.value!,
    })

    if (response.success) {
      router.push(`/clients/${clientId}`)
    } else {
      apiError.value = response.error || 'Failed to update client'
    }
  } catch {
    apiError.value = 'An error occurred while updating the client'
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="p-6">
    <div v-if="clientNotFound" class="bg-white rounded-lg shadow p-6 text-center">
      <p class="text-xl text-gray-600 mb-4">Client does not exist</p>
      <router-link
        to="/clients"
        class="text-blue-500 hover:text-blue-600 underline"
      >
        Back to clients
      </router-link>
    </div>

    <template v-else>
      <div class="flex justify-between items-center mb-6">
        <div class="flex items-center">
          <router-link
            to="/clients"
            class="text-blue-500 hover:text-blue-600 flex items-center gap-1"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
            Back To Clients
          </router-link>
        </div>
        <button
          v-if="!isEditing"
          @click="startEdit"
          class="bg-blue-500 text-white px-4 py-2 rounded-lg hover:bg-blue-600 transition-colors"
        >
          Edit
        </button>
      </div>

      <div v-if="apiError" class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
        {{ apiError }}
      </div>

      <div class="bg-white rounded-lg shadow p-6">
        <div v-if="!isEditing" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <p class="text-sm text-gray-500">First Name</p>
            <p class="font-medium">{{ firstName }}</p>
          </div>

          <div>
            <p class="text-sm text-gray-500">Last Name</p>
            <p class="font-medium">{{ lastName }}</p>
          </div>

          <div>
            <p class="text-sm text-gray-500">TC Number</p>
            <p class="font-medium">{{ tcNo }}</p>
          </div>

          <div>
            <p class="text-sm text-gray-500">Phone Number</p>
            <p class="font-medium">{{ phoneNumber }}</p>
          </div>

          <div>
            <p class="text-sm text-gray-500">Birth Date</p>
            <p class="font-medium">{{ birthDate }}</p>
          </div>

          <div>
            <p class="text-sm text-gray-500">Profession</p>
            <p class="font-medium">{{ profession?.name || 'Loading...' }}</p>
          </div>

          <div>
            <p class="text-sm text-gray-500">Residence City</p>
            <p class="font-medium">{{ city?.name || 'Loading...' }}</p>
          </div>
        </div>

      <form v-else @submit.prevent="handleSave" class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label for="firstName" class="block text-sm font-medium text-gray-700 mb-1"
            >First Name</label
          >
          <input
            id="firstName"
            v-model="firstName"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label for="lastName" class="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
          <input
            id="lastName"
            v-model="lastName"
            type="text"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label for="tcNo" class="block text-sm font-medium text-gray-700 mb-1">TC Number</label>
          <input
            id="tcNo"
            v-model="tcNo"
            type="text"
            maxlength="11"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label for="phoneNumber" class="block text-sm font-medium text-gray-700 mb-1"
            >Phone Number</label
          >
          <input
            id="phoneNumber"
            v-model="phoneNumber"
            type="tel"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label for="birthDate" class="block text-sm font-medium text-gray-700 mb-1"
            >Birth Date</label
          >
          <input
            id="birthDate"
            v-model="birthDate"
            type="date"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label for="profession" class="block text-sm font-medium text-gray-700 mb-1"
            >Profession</label
          >
          <select
            id="profession"
            v-model="professionId"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option :value="null" disabled>Select profession</option>
            <option v-for="p in professions" :key="p.id" :value="p.id">
              {{ p.name }}
            </option>
          </select>
        </div>

        <div>
          <label for="city" class="block text-sm font-medium text-gray-700 mb-1"
            >Residence City</label
          >
          <select
            id="city"
            v-model="cityId"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option :value="null" disabled>Select city</option>
            <option v-for="c in cities" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select>
        </div>

        <div class="md:col-span-2 flex gap-4 mt-4">
          <button
            type="submit"
            :disabled="isLoading"
            class="flex-1 bg-blue-500 text-white py-2 rounded-lg hover:bg-blue-600 transition-colors disabled:opacity-50"
          >
            {{ isLoading ? 'Saving...' : 'Save' }}
          </button>
          <button
            type="button"
            @click="cancelEdit"
            class="flex-1 bg-gray-300 text-gray-700 py-2 rounded-lg hover:bg-gray-400 transition-colors"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
    </template>
  </div>
</template>