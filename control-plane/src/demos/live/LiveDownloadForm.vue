<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { Download, Check } from '@element-plus/icons-vue'
import { useParameterForm } from '../../composables/useParameterForm.js'
import ErrorNotice from './ErrorNotice.vue'

const props = defineProps({ flow: { type: Object, required: true } })
defineEmits(['detail'])
const form = ref(null)
// Include the form generation: revisiting a mode must reset its draft even if metadata is reused.
const parameters = computed(() => { void props.flow.formKey; return [...props.flow.parameters] })
const fields = reactive(useParameterForm(parameters))
const range = computed(() => props.flow.capabilities?.range)
const rangeFields = computed(() => props.flow.mode === 'RANGE' ? parameters.value.filter(p => [range.value.startParameter, range.value.endParameter].includes(p.name)) : [])
const otherFields = computed(() => parameters.value.filter(p => !rangeFields.value.includes(p)))
const receipt = computed(() => props.flow.receipt ?? props.flow.recoveredTask)

async function submit() {
  if (!props.flow.canSubmit) return
  if (!fields.validateValues()) {
    await nextTick()
    form.value?.querySelector('[aria-invalid="true"]')?.focus()
    return
  }
  await props.flow.submit(fields.normalizedValues())
}
</script>

<template>
  <form ref="form" class="download-form" novalidate @submit.prevent="submit">
    <template v-if="flow.selectedApi && flow.capabilities">
      <div class="interface-note"><span class="code">{{ flow.selectedApiName }}</span><span>{{ flow.selectedApi.category }}</span><span>写入后可查询</span></div>
      <div class="form-parameters">
        <div class="mode-heading"><h3>下载方式</h3><span>{{ flow.mode === 'RANGE' ? '按日期范围' : '单次请求' }}</span></div>
        <div class="mode-control" aria-label="下载方式">
          <button type="button" :class="{ selected: flow.mode === 'SINGLE' }" :aria-pressed="flow.mode === 'SINGLE'" :disabled="flow.locked || !flow.capabilities.single.available" @click="flow.mode !== 'SINGLE' && flow.selectMode('SINGLE')"><Check v-if="flow.mode === 'SINGLE'" />单次下载</button>
          <button type="button" :class="{ selected: flow.mode === 'RANGE' }" :aria-pressed="flow.mode === 'RANGE'" :disabled="flow.locked || range.availability !== 'AVAILABLE'" @click="flow.mode !== 'RANGE' && flow.selectMode('RANGE')"><Check v-if="flow.mode === 'RANGE'" />批量下载<small v-if="range.availability !== 'AVAILABLE'">{{ range.availability === 'NEEDS_VERIFICATION' ? '待验证' : '不支持' }}</small></button>
        </div>
        <p v-if="range.availability !== 'AVAILABLE'" class="mode-note">{{ range.unavailableReason || '此接口当前仅支持单次下载。' }}</p>
        <div v-for="(group, index) in [otherFields, rangeFields]" :key="index" :class="index ? 'range-configuration' : ''">
          <div v-if="index && group.length" class="mode-heading"><h3>{{ range.dateLabel }}范围</h3><span>包含起止当天</span></div>
          <div :class="index ? 'range-fields' : 'parameter-grid'">
            <label v-for="parameter in group" :key="parameter.name" class="field">
              <span>{{ parameter.label }} <small>{{ parameter.required ? '必填' : '选填' }}</small></span>
              <select v-if="parameter.type === 'ENUM'" :name="parameter.name" :value="fields.values[parameter.name]" :disabled="flow.locked" :aria-invalid="Boolean(fields.errors[parameter.name])" :aria-describedby="fields.errors[parameter.name] ? `error-${parameter.name}` : undefined" @change="fields.setValue(parameter.name, $event.target.value)"><option value="">{{ parameter.required ? '请选择' : '全部' }}</option><option v-for="value in parameter.allowedValues" :key="value" :value="value">{{ value }}</option></select>
              <input v-else :name="parameter.name" :type="parameter.type.includes('DATE') ? 'date' : parameter.type === 'MONTH' ? 'month' : 'text'" :value="fields.values[parameter.name]" :disabled="flow.locked" :aria-required="parameter.required" :aria-invalid="Boolean(fields.errors[parameter.name])" :aria-describedby="fields.errors[parameter.name] ? `error-${parameter.name}` : undefined" :placeholder="parameter.type === 'TS_CODE' ? '例如 000001.SZ' : parameter.label" autocomplete="off" @input="fields.setValue(parameter.name, $event.target.value)" />
              <small v-if="fields.errors[parameter.name]" :id="`error-${parameter.name}`" class="form-error" role="alert">{{ fields.errors[parameter.name] }}</small>
              <small v-else-if="parameter.description" class="field-help">{{ parameter.description }}</small>
            </label>
          </div>
        </div>
        <p v-if="flow.mode === 'RANGE' && range.completenessRule.kind === 'RESPONSE_ONLY'" class="mode-note">此接口仅采集返回记录，不保证区间数据完整。</p>
        <p v-if="!parameters.length" class="empty-parameters">这个接口无需填写参数，可以直接下载。</p>
      </div>
      <div class="form-actions"><span class="form-footnote">将创建真实下载任务，关闭页面后仍会继续。</span><button class="button primary" type="submit" :disabled="!flow.canSubmit"><Download />{{ flow.locked ? '正在处理…' : flow.mode === 'RANGE' ? '开始批量下载' : '开始下载' }}</button></div>
    </template>
    <div v-if="receipt" class="live-notice" role="status"><strong>任务已创建</strong><p>任务在后台执行，可随时查看进度。</p><button type="button" class="text-button" @click="$emit('detail', receipt.taskId)">查看任务详情</button></div>
    <div v-if="flow.submissionState === 'UNCERTAIN' && flow.pendingSubmission" class="live-notice" role="status"><strong>正在确认上次提交结果</strong><p>{{ flow.pendingSubmission.apiName }} 的任务可能已创建。查询或重发同一请求均使用原提交标识。</p><div class="live-actions"><button type="button" class="button secondary" :disabled="flow.locked" @click="flow.recoverSubmission">查询提交结果</button><button type="button" class="text-button" :disabled="flow.locked" @click="flow.replaySubmission">重发原请求</button></div></div>
    <p v-if="flow.submissionState === 'RECOVERING'" class="live-notice" role="status">正在找回上次提交的任务…</p>
    <ErrorNotice :error="flow.submissionError" title="提交反馈" />
    <ErrorNotice :error="flow.storageError" title="本地提交记录需要处理" :retry-label="flow.storageError?.kind === 'CORRUPT' ? undefined : '重试本地存储'" :disabled="flow.locked" @retry="flow.retryStorage" />
    <button v-if="flow.storageError?.kind === 'CORRUPT'" type="button" class="text-button" :disabled="flow.locked" @click="flow.clearCorruptSubmission">已核对近期任务，清除损坏的提交记录</button>
  </form>
</template>
