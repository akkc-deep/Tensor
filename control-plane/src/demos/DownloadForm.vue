<script setup>
import { inject, ref, watch } from 'vue'
import { Download, Check } from '@element-plus/icons-vue'
import { demoKey } from './demoState.js'

const demo = inject(demoKey)
const form = ref(null)
const error = ref('')
watch(() => demo.selectedId, () => { error.value = '' })

function validate() {
  error.value = ''
  if (!form.value.reportValidity()) return false
  for (const p of demo.selected.parameters) {
    if (p.relatedParameter && p.name.includes('start') && demo.params[p.name] > demo.params[p.relatedParameter]) {
      error.value = '开始日期不能晚于结束日期，请重新选择。'
      return false
    }
  }
  return true
}
function submit() {
  if (validate()) demo.submit()
}
</script>

<template>
  <form ref="form" class="download-form" @submit.prevent="submit">
    <div class="interface-note"><span class="code">{{ demo.selected.id }}</span><span>{{ demo.selected.category }}</span><span>写入后可查询</span></div>

    <div class="form-parameters">
      <div class="mode-heading"><h3>下载方式</h3><span>单次请求</span></div>
      <div class="mode-control" aria-label="下载方式">
        <button type="button" class="selected" aria-pressed="true"><Check /> 单次下载</button>
        <button type="button" disabled :title="demo.selected.rangePending ? '区间参数语义与完整性尚待真实接口验证' : '当前接口不支持日期范围下载'">日期范围 <small>{{ demo.selected.rangePending ? '待验证' : '不支持' }}</small></button>
      </div>
      <div class="parameter-grid">
        <label v-for="parameter in demo.selected.parameters" :key="parameter.name" class="field">
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
      <p v-if="!demo.selected.parameters.length" class="empty-parameters">这个接口无需填写参数，可以直接下载。</p>
      <p v-if="error" role="alert" class="form-error">{{ error }}</p>
    </div>

    <div class="form-actions">
      <span class="form-footnote">示例操作 · 不连接真实数据源</span>
      <button class="button primary" type="submit" :disabled="demo.busy"><Download />{{ demo.busy ? '正在创建…' : '开始下载' }}</button>
    </div>
  </form>
</template>
