<script setup lang="ts">
defineOptions({
  name: 'NotFoundView',
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-gray-100">
    <div class="text-center">
      <h1 class="text-4xl font-bold text-gray-800 mb-4">404</h1>
      <p class="text-xl text-gray-600 mb-6">Page Not Found</p>
      <router-link to="/dashboard" class="text-blue-500 hover:text-blue-600">
        Go to Dashboard
      </router-link>
    </div>
  </div>
</template>
