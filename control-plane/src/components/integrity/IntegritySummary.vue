<script setup>
import { CircleCheckFilled, Clock, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { formatIngestedAt } from '../../utils/format.js'
import { formatIntegrityCount } from '../../utils/integrityReport.js'

defineProps({ detail: { type: Object, required: true } })
const execution = {
  QUEUED: ['排队中', Clock], RUNNING: ['执行中', Clock],
  COMPLETED: ['计算已完成', CircleCheckFilled], FAILED: ['执行失败', CircleCloseFilled],
  INTERRUPTED: ['已中断', WarningFilled],
}
const conclusions = {
  PASS: '通过', FAIL: '有问题', WARN: '待核实', UNKNOWN: '无法判定',
  NOT_APPLICABLE: 'N/A（不适用）',
}
const conclusionIcons = { PASS: CircleCheckFilled, FAIL: CircleCloseFilled, WARN: WarningFilled, UNKNOWN: WarningFilled, NOT_APPLICABLE: Clock }
const times = [['创建时间', 'createdAt'], ['开始时间', 'startedAt'], ['结束时间', 'finishedAt']]
</script>
<template>
  <section class="summary" aria-labelledby="integrity-summary-title">
    <header><h2 id="integrity-summary-title">报告总览</h2>
      <div class="states">
        <span class="state"><component :is="execution[detail.status][1]" aria-hidden="true" />{{ execution[detail.status][0] }}</span>
        <span class="conclusion"><component :is="conclusionIcons[detail.overallStatus]" aria-hidden="true" />数据结论：{{ conclusions[detail.overallStatus] }}</span>
      </div>
    </header>
    <p v-if="['QUEUED', 'RUNNING'].includes(detail.status)" class="active-note">进行中，已有结论</p>
    <div class="progress">
      <strong>已处理 {{ formatIntegrityCount(detail.completedUnits) }} / 计划 {{ detail.plannedUnits }}</strong>
      <span>执行错误 {{ formatIntegrityCount(detail.errorUnits) }}</span>
      <span>未运行 {{ formatIntegrityCount(detail.notRunUnits) }}</span>
    </div>
    <dl class="counts">
      <div v-for="status in ['PASS','FAIL','WARN','UNKNOWN','NOT_APPLICABLE']" :key="status">
        <dt>{{ conclusions[status] }}</dt><dd>{{ formatIntegrityCount(detail.statusCounts[status]) }}</dd>
      </div>
    </dl>
    <div class="columns">
      <section aria-label="原始请求">
        <h3>原始请求</h3>
        <dl>
          <div><dt>股票</dt><dd>{{ detail.originalRequest.symbols.join('、') }}</dd></div>
          <div><dt>日期</dt><dd>{{ detail.originalRequest.startDate }} 至 {{ detail.originalRequest.endDate }}</dd></div>
          <div><dt>接口</dt><dd>{{ detail.originalRequest.apiNames?.join('、') ?? '请求时未指定全部接口' }}</dd></div>
        </dl>
      </section>
      <section aria-label="固定执行范围">
        <h3>固定执行范围</h3>
        <dl>
          <div><dt>数据源</dt><dd>{{ detail.scope.pluginId }}</dd></div>
          <div><dt>股票</dt><dd>{{ detail.scope.symbols.join('、') }}</dd></div>
          <div><dt>日期</dt><dd>{{ detail.scope.startDate }} 至 {{ detail.scope.endDate }}</dd></div>
          <div><dt>接口</dt><dd>{{ detail.scope.apiNames.join('、') }}</dd></div>
        </dl>
        <p v-if="!Object.hasOwn(detail.originalRequest, 'apiNames')" class="note">原请求未指定接口；受理时固定为以下接口。</p>
      </section>
      <section aria-label="报告身份">
        <h3>报告身份</h3>
        <dl>
          <div><dt>检查编号</dt><dd><code>{{ detail.checkId }}</code></dd></div>
          <div><dt>提交编号</dt><dd><code>{{ detail.submissionId }}</code></dd></div>
          <div><dt>口径哈希</dt><dd><code>{{ detail.capabilityHash }}</code></dd></div>
        </dl>
      </section>
    </div>
    <dl class="times">
      <div v-for="([label,key]) in times" :key="key"><dt>{{ label }}</dt><dd>
        <time v-if="detail[key]" :datetime="detail[key]" :title="detail[key]">{{ formatIngestedAt(detail[key]) }}（Asia/Shanghai）</time>
        <span v-else>尚无</span>
      </dd></div>
    </dl>
    <p v-if="detail.errorCode || detail.errorMessage" class="error" role="alert">
      {{ detail.errorCode }}<span v-if="detail.errorCode && detail.errorMessage"> · </span>{{ detail.errorMessage }}
    </p>
  </section>
</template>

<style scoped>
.summary{min-width:0;padding:24px;border:1px solid var(--tensor-line);background:var(--tensor-surface)}header,.states,.progress{display:flex;align-items:center}header{justify-content:space-between;gap:18px}h2,h3,p{margin:0}h2{font-size:20px}h3{margin-bottom:12px;font-size:14px}.states{flex-wrap:wrap;justify-content:flex-end;gap:10px}.state,.conclusion{display:inline-flex;align-items:center;gap:6px;padding:6px 9px;border:1px solid var(--tensor-line);background:var(--tensor-raised);font-size:12px}.state svg,.conclusion svg{width:15px}.active-note{margin-top:10px;color:var(--tensor-muted);font-size:12px}.progress{flex-wrap:wrap;gap:16px;margin:20px 0;color:var(--tensor-muted);font-size:13px}.progress strong{color:var(--tensor-text)}.counts{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:1px;margin:0 0 20px;border:1px solid var(--tensor-line);background:var(--tensor-line)}.counts div{padding:12px;background:var(--tensor-raised)}dt{color:var(--tensor-muted);font-size:12px}.counts dd{margin:5px 0 0;font-size:18px}.columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.columns section{min-width:0;padding:16px;border:1px solid var(--tensor-line)}dl{margin:0}dl div+div{margin-top:9px}dd{margin:3px 0 0;overflow-wrap:anywhere}.note{margin-top:10px;color:var(--tensor-warning);font-size:12px}.times{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-top:16px}.error{margin-top:16px;color:var(--tensor-error);overflow-wrap:anywhere}@media(max-width:680px){.summary{padding:18px}header{align-items:flex-start;flex-direction:column}.states{justify-content:flex-start}.counts{grid-template-columns:repeat(2,minmax(0,1fr))}.columns,.times{grid-template-columns:minmax(0,1fr)}}
.counts>div,.times>div{margin-top:0}
</style>
