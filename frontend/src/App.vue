<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'

const isSidebarOpen = ref(true)
const route = useRoute()

const showLayout = computed(() => !['/login'].includes(route.path))
</script>

<template>
  <div v-if="showLayout" class="flex min-h-screen">
    <AppSidebar :is-open="isSidebarOpen" @toggle="isSidebarOpen = !isSidebarOpen" />

    <main class="flex-1">
      <router-view />
    </main>
  </div>
  <router-view v-else />
</template>

<style scoped></style>
