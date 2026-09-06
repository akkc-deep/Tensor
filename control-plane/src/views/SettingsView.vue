<script setup>
import { computed, ref } from 'vue'

import FieldError from '../components/common/FieldError.vue'
import PageHeading from '../components/common/PageHeading.vue'
import { useTheme } from '../composables/useTheme.js'

const theme = useTheme()
const draft = ref(theme.requested.value.toUpperCase())
const error = ref('')

const corrected = computed(
  () => theme.requested.value !== theme.applied.value,
)
const storageLabel = computed(() =>
  theme.storageStatus.value === 'preview-only' ? '仅本次预览' : '已保存',
)

function apply(value) {
  if (!theme.apply(value)) {
    error.value = '请输入 6 位 HEX 颜色，例如 #2857B4'
    return
  }
  draft.value = theme.requested.value.toUpperCase()
  error.value = ''
}

function handleColor(event) {
  apply(event.target.value)
}

function handleReset() {
  theme.reset()
  draft.value = theme.requested.value.toUpperCase()
  error.value = ''
}
</script>

<template>
  <section class="page settings-page" aria-labelledby="settings-title">
    <PageHeading
      id="settings-title"
      title="设置"
      description="调整工作台外观，让每一次操作都更合心意。"
    />

    <section class="settings-panel" aria-labelledby="appearance-title">
      <header class="panel-heading">
        <h2 id="appearance-title">外观与主题</h2>
        <span>当前浏览器</span>
      </header>
      <form class="settings-form" novalidate @submit.prevent="apply(draft)">
        <p class="settings-form__help">
          选择喜欢的主题色，整个工作台会同步更新。偏好自动保存在当前浏览器。
        </p>
        <label for="theme-color">全局主题色</label>
        <div class="theme-controls">
          <input
            id="theme-color"
            type="color"
            :value="theme.requested.value"
            aria-describedby="theme-status"
            @input="handleColor"
          >
          <input
            id="theme-hex"
            v-model="draft"
            class="theme-hex"
            type="text"
            maxlength="7"
            aria-label="主题色 HEX"
            :aria-invalid="error ? 'true' : undefined"
            :aria-describedby="error ? 'theme-error theme-status' : 'theme-status'"
            autocomplete="off"
            spellcheck="false"
          >
          <button class="button button--primary" type="submit">应用</button>
        </div>
        <FieldError id="theme-error" :message="error" />
        <p id="theme-status" class="theme-status" role="status" aria-live="polite">
          实际应用色：{{ theme.applied.value.toUpperCase() }} · {{ storageLabel }}
          <span v-if="corrected">为保证可读性，实际应用色已自动调整亮度。</span>
        </p>
        <div class="theme-reset-row">
          <button class="button button--secondary" type="button" @click="handleReset">
            恢复冰川白
          </button>
          <p>恢复默认背景、文字与钴蓝主色。</p>
        </div>
      </form>
    </section>
  </section>
</template>
