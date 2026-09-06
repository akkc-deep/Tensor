<script setup>
import { provide } from 'vue'

import { createThemeState, THEME_KEY } from './composables/useTheme.js'
import AppLayout from './layouts/AppLayout.vue'

provide(THEME_KEY, createThemeState())
</script>

<template>
  <AppLayout />
</template>
