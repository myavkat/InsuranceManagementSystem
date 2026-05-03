<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getClients, type Client } from '../services/clientService'

defineOptions({
  name: 'ClientsView'
})

const router = useRouter()

const clients = ref<Client[]>([])
const isLoading = ref(false)
const currentPage = ref(1)
const limit = ref(10)
const total = ref(0)
const totalPages = ref(0)

async function fetchClients() {
  isLoading.value = true
  try {
    const response = await getClients(currentPage.value, limit.value)
    clients.value = response.clients
    total.value = response.total
    totalPages.value = response.totalPages
  } catch (error) {
    console.error('Failed to fetch clients:', error)
  } finally {
    isLoading.value = false
  }
}

function goToPage(page: number) {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    fetchClients()
  }
}

function viewClient(client: Client) {
  router.push(`/clients/${client.id}`)
}

onMounted(() => {
  fetchClients()
})
</script>

<template>
  <div class="p-6">
    <h1 class="text-2xl font-bold mb-6">Clients</h1>

    <div class="bg-white rounded-lg shadow overflow-hidden">
      <div v-if="isLoading" class="p-8 text-center text-gray-500">
        Loading...
      </div>
      <table v-else-if="clients.length > 0" class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              First Name
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Last Name
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              TC Number
            </th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
              Phone Number
            </th>
            <th class="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
              Actions
            </th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="client in clients" :key="client.id" class="hover:bg-gray-50">
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ client.firstName }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ client.lastName }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ client.tcNo }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ client.phoneNumber }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-right text-sm">
              <button
                @click="viewClient(client)"
                class="text-blue-600 hover:text-blue-900 font-medium"
              >
                View
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-else-if="clients.length === 0" class="p-8 text-center text-gray-500">
        No clients found.
      </div>

      <div v-if="totalPages > 1" class="bg-gray-50 px-6 py-3 flex items-center justify-between border-t border-gray-200">
        <div class="text-sm text-gray-700">
          Showing {{ (currentPage - 1) * limit + 1 }} to {{ Math.min(currentPage * limit, total) }} of {{ total }} results
        </div>
        <div class="flex gap-2">
          <button
            @click="goToPage(currentPage - 1)"
            :disabled="currentPage === 1"
            class="px-3 py-1 text-sm border rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Previous
          </button>
          <button
            @click="goToPage(currentPage + 1)"
            :disabled="currentPage === totalPages"
            class="px-3 py-1 text-sm border rounded hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Next
          </button>
        </div>
      </div>
    </div>
  </div>
</template>