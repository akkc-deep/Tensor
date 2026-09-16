<script setup>
import { computed, ref } from 'vue'

import FieldError from '../components/common/FieldError.vue'
import PageHeading from '../components/common/PageHeading.vue'
import { Setting, Check } from '@element-plus/icons-vue'
import { useTheme } from '../composables/useTheme.js'
import { DISPLAY_SCALES, useDisplay } from '../composables/useDisplay.js'

const theme = useTheme()
const display = useDisplay()
const draft = ref(theme.requested.value.toUpperCase())
const error = ref('')

const corrected = computed(
  () => theme.requested.value !== theme.applied.value,
)
const storageLabel = computed(() =>
  theme.storageStatus.value === 'preview-only' ? '仅本次生效，无法保存到当前浏览器' : '已保存',
)

function apply(value) {
  if (!theme.apply(value)) {
    error.value = '请输入 6 位 HEX 颜色，例如 #3565B6'
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
    <PageHeading id="settings-title" title="外观设置" description="让工作台更合你的习惯。" />
    <section class="settings-content" aria-labelledby="appearance-title">
      <div class="settings-intro">
        <Setting aria-hidden="true" />
        <h2 id="appearance-title">外观与主题</h2>
        <p>调整工作台的主题色。</p>
      </div>
      <div class="accent-setting">
        <div><h3>主题色</h3><p>偏好自动保存在当前浏览器。</p></div>
        <div class="color-options" role="group" aria-label="主题色预设">
          <button
            v-for="color in ['#28745a', '#3565b6', '#745942', '#383d43']"
            :key="color"
            type="button"
            :style="{ background: color }"
            :aria-label="`使用主题色 ${color}`"
            :aria-pressed="theme.requested.value === color"
            @click="apply(color)"
          ><Check v-if="theme.requested.value === color" aria-hidden="true" /></button>
          <button class="text-button" type="button" @click="handleReset">恢复默认</button>
        </div>
      </div>
      <p id="theme-status" class="theme-status" role="status" aria-live="polite">
        实际应用色：{{ theme.applied.value.toUpperCase() }} · {{ storageLabel }}
        <span v-if="corrected">为保证可读性，实际应用色已自动调整亮度。</span>
      </p>
      <details class="custom-theme">
        <summary>自定义主题色</summary>
        <form class="settings-form" novalidate @submit.prevent="apply(draft)">
          <label for="theme-color">全局主题色</label>
          <div class="theme-controls">
            <input
              id="theme-color" type="color" :value="theme.requested.value"
              aria-describedby="theme-status" @input="handleColor"
            >
            <input
              id="theme-hex" v-model="draft" class="theme-hex" type="text" maxlength="7"
              aria-label="主题色 HEX" :aria-invalid="error ? 'true' : undefined"
              :aria-describedby="error ? 'theme-error theme-status' : 'theme-status'"
              autocomplete="off" spellcheck="false"
            >
            <button class="button button--primary" type="submit">应用</button>
          </div>
          <FieldError id="theme-error" :message="error" />
        </form>
      </details>
      <section v-if="display" class="display-setting" aria-labelledby="display-title">
        <div>
          <h3 id="display-title">显示与布局</h3>
          <p>自动适配 2K、4K 与当前窗口，也可选择适合你的显示比例。</p>
        </div>
        <div class="field">
          <label for="display-scale">显示比例</label>
          <select id="display-scale" :value="display.requested.value" aria-describedby="display-status" @change="display.apply($event.target.value)">
            <option value="auto">自动适配（推荐）</option>
            <option v-for="value in DISPLAY_SCALES" :key="value" :value="value">{{ value }}%</option>
          </select>
        </div>
        <p id="display-status" data-testid="display-status" role="status" aria-live="polite">
          当前显示 {{ display.scale.value * 100 }}%
          <span v-if="display.requested.value !== 'auto' && Number(display.requested.value) !== display.scale.value * 100"> · 已按窗口大小调整</span>
          · {{ display.storageStatus.value === 'preview-only' ? '仅本次生效，无法保存到当前浏览器' : '偏好已保存' }}
        </p>
      </section>
    </section>
  </section>
</template>
