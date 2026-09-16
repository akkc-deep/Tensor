<script setup>
import { computed, inject, ref, watch } from 'vue'
import { Download, Check } from '@element-plus/icons-vue'
import { demoKey } from './demoState.js'
import { planDemoBatches } from './demoBatch.js'

const demo = inject(demoKey)
const form = ref(null)
const error = ref('')
const rangeError = computed(() => {
  if (demo.mode !== 'RANGE') return ''
  if (demo.selectedId === 'trade_cal' && demo.params.exchange === 'BSE') return '北交所日历暂不支持批量下载，请选择 SSE 或 SZSE。'
  try { planDemoBatches(demo.range.start, demo.range.end); return '' }
  catch (cause) { return cause.message }
})
watch(() => [demo.selectedId, demo.mode, demo.range.start, demo.range.end], () => { error.value = '' })

function validate() {
  error.value = ''
  if (rangeError.value) return false
  if (!form.value.reportValidity()) return false
  for (const p of demo.parameters) {
    if (p.relatedParameter && p.name.includes('start') && demo.params[p.name] > demo.params[p.relatedParameter]) {
      error.value = '开始日期不能晚于结束日期，请重新选择。'
      return false
    }
  }
  return true
}
function submit() {
  if (!validate()) return
  try { demo.submit() } catch (cause) { error.value = cause.message }
}
</script>

<template>
  <form ref="form" class="download-form" @submit.prevent="submit">
    <div class="interface-note"><span class="code">{{ demo.selected.id }}</span><span>{{ demo.selected.category }}</span><span>写入后可查询</span></div>

    <div class="form-parameters">
      <div class="mode-heading"><h3>下载方式</h3><span>{{ demo.mode === 'RANGE' ? '按日期范围' : '单次请求' }}</span></div>
      <div class="mode-control" aria-label="下载方式">
        <button type="button" data-mode="SINGLE" :class="{ selected: demo.mode === 'SINGLE' }" :aria-pressed="demo.mode === 'SINGLE'" @click="demo.mode = 'SINGLE'"><Check v-if="demo.mode === 'SINGLE'" />单次下载</button>
        <button type="button" data-mode="RANGE" :class="{ selected: demo.mode === 'RANGE' }" :aria-pressed="demo.mode === 'RANGE'" :disabled="demo.rangeCapability.availability !== 'AVAILABLE'" @click="demo.mode = 'RANGE'"><Check v-if="demo.mode === 'RANGE'" />批量下载<small v-if="demo.rangeCapability.availability !== 'AVAILABLE'">{{ demo.rangeCapability.availability === 'NEEDS_VERIFICATION' ? '待验证' : '不支持' }}</small></button>
      </div>
      <p v-if="demo.rangeCapability.availability !== 'AVAILABLE'" class="mode-note">{{ demo.rangeCapability.availability === 'NEEDS_VERIFICATION' ? '区间能力待验证，当前仅开放单次下载。' : '这个接口仅支持单次下载。' }}</p>
      <div class="parameter-grid">
        <label v-for="parameter in demo.parameters" :key="parameter.name" class="field">
          <span>{{ parameter.label }} <small>{{ parameter.required ? '必填' : '选填' }}</small></span>
          <select v-if="parameter.type === 'ENUM'" v-model="demo.params[parameter.name]" :required="parameter.required">
            <option v-if="!parameter.required" value="">全部</option>
            <option v-for="value in parameter.allowedValues" :key="value" :value="value">{{ value }}</option>
          </select>
          <input v-else v-model="demo.params[parameter.name]" :type="parameter.type.includes('DATE') ? 'date' : parameter.type === 'MONTH' ? 'month' : 'text'" :required="parameter.required" :pattern="parameter.type === 'TS_CODE' ? '[0-9]{6}[.](SZ|SH|BJ)' : parameter.pattern" :placeholder="parameter.type === 'TS_CODE' ? '例如 000001.SZ' : parameter.label" :title="parameter.type === 'TS_CODE' ? '6 位数字加交易所后缀，例如 000001.SZ' : parameter.description" autocomplete="off" />
          <small v-if="parameter.type === 'TS_CODE'" class="field-help">股票代码与交易所，例如 000001.SZ</small>
          <small v-else-if="parameter.description" class="field-help">{{ parameter.description }}</small>
        </label>
      </div>
      <div v-if="demo.mode === 'RANGE'" class="range-configuration">
        <div class="mode-heading"><h3>{{ demo.rangeCapability.dateLabel }}范围</h3><span>包含起止当天</span></div>
        <div class="range-fields">
          <label class="field"><span>开始日期 <small>必填</small></span><input v-model="demo.range.start" name="start_date" type="date" required :aria-invalid="Boolean(rangeError)" :aria-describedby="rangeError ? 'range-error' : undefined" /></label>
          <label class="field"><span>结束日期 <small>必填</small></span><input v-model="demo.range.end" name="end_date" type="date" required :aria-invalid="Boolean(rangeError)" :aria-describedby="rangeError ? 'range-error' : undefined" /></label>
        </div>
        <p v-if="demo.rangeCapability.responseOnly" class="mode-note">此接口仅采集返回记录，不保证区间数据完整。</p>
      </div>
      <p v-if="!demo.parameters.length && demo.mode === 'SINGLE'" class="empty-parameters">这个接口无需填写参数，可以直接下载。</p>
      <p v-if="error || rangeError" id="range-error" role="alert" class="form-error">{{ error || rangeError }}</p>
    </div>

    <div class="form-actions">
      <span class="form-footnote">示例操作 · 不连接真实数据源</span>
      <button class="button primary" type="submit" :disabled="demo.busy || Boolean(rangeError)"><Download />{{ demo.busy ? '正在创建…' : demo.mode === 'RANGE' ? '开始批量下载' : '开始下载' }}</button>
    </div>
  </form>
</template>
