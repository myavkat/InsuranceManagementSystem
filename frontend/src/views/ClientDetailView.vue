<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { getCities } from '../services/cityService'
import { getProfessions } from '../services/professionService'
import type { City } from '../services/cityService'
import type { Profession } from '../services/professionService'

defineOptions({
  name: 'ClientDetailView',
})

const route = useRoute()
const clientId = route.params.id

const city = ref<City | null>(null)
const profession = ref<Profession | null>(null)

async function loadData() {
  const cities = await getCities()
  const professions = await getProfessions()

  const randomCityId = Math.floor(Math.random() * cities.length) + 1
  const randomProfessionId = Math.floor(Math.random() * professions.length) + 1

  city.value = cities.find((c) => c.id === randomCityId) || null
  profession.value = professions.find((p) => p.id === randomProfessionId) || null
}

loadData()
</script>

<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold mb-6">Client Details</h1>

    <div class="bg-white rounded-lg shadow p-6">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <p class="text-sm text-gray-500">Client ID</p>
          <p class="font-medium">{{ clientId }}</p>
        </div>

        <div>
          <p class="text-sm text-gray-500">First Name</p>
          <p class="font-medium">John</p>
        </div>

        <div>
          <p class="text-sm text-gray-500">Last Name</p>
          <p class="font-medium">Doe</p>
        </div>

        <div>
          <p class="text-sm text-gray-500">TC Number</p>
          <p class="font-medium">12345678901</p>
        </div>

        <div>
          <p class="text-sm text-gray-500">Phone Number</p>
          <p class="font-medium">+90 555 123 4567</p>
        </div>

        <div>
          <p class="text-sm text-gray-500">Birth Date</p>
          <p class="font-medium">1990-01-15</p>
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
    </div>
  </div>
</template>