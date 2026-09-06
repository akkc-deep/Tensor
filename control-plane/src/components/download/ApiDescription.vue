<script setup>
const props = defineProps({
  api: { type: Object, default: null },
})

const QUERY_MODE_LABELS = {
  trade_date: '交易日',
  ann_date: '公告日',
  snapshot: '快照',
  date_range: '日期范围',
}

function queryModeLabel(queryMode) {
  return QUERY_MODE_LABELS[queryMode] ?? queryMode
}
</script>

<template>
  <section
    v-if="props.api"
    class="api-description"
    aria-labelledby="api-description-title"
  >
    <h2 id="api-description-title">接口说明</h2>
    <dl>
      <div>
        <dt>中文说明</dt>
        <dd class="api-description__display-name">{{ props.api.displayName }}</dd>
      </div>
      <div>
        <dt>接口名</dt>
        <dd><code class="api-description__api-name">{{ props.api.apiName }}</code></dd>
      </div>
      <div>
        <dt>分类</dt>
        <dd class="api-description__category">{{ props.api.category }}</dd>
      </div>
      <div>
        <dt>查询方式</dt>
        <dd class="api-description__query-mode">
          {{ queryModeLabel(props.api.queryMode) }}
        </dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.api-description {
  min-width: 0;
  margin: 22px 0 28px;
}

.api-description h2 {
  margin: 0 0 14px;
  font-size: 13px;
  font-weight: 600;
}

.api-description dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  gap: 14px 18px;
}

.api-description dt {
  color: var(--tensor-muted);
  font-size: 12px;
  line-height: 1.5;
}

.api-description dd {
  margin: 4px 0 0;
  color: var(--tensor-text);
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.api-description code {
  white-space: normal;
  overflow-wrap: anywhere;
}

@media (max-width: 680px) {
  .api-description dl {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
