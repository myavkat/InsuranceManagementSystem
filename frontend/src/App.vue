<script setup lang="ts">
import { ref } from 'vue'
import AppSidebar from './components/AppSidebar.vue'

const isSidebarOpen = ref(true)
</script>

<template>
  <div class="flex min-h-screen">
    <AppSidebar :is-open="isSidebarOpen" @toggle="isSidebarOpen = !isSidebarOpen" />

    <main class="flex-1 p-8">
      <h1 class="text-3xl font-bold underline">Tailwind Works!!</h1>
    </main>
  </div>
</template>

<style scoped></style>
