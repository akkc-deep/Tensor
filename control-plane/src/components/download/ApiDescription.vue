<script setup>
import { isRangeMode } from '../../utils/downloadPolicy.js'

const props = defineProps({
  api: { type: Object, default: null },
})

const MODE_LABELS = {
  TRADE_DATE_RANGE: '交易日期区间',
  ANN_DATE_RANGE: '公告日期区间',
  MONTH_RANGE: '覆盖月份',
  NATIVE_RANGE: '原生日期范围',
  ORIGINAL_PARAMS: '保留原条件',
}

const GUIDANCE = {
  TRADE_DATE_RANGE: '按交易日期下载，包含起止日期；自动跳过适用交易日历确认的休市日。个股停牌不作为市场休市处理。',
  ANN_DATE_RANGE: '按公告日期下载，包含起止日期及周末、节假日；公告日期不等于财务报告期或事件生效日期。',
  MONTH_RANGE: '按所选日期覆盖的完整月份下载，月度数据不按日切分。',
  CALENDAR_DATE: '下载所选交易所的日历日期范围，包含开盘及休市记录，不套用交易日期下载的休市过滤。',
  IPO_DATE: '按IPO申购日期范围下载，申购日期不等同于上市日期；实际筛选与完整性仍需来源核实。',
  ANN_DATE: '按名称变更公告日期范围下载，不将起止日期解释为名称有效期；实际筛选与完整性仍需来源核实。',
  ORIGINAL_PARAMS: '当前接入方式不支持按历史区间下载；请按现有条件获取数据。',
}

function guidance(api) {
  return api.downloadPolicy?.mode === 'NATIVE_RANGE'
    ? GUIDANCE[api.downloadPolicy.dateSemantic]
    : GUIDANCE[api.downloadPolicy?.mode]
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
        <dt>下载方式</dt>
        <dd class="api-description__query-mode">
          {{ MODE_LABELS[props.api.downloadPolicy?.mode] }}
        </dd>
      </div>
    </dl>
    <div class="api-description__guidance">
      <p>{{ guidance(props.api) }}</p>
      <p v-if="props.api.downloadPolicy?.description">{{ props.api.downloadPolicy.description }}</p>
      <p v-if="isRangeMode(props.api.downloadPolicy?.mode)">
        单次最多支持 {{ props.api.downloadPolicy?.limits?.maxRangeDays }} 个自然日（含起止日期）
      </p>
      <p v-if="isRangeMode(props.api.downloadPolicy?.mode)">区间下载开始后不可终止。</p>
      <p v-if="props.api.apiName === 'weekly'">结果保持周线粒度；所选日期用于匹配来源记录的交易日期，不生成每日记录，也不自动扩展为整个周。</p>
      <p v-if="props.api.apiName === 'monthly'">结果保持月线粒度；所选日期用于匹配来源记录的交易日期，不生成每日记录，也不自动扩展为整个月。</p>
    </div>
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

.api-description__guidance { margin-top: 14px; color: var(--tensor-muted); font-size: 13px; line-height: 1.6; }
.api-description__guidance p { margin: 4px 0 0; }

@media (max-width: 680px) {
  .api-description dl {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
