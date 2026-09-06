<script setup>
import AsyncStatePanel from '../common/AsyncStatePanel.vue'

defineProps({
  state: {
    type: String,
    required: true,
    validator: (value) => ['SUCCESS', 'EMPTY', 'FAILURE'].includes(value),
  },
  result: { type: Object, default: null },
  error: { type: Object, default: null },
  canRetry: { type: Boolean, default: false },
})

const emit = defineEmits(['retry'])
</script>

<template>
  <AsyncStatePanel
    v-if="state === 'SUCCESS'"
    class="download-result"
    state="SUCCESS"
    title="下载成功"
    message="本次数据已完成写入。"
  >
    <dl class="download-result__counts">
      <div>
        <dt>上游返回数</dt>
        <dd>{{ result.sourceRowCount }}</dd>
      </div>
      <div>
        <dt>插入数</dt>
        <dd>{{ result.insertedRows }}</dd>
      </div>
      <div>
        <dt>更新数</dt>
        <dd>{{ result.updatedRows }}</dd>
      </div>
    </dl>
  </AsyncStatePanel>

  <AsyncStatePanel
    v-else-if="state === 'EMPTY'"
    state="EMPTY"
    title="下载成功，0 条数据"
    message="本次请求没有可写入的数据。"
  />

  <AsyncStatePanel
    v-else-if="state === 'FAILURE'"
    state="FAILURE"
    title="下载失败"
    :message="error.message"
    :request-id="error.requestId"
    :retry-label="canRetry ? '使用原参数重试' : ''"
    @retry="emit('retry')"
  />
</template>

<style scoped>
.download-result__counts {
  display: grid;
  width: 100%;
  grid-template-columns: 1.25fr 1fr 1fr;
  gap: 14px;
  margin: 25px 0 0;
  padding-top: 24px;
  border-top: 1px solid var(--tensor-line);
}

.download-result__counts div {
  min-width: 0;
}

.download-result__counts dt {
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.5;
}

.download-result__counts dd {
  margin: 9px 0 0;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: clamp(19px, 2vw, 28px);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
}

.download-result__counts > div:first-child dd {
  color: var(--tensor-accent);
}

</style>
