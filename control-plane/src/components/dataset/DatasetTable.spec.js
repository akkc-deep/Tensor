import { flushPromises, mount } from '@vue/test-utils'
import { ElTableColumn } from 'element-plus'
import { nextTick } from 'vue'

import DatasetTable from './DatasetTable.vue'

function column(name, label = name, logicalType = 'STRING', extra = {}) {
  return { name, label, logicalType, nullable: true, displayOrder: 0, ...extra }
}

function renderedColumns(wrapper) {
  return wrapper.findAllComponents(ElTableColumn)
}

function firstRowTexts(wrapper) {
  return wrapper.findAll('.el-table__body tbody tr')[0]
    .findAll('td .cell')
    .map((cell) => cell.text())
}

describe('DatasetTable', () => {
  it('renders immutable business metadata order followed by only the three source columns', async () => {
    const columns = [
      column('trade_date', '交易日期', 'DATE'),
      column('open', '开盘价', 'DECIMAL'),
      column('notes', '备注', 'TEXT', { longText: true }),
    ]
    const items = [{
      trade_date: '2026-09-04',
      open: '12.340000000000000000',
      notes: '说明',
      source_plugin: 'tushare_pro',
      source_api: 'daily',
      ingested_at: '2026-09-05T00:00:00Z',
      ignored: 'not a column',
    }]
    const originalColumns = structuredClone(columns)
    const originalItems = structuredClone(items)
    const wrapper = mount(DatasetTable, { props: { columns, items } })

    await flushPromises()

    expect(renderedColumns(wrapper).map((current) => current.props('prop'))).toEqual([
      'trade_date', 'open', 'notes', 'source_plugin', 'source_api', 'ingested_at',
    ])
    expect(renderedColumns(wrapper).map((current) => current.props('label'))).toEqual([
      '交易日期', '开盘价', '备注', 'source_plugin', 'source_api', 'ingested_at',
    ])
    expect(wrapper.findAll('.el-table__header th .cell').map((header) => header.text())).toEqual([
      '交易日期', '开盘价', '备注', 'source_plugin', 'source_api', 'ingested_at',
    ])
    expect(columns).toEqual(originalColumns)
    expect(items).toEqual(originalItems)
  })

  it('renders all 152 business columns in a horizontally scrollable table without utility columns', async () => {
    const columns = Array.from({ length: 152 }, (_, index) => {
      const ordinal = String(index + 1).padStart(3, '0')
      return column(`column_${ordinal}`, `字段 ${ordinal}`)
    })
    const wrapper = mount(DatasetTable, { props: { columns, items: [] }, attachTo: document.body })

    try {
      await flushPromises()
      const allColumns = renderedColumns(wrapper)
      const properties = allColumns.map((current) => current.props('prop'))

      expect(allColumns).toHaveLength(155)
      expect(properties.slice(0, 2)).toEqual(['column_001', 'column_002'])
      expect(properties.slice(150)).toEqual([
        'column_151', 'column_152', 'source_plugin', 'source_api', 'ingested_at',
      ])
      expect(new Set(properties.slice(0, 152)).size).toBe(152)
      expect(allColumns.every((current) => current.props('type') === 'default')).toBe(true)
      expect(getComputedStyle(wrapper.get('.dataset-table').element).overflowX).toBe('auto')
      expect(getComputedStyle(wrapper.get('.dataset-table').element).maxWidth).toBe('100%')
    } finally {
      wrapper.unmount()
    }
  })

  it('fixes one business column without changing rendered metadata order', async () => {
    const withTsCode = mount(DatasetTable, {
      props: {
        columns: [column('trade_date'), column('ts_code'), column('close', '收盘价', 'DECIMAL')],
        items: [{
          trade_date: '2026-09-04',
          ts_code: '000001.SZ',
          close: '12.34',
          source_plugin: 'tushare_pro',
          source_api: 'daily',
          ingested_at: '2026-09-05T00:00:00Z',
        }],
      },
    })
    const withoutTsCode = mount(DatasetTable, {
      props: { columns: [column('ann_date', '公告日期', 'DATE'), column('end_date')], items: [] },
    })
    const withoutBusinessColumns = mount(DatasetTable, { props: { columns: [], items: [] } })

    await flushPromises()

    const withTsHeaders = withTsCode.findAll('.el-table__header th')
    expect(withTsHeaders.map((header) => header.get('.cell').text())).toEqual([
      'trade_date', 'ts_code', '收盘价', 'source_plugin', 'source_api', 'ingested_at',
    ])
    expect(withTsHeaders.map((header) => header.element.style.position)).toEqual([
      '', 'sticky', '', '', '', '',
    ])
    expect(withTsHeaders[1].element.style.left).toBe('0px')
    expect(withTsHeaders[1].element.style.zIndex).toBe('calc(var(--el-table-index) + 1)')

    const withTsCells = withTsCode.findAll('.el-table__body tbody tr')[0].findAll('td')
    expect(withTsCells.map((cell) => cell.get('.cell').text())).toEqual([
      '2026-09-04', '000001.SZ', '12.34', 'tushare_pro', 'daily', '2026-09-05 08:00:00',
    ])
    expect(withTsCells.map((cell) => cell.element.style.position)).toEqual([
      '', 'sticky', '', '', '', '',
    ])
    expect(withTsCells[1].element.style.zIndex).toBe('calc(var(--el-table-index) + 1)')

    const withoutTsHeaders = withoutTsCode.findAll('.el-table__header th')
    expect(withoutTsHeaders.map((header) => header.element.style.position)).toEqual([
      'sticky', '', '', '', '',
    ])
    expect(withoutTsHeaders[0].element.style.left).toBe('0px')
    expect(withoutTsHeaders[0].element.style.zIndex).toBe('calc(var(--el-table-index) + 1)')

    const emptyHeaders = withoutBusinessColumns.findAll('.el-table__header th')
    expect(emptyHeaders.map((header) => header.get('.cell').text())).toEqual([
      'source_plugin', 'source_api', 'ingested_at',
    ])
    expect(emptyHeaders.every((header) => header.element.style.position !== 'sticky')).toBe(true)
  })

  it('formats empty, exact numeric, date, and ingestion values in real cells without precision loss', async () => {
    const columns = [
      column('nullable'),
      column('missing'),
      column('zero'),
      column('empty'),
      column('precise', '精确值', 'DECIMAL'),
      column('count', '计数', 'LONG'),
      column('trade_date', '交易日期', 'DATE'),
    ]
    const items = [{
      nullable: null,
      zero: 0,
      empty: '',
      precise: '12345678901234567890.123456789012345678',
      count: '9223372036854775807',
      trade_date: '2026-09-04',
      source_plugin: 'tushare_pro',
      source_api: 'daily',
      ingested_at: '2026-08-25T02:30:15.123Z',
    }]
    const wrapper = mount(DatasetTable, { props: { columns, items } })

    await flushPromises()

    expect(firstRowTexts(wrapper)).toEqual([
      '--',
      '--',
      '0',
      '',
      '12345678901234567890.123456789012345678',
      '9223372036854775807',
      '2026-09-04',
      'tushare_pro',
      'daily',
      '2026-08-25 10:30:15',
    ])
  })

  it('uses approved widths and shows overflowing formatted values in a plain-text tooltip', async () => {
    const rawValue = '<strong>完整的长文本值</strong>'
    const columns = [column('ts_code'), column('notes', '备注', 'TEXT', { longText: true })]
    const items = [{
      ts_code: '000001.SZ',
      notes: rawValue,
      source_plugin: 'tushare_pro',
      source_api: 'daily',
      ingested_at: '2026-08-25T02:30:15Z',
    }]
    const wrapper = mount(DatasetTable, {
      attachTo: document.body,
      props: { columns, items },
    })

    try {
      await flushPromises()
      const allColumns = renderedColumns(wrapper)
      expect(allColumns.every((current) => current.props('showOverflowTooltip') === true)).toBe(true)
      expect(allColumns.map((current) => current.props('minWidth'))).toEqual([140, 240, 140, 140, 180])

      const longTextCell = wrapper.findAll('.el-table__body tbody tr')[0].findAll('td')[1]
      const content = longTextCell.get('.cell').element
      Object.defineProperty(content, 'scrollWidth', { configurable: true, value: 400 })
      content.getBoundingClientRect = () => ({
        width: 100, height: 24, top: 0, right: 100, bottom: 24, left: 0, x: 0, y: 0,
        toJSON() {},
      })
      const rangeSpy = vi.spyOn(document, 'createRange').mockReturnValue({
        setStart() {},
        setEnd() {},
        getBoundingClientRect: () => ({ width: 400, height: 24 }),
      })

      await longTextCell.trigger('mouseenter')
      await flushPromises()

      expect(rangeSpy).toHaveBeenCalledOnce()
      await vi.waitFor(() => {
        expect(document.body.querySelector('.el-popper')).not.toBeNull()
      })
      const tooltip = document.body.querySelector('.el-popper')
      expect(tooltip.textContent).toBe(rawValue)
      expect(wrapper.find('strong').exists()).toBe(false)
      expect(tooltip.querySelector('strong')).toBeNull()

      const ingestionCell = wrapper.findAll('.el-table__body tbody tr')[0].findAll('td')[4]
      const ingestionContent = ingestionCell.get('.cell').element
      Object.defineProperty(ingestionContent, 'scrollWidth', { configurable: true, value: 400 })
      ingestionContent.getBoundingClientRect = content.getBoundingClientRect
      await ingestionCell.trigger('mouseenter')
      await vi.waitFor(() => {
        expect(document.body.querySelector('.el-popper')?.textContent).toBe('2026-08-25 10:30:15')
      })
      expect(rangeSpy).toHaveBeenCalledTimes(2)
    } finally {
      wrapper.unmount()
    }
  })

  it('synchronizes loading and aria-busy without clearing data or exposing mutating controls', async () => {
    vi.useFakeTimers()
    const columns = [column('ts_code')]
    const items = [{
      ts_code: '000001.SZ',
      source_plugin: 'tushare_pro',
      source_api: 'daily',
      ingested_at: '2026-08-25T02:30:15Z',
    }]
    const wrapper = mount(DatasetTable, { props: { columns, items, loading: false } })

    try {
      expect(wrapper.get('.dataset-table').attributes('aria-busy')).toBe('false')
      expect(wrapper.find('.el-loading-mask').exists()).toBe(false)

      await wrapper.setProps({ loading: true })
      await nextTick()
      expect(wrapper.get('.dataset-table').attributes('aria-busy')).toBe('true')
      expect(wrapper.find('.el-loading-mask').exists()).toBe(true)
      expect(renderedColumns(wrapper)).toHaveLength(4)
      expect(firstRowTexts(wrapper)).toContain('000001.SZ')

      await wrapper.setProps({ loading: false })
      await nextTick()
      vi.runAllTimers()
      await nextTick()
      expect(wrapper.get('.dataset-table').attributes('aria-busy')).toBe('false')
      expect(wrapper.find('.el-loading-mask').exists()).toBe(false)
      expect(firstRowTexts(wrapper)).toContain('000001.SZ')
      expect(wrapper.find('button, input, select, textarea').exists()).toBe(false)
      expect(Object.keys(wrapper.emitted())).toEqual([])
      expect(renderedColumns(wrapper).every((current) => current.props('sortable') === false)).toBe(true)
    } finally {
      wrapper.unmount()
      vi.useRealTimers()
    }
  })
})
