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
  <section
    v-if="state === 'SUCCESS'"
    class="download-result"
    role="status"
    aria-live="polite"
  >
    <h2>下载成功</h2>
    <dl>
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
  </section>

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
  >
    <template v-if="error.requestId || canRetry" #actions>
      <p v-if="error.requestId">请求 ID：{{ error.requestId }}</p>
      <el-button
        v-if="canRetry"
        native-type="button"
        @click="emit('retry')"
      >
        使用原参数重试
      </el-button>
    </template>
  </AsyncStatePanel>
</template>
