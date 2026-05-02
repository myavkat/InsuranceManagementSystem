<script setup lang="ts">
defineOptions({
  name: 'AppSidebar'
})
import { ref } from 'vue'

defineProps<{
  isOpen: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const menuItems = [
  {
    label: 'Dashboard',
    children: [
      { label: 'Overview', path: '/dashboard/overview' },
      { label: 'Analytics', path: '/dashboard/analytics' },
      { label: 'Reports', path: '/dashboard/reports' }
    ]
  },
  {
    label: 'Policies',
    children: [
      { label: 'All Policies', path: '/policies' },
      { label: 'Create New', path: '/policies/create' },
      { label: 'Renewals', path: '/policies/renewals' }
    ]
  },
  {
    label: 'Clients',
    children: [
      { label: 'Client List', path: '/clients' },
      { label: 'Add Client', path: '/clients/add' }
    ]
  },
  { label: 'Claims', path: '/claims' },
  { label: 'Users', path: '/users' },
  { label: 'Settings', path: '/settings' }
]

const openDropdowns = ref<Set<string>>(new Set())

const toggleDropdown = (label: string) => {
  if (openDropdowns.value.has(label)) {
    openDropdowns.value.delete(label)
  } else {
    openDropdowns.value.add(label)
  }
}
</script>

<template>
  <aside
    class="bg-gray-800 text-white flex flex-col transition-all duration-300"
    :class="isOpen ? 'w-64' : 'w-16'"
  >
    <div class="flex items-center justify-between p-4 border-b border-gray-700">
      <img
        v-if="isOpen"
        src="/banner_logo.png"
        alt="logo"
        class="h-10"
      />
      <button
        @click="emit('toggle')"
        class="p-1 hover:bg-gray-700 rounded transition-colors"
      >
        <svg
          v-if="isOpen"
          class="w-5 h-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
        </svg>
        <svg
          v-else
          class="w-5 h-5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 5l7 7-7 7M5 5l7 7-7 7" />
        </svg>
      </button>
    </div>

    <nav v-if="isOpen" class="flex-1 py-4 overflow-y-auto">
      <ul class="space-y-1">
        <li v-for="item in menuItems" :key="item.label">
          <template v-if="item.children">
            <button
              @click="toggleDropdown(item.label)"
              class="w-full flex items-center justify-between px-4 py-2 hover:bg-gray-700 transition-colors"
            >
              <span>{{ item.label }}</span>
              <svg
                class="w-4 h-4 transition-transform"
                :class="openDropdowns.has(item.label) ? 'rotate-180' : ''"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            <ul
              v-if="openDropdowns.has(item.label)"
              class="bg-gray-700"
            >
              <li v-for="child in item.children" :key="child.path">
                <a
                  :href="child.path"
                  class="block px-8 py-2 hover:bg-gray-600 transition-colors"
                >
                  {{ child.label }}
                </a>
              </li>
            </ul>
          </template>
          <template v-else>
            <a
              :href="item.path"
              class="block px-4 py-2 hover:bg-gray-700 transition-colors"
            >
              {{ item.label }}
            </a>
          </template>
        </li>
      </ul>
    </nav>
  </aside>
</template>