<script setup>
import { onBeforeUnmount, provide } from 'vue'

import { createThemeState, THEME_KEY } from './composables/useTheme.js'
import AppLayout from './layouts/AppLayout.vue'
import { createDisplayState, DISPLAY_KEY } from './composables/useDisplay.js'

provide(THEME_KEY, createThemeState())
const display = createDisplayState()
provide(DISPLAY_KEY, display)
onBeforeUnmount(display.dispose)
</script>

<template>
  <AppLayout />
</template>
