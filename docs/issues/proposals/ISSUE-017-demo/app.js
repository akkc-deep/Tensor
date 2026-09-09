/* Independent UI demo: all operations use local sample data, never a backend. */
const { createApp, ref, computed, watch, nextTick, onMounted, onBeforeUnmount, h } = Vue;
const iconPaths = {
  table: ['M4 5h16v14H4z', 'M4 10h16M9 5v14'],
  download: ['M12 3v12m-5-5 5 5 5-5', 'M4 16v4h16v-4'],
  settings: ['M4 7h7m4 0h5M4 17h3m4 0h9', 'M15 7a2 2 0 1 1-4 0 2 2 0 0 1 4 0M11 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0'],
  chevron: ['m7 10 5 5 5-5'], left: ['m14 6-6 6 6 6'], right: ['m10 6 6 6-6 6'],
  search: ['M19 19l-4-4', 'M16 10a6 6 0 1 1-12 0 6 6 0 0 1 12 0'],
  check: ['m5 12 4 4L19 6'], close: ['m6 6 12 12M6 18 18 6'],
  info: ['M12 11v5M12 7v.1', 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0'],
  alert: ['m12 3 9 17H3L12 3zM12 9v5M12 17v.1'],
  flask: ['M9 3h6M10 3v7l-5 8a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-5-8V3M8 15h8'],
  inbox: ['M4 6h16v13H4zM4 13h5l2 3h2l2-3h5'],
  retry: ['M20 7v5h-5', 'M19 12a7 7 0 1 0-1 5'],
  spinner: ['M20 12a8 8 0 1 1-8-8'],
  compact: ['M5 5h14M5 9h14M5 13h14M5 17h14'],
  standard: ['M5 6h14M5 12h14M5 18h14'],
  comfort: ['M5 7h14M5 17h14'],
};
const UiIcon = {
  props: ['name'],
  setup: (props) => () => h('svg', { class: ['icon', props.name], viewBox: '0 0 24 24', 'aria-hidden': 'true' },
    (iconPaths[props.name] || iconPaths.info).map(d => h('path', { d }))),
};
const DemoPicker = {
  components: { UiIcon },
  props: { id: String, modelValue: String, options: Array, searchable: Boolean, disabled: Boolean, compact: Boolean },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const root = ref(), trigger = ref(), popover = ref(), searchInput = ref(), opened = ref(false), search = ref(''), active = ref(0), position = ref({});
    const selected = computed(() => props.options.find(item => item.value === props.modelValue));
    const filtered = computed(() => props.options.filter(item => `${item.label} ${item.meta || ''}`.toLowerCase().includes(search.value.toLowerCase())));
    function close() { opened.value = false; search.value = ''; }
    async function toggle() {
      if (props.disabled) return;
      opened.value = !opened.value; search.value = '';
      active.value = Math.max(0, props.options.findIndex(item => item.value === props.modelValue));
      const rect = trigger.value.getBoundingClientRect(), below = innerHeight - rect.bottom - 12, above = rect.top - 12;
      const upwards = below < Math.min(300, props.options.length * (props.compact ? 40 : 62) + (props.searchable ? 60 : 16)) && above > below;
      position.value = {
        left: Math.max(12, Math.min(rect.left, innerWidth - rect.width - 12)) + 'px',
        width: Math.min(rect.width, innerWidth - 24) + 'px',
        ...(upwards ? { bottom: innerHeight - rect.top + 8 + 'px' } : { top: rect.bottom + 8 + 'px' }),
        '--menu-height': Math.max(80, Math.min(340, upwards ? above : below)) + 'px',
        transformOrigin: upwards ? 'bottom' : 'top',
      };
      if (opened.value && props.searchable) { await nextTick(); searchInput.value?.focus(); }
    }
    function choose(item) { emit('update:modelValue', item.value); close(); trigger.value.focus(); }
    function keydown(event) {
      if (event.key === 'Escape' && opened.value) { event.preventDefault(); close(); trigger.value.focus(); }
      else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        if (!opened.value) { toggle(); return; }
        active.value = (active.value + (event.key === 'ArrowDown' ? 1 : -1) + filtered.value.length) % (filtered.value.length || 1);
        nextTick(() => popover.value?.querySelector('.highlighted')?.scrollIntoView({ block: 'nearest' }));
      } else if (event.key === 'Enter' && opened.value) {
        event.preventDefault(); if (filtered.value[active.value]) choose(filtered.value[active.value]);
      } else if (event.key === 'Tab') close();
    }
    const outside = event => { if (!root.value.contains(event.target) && !popover.value?.contains(event.target)) close(); };
    const onScroll = event => { if (!popover.value?.contains(event.target)) close(); };
    watch(search, () => { active.value = 0; });
    watch(() => props.disabled, disabled => { if (disabled) close(); });
    onMounted(() => { document.addEventListener('pointerdown', outside); window.addEventListener('resize', close); window.addEventListener('scroll', onScroll, true); window.addEventListener('hashchange', close); });
    onBeforeUnmount(() => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', close); window.removeEventListener('scroll', onScroll, true); window.removeEventListener('hashchange', close); });
    return { root, trigger, popover, searchInput, opened, search, active, position, selected, filtered, toggle, choose, keydown };
  },
  template: `<div class="picker" :class="{ 'picker-compact': compact }" ref="root" @keydown="keydown">
    <button :id="id" ref="trigger" type="button" class="picker-trigger" :disabled="disabled" :role="searchable ? null : 'combobox'" :aria-activedescendant="!searchable && opened && filtered[active] ? id + '-option-' + active : null" :aria-expanded="opened" :aria-controls="id + '-options'" aria-haspopup="listbox" @click="toggle"><span>{{ selected?.label || '请选择' }}</span><span class="picker-meta" v-if="selected?.meta && !searchable">{{ selected.meta }}</span><ui-icon name="chevron"/></button>
    <teleport to="#app" defer><transition name="popover"><div v-if="opened" ref="popover" class="picker-popover" :class="{ 'menu-compact': compact }" :style="position" @keydown="keydown"><div v-if="searchable" class="picker-search"><ui-icon name="search"/><input ref="searchInput" v-model="search" placeholder="搜索接口" :aria-label="'搜索选项'" role="combobox" aria-expanded="true" :aria-controls="id + '-options'" :aria-activedescendant="filtered[active] ? id + '-option-' + active : null"></div><div class="picker-options" :id="id + '-options'" role="listbox" :aria-label="'可选项'"><button v-for="(item, index) in filtered" :id="id + '-option-' + index" :key="item.value" class="picker-option" :class="{ highlighted: active === index }" type="button" role="option" :aria-selected="modelValue === item.value" tabindex="-1" @pointermove="active = index" @click="choose(item)"><span>{{ item.label }}<small v-if="item.meta">{{ item.meta }}</small></span><ui-icon v-if="modelValue === item.value" name="check"/></button><p v-if="!filtered.length" class="picker-no-results">没有匹配的选项</p></div></div></transition></teleport>
  </div>`,
};
const DATA = window.TENSOR_DEMO_DATA;
const preferenceKey = 'tensor-issue-017-demo-apple';
const rgb = value => value.slice(1).match(/../g).map(channel => parseInt(channel, 16));
const hex = channels => '#' + channels.map(channel => Math.round(channel).toString(16).padStart(2, '0')).join('');
const mix = (a, b, weight) => hex(rgb(a).map((channel, i) => channel * (1 - weight) + rgb(b)[i] * weight));
function luminance(value) { return rgb(value).map(channel => { const c = channel / 255; return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4; }).reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0); }
function contrast(a, b) { const x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); }

createApp({
  components: { UiIcon, DemoPicker },
  setup() {
    let preferences = {};
    try { preferences = JSON.parse(localStorage.getItem(preferenceKey) || '{}') || {}; } catch {}
    const titles = { datasets: '数据查看', downloads: '数据下载', settings: '外观与设置' };
    const descriptions = { datasets: '从市场数据中，找到你需要的答案。', downloads: '连接数据源，让研究从一份可靠的数据开始。', settings: '清晰、舒适，让工作台更合你的习惯。' };
    const route = ref(Object.hasOwn(titles, location.hash.slice(1)) ? location.hash.slice(1) : 'datasets');
    const data = DATA, datasetKey = ref('daily'), source = ref('tushare_pro');
    const sources = [{ value: 'tushare_pro', label: 'Tushare Pro' }];
    const datasetOptions = Object.entries(DATA).map(([value, dataset]) => ({ value, label: dataset.label, meta: value }));
    const pageSizes = [20, 50, 100].map(value => ({ value: String(value), label: value + ' 条' }));
    const scenarios = [{ value: 'success', label: '正常返回' }, { value: 'empty', label: '空结果' }, { value: 'error', label: '请求失败' }, { value: 'slow', label: '慢速响应' }];
    const downloadOptions = datasetOptions.filter(item => item.value !== 'precision');
    const exchanges = [{ value: 'SSE', label: '上海证券交易所', meta: 'SSE' }, { value: 'SZSE', label: '深圳证券交易所', meta: 'SZSE' }, { value: 'BSE', label: '北京证券交易所', meta: 'BSE' }];
    const dataset = computed(() => DATA[datasetKey.value]);
    const draft = ref({ code: '', start: '', end: '' }), committed = ref({ code: '', start: '', end: '' });
    const page = ref(1), pageSize = ref(50), queryState = ref('success'), queryError = ref(''), forcedEmpty = ref(false), scenario = ref('success');
    const tableScroller = ref(), fontReady = ref(0), textDialog = ref(), detailTitle = ref(''), detailText = ref('');
    const density = ref(['compact', 'standard', 'comfort'].includes(preferences.density) ? preferences.density : 'standard');
    const font = ref(preferences.font === 'noto' ? 'noto' : 'system');
    const showFieldCodes = ref(false);
    const motion = ref(preferences.motion === 'reduce' ? 'reduce' : 'standard');
    const prefersReduced = ref(matchMedia('(prefers-reduced-motion: reduce)').matches);
    const densities = [{ value: 'compact', label: '紧凑', icon: 'compact' }, { value: 'standard', label: '标准', icon: 'standard' }, { value: 'comfort', label: '舒适', icon: 'comfort' }];
    const swatches = [{ value: '#0066cc', label: '蓝色' }, { value: '#20766b', label: '青绿' }, { value: '#76578e', label: '鸢尾' }, { value: '#a95837', label: '陶土' }, { value: '#475569', label: '石墨' }];
    const themeHex = ref(/^#[0-9a-f]{6}$/i.test(preferences.theme) ? preferences.theme : '#0066cc');
    const themeDraft = ref(themeHex.value.toUpperCase()), themeApplied = ref(themeHex.value), themeError = ref(''), storageStatus = ref('偏好保存在当前浏览器');
    let queryGeneration = 0;
    const filtered = computed(() => {
      if (forcedEmpty.value) return [];
      const { code, start, end } = committed.value;
      return dataset.value.rows.filter(row => (!code || String(row.ts_code || '').toUpperCase().includes(code.toUpperCase())) && (!dataset.value.dateKey || ((!start || (row[dataset.value.dateKey] || '') >= start) && (!end || (row[dataset.value.dateKey] || '') <= end))));
    });
    const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)));
    const pageRows = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
    function numberParts(value, key) {
      let raw = String(value ?? '');
      if (['change', 'pct_chg'].includes(key) && !raw.startsWith('-') && !raw.startsWith('+') && /[1-9]/.test(raw)) raw = '+' + raw;
      const [integer, fraction = ''] = raw.split('.');
      return { integer, fraction, hasPoint: raw.includes('.') };
    }
    const canvas = document.createElement('canvas'), measure = canvas.getContext('2d');
    const displayColumns = computed(() => {
      fontReady.value;
      measure.font = `14px ${font.value === 'system' ? 'system-ui' : '"Tensor Sans"'}`;
      // Canvas measures proportional numerals; use the table's tabular metrics instead.
      const probe = document.createElement('span');
      probe.style.cssText = `position:fixed;visibility:hidden;white-space:nowrap;font:${measure.font};font-variant-numeric:tabular-nums`;
      document.body.append(probe);
      probe.textContent = '0'; const digitWidth = probe.getBoundingClientRect().width;
      probe.textContent = '.'; const pointWidth = probe.getBoundingClientRect().width;
      probe.remove();
      return dataset.value.columns.map(col => {
        const numeric = ['LONG', 'DECIMAL'].includes(col.kind), values = dataset.value.rows.map(row => row[col.key]).filter(value => value != null);
        const fraction = numeric ? Math.max(0, ...values.map(value => String(value).split('.')[1]?.length || 0)) : 0;
        const maxText = Math.max(0, ...values.map(value => measure.measureText(String(value)).width));
        const labelSize = measure.measureText(col.label).width + 32;
        const integerWidth = numeric ? Math.max(0, ...values.map(value => numberParts(value, col.key).integer.length * digitWidth)) : 0;
        const numberWidth = Math.ceil(integerWidth + (fraction ? pointWidth + fraction * digitWidth : 0));
        let width = col.key === 'ts_code' ? 120 : col.kind === 'DATE' ? 120 : col.key === 'ingested_at' ? 192 : col.long ? 280 : numeric ? 100 : 148;
        if (numeric) {
          width = Math.max(width, numberWidth + 36);
        } else width = Math.max(width, Math.min(col.long ? 360 : 250, Math.ceil(maxText + 26)));
        width = Math.max(width, Math.min(labelSize, col.long ? 360 : 280));
        return { ...col, long: col.long || (!numeric && col.kind === 'STRING' && maxText + 32 > width), numeric, fraction, digitWidth, pointWidth, numberWidth, width: Math.ceil(width) };
      });
    });
    function numberText(value, key) { const part = numberParts(value, key); return part.integer + (part.hasPoint ? '.' + part.fraction : ''); }
    function numberStyle(value, col) {
      const part = numberParts(value, col.key);
      return { width: col.numberWidth + 'px', paddingRight: ((col.fraction - part.fraction.length) * col.digitWidth + (col.fraction && !part.hasPoint ? col.pointWidth : 0)) + 'px' };
    }
    const tableWidth = computed(() => displayColumns.value.reduce((total, col) => total + col.width, 0));
    function marketClass(value, key) {
      if (!['change', 'pct_chg'].includes(key) || value == null || !/[1-9]/.test(String(value))) return '';
      return String(value).startsWith('-') ? 'negative' : 'positive';
    }
    function selectDataset(key) {
      if (!DATA[key]) return;
      queryGeneration++; datasetKey.value = key; page.value = 1;
      draft.value = { code: '', start: '', end: '' }; committed.value = { ...draft.value };
      forcedEmpty.value = false; queryState.value = 'success'; queryError.value = '';
      nextTick(() => { if (tableScroller.value) { tableScroller.value.scrollTop = 0; tableScroller.value.scrollLeft = 0; } });
    }
    async function query(retry = false, targetPage = 1, useSnapshot = false) {
      if (!retry && !useSnapshot && draft.value.start && draft.value.end && draft.value.start > draft.value.end) {
        queryError.value = '开始日期不能晚于结束日期。'; document.getElementById('query-start')?.focus(); return;
      }
      const generation = ++queryGeneration, effect = retry ? 'success' : scenario.value;
      const snapshot = retry || useSnapshot ? { ...committed.value } : { ...draft.value, code: draft.value.code.trim() };
      queryError.value = ''; queryState.value = 'loading'; committed.value = snapshot;
      await new Promise(resolve => setTimeout(resolve, effect === 'slow' ? 2400 : 420));
      if (generation !== queryGeneration) return;
      forcedEmpty.value = effect === 'empty'; queryState.value = effect === 'error' ? 'error' : 'success';
      page.value = Math.min(targetPage, pageCount.value);
      nextTick(() => { if (tableScroller.value) tableScroller.value.scrollTop = 0; });
    }
    function resetQuery() { queryGeneration++; draft.value = { code: '', start: '', end: '' }; committed.value = { ...draft.value }; queryState.value = 'success'; forcedEmpty.value = false; queryError.value = ''; page.value = 1; }
    function changePage(value) { if (value >= 1 && value <= pageCount.value) query(true, value, true); }
    function resizePage(size) { pageSize.value = size; page.value = 1; nextTick(() => { if (tableScroller.value) tableScroller.value.scrollTop = 0; }); }
    const downloadApi = ref('daily'), downloadDraft = ref({ date: '2026-08-07', code: '000001.SZ', exchange: 'SSE' }), downloadState = ref('idle'), downloadSnapshot = ref({});
    function selectDownload(value) { downloadApi.value = value; downloadState.value = 'idle'; }
    async function download(retry = false) {
      if (downloadState.value === 'loading') return;
      if (!retry) downloadSnapshot.value = { ...downloadDraft.value, date: downloadApi.value === 'stock_company' ? '' : downloadDraft.value.date, api: downloadApi.value, count: DATA[downloadApi.value].rows.length };
      const effect = retry ? 'success' : scenario.value;
      downloadState.value = 'loading';
      await new Promise(resolve => setTimeout(resolve, effect === 'slow' ? 2600 : 850));
      downloadState.value = ['empty', 'error'].includes(effect) ? effect : 'success';
    }
    function savePreferences() {
      try { localStorage.setItem(preferenceKey, JSON.stringify({ theme: themeHex.value, density: density.value, font: font.value, motion: motion.value })); storageStatus.value = '已保存 · 仅当前浏览器'; }
      catch { storageStatus.value = '仅本次预览 · 浏览器存储不可用'; }
    }
    function applyTheme(value, save = true) {
      if (!/^#[0-9a-f]{6}$/i.test(value)) { themeError.value = '请输入 #RRGGBB 格式的颜色'; return; }
      themeError.value = ''; themeHex.value = value.toLowerCase(); themeDraft.value = value.toUpperCase();
      const palette = { surface: mix(value, '#ffffff', .999), nav: mix(value, '#f5f5f7', .994), raised: mix(value, '#f5f5f7', .996), line: mix(value, '#d2d2d7', .995), 'accent-soft': mix(value, '#ffffff', .965), text: mix(value, '#1d1d1f', .998), muted: mix(value, '#6e6e73', .998) };
      let applied = value;
      for (let i = 0; i <= 100; i++) { applied = mix(value, '#000000', i / 100); if (['#ffffff', palette.surface, palette.nav, palette.raised, palette['accent-soft']].every(color => contrast(applied, color) >= 5.5)) break; }
      palette.accent = applied; themeApplied.value = applied.toUpperCase();
      for (const [key, color] of Object.entries(palette)) document.documentElement.style.setProperty('--' + key, color);
      if (save) savePreferences();
    }
    function setPreference(key, value) { ({ density, font, motion })[key].value = value; savePreferences(); }
    function resetPreferences() { density.value = 'standard'; font.value = 'system'; motion.value = 'standard'; applyTheme('#0066cc'); }
    function go(value) { location.hash = value; }
    function syncRoute() {
      const value = location.hash.slice(1); if (value === 'main') return; route.value = Object.hasOwn(titles, value) ? value : 'datasets';
      document.title = `${titles[route.value]} · Tensor Demo`;
      if (motion.value !== 'reduce' && !prefersReduced.value) nextTick(() => document.querySelector(`.${route.value === 'datasets' ? 'dataset' : route.value === 'downloads' ? 'download' : 'settings'}-page`)?.animate([{ opacity: .25 }, { opacity: 1 }], { duration: 180, easing: 'cubic-bezier(.2,.8,.2,1)' }));
    }
    function openText(title, value) { detailTitle.value = title; detailText.value = value; nextTick(() => textDialog.value.showModal()); }
    function closeOnBackdrop(event) { if (event.target === textDialog.value) { const rect = textDialog.value.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) textDialog.value.close(); } }
    onMounted(() => {
      applyTheme(themeHex.value, false); document.fonts.ready.then(() => fontReady.value++);
      document.fonts.addEventListener('loadingdone', () => fontReady.value++);
      addEventListener('hashchange', syncRoute);
      matchMedia('(prefers-reduced-motion: reduce)').addEventListener('change', event => prefersReduced.value = event.matches);
      document.addEventListener('keydown', event => { if (event.key === 'Escape') document.querySelectorAll('details[open]').forEach(element => element.open = false); });
    });
    return { data, titles, descriptions, route, sources, source, datasetKey, dataset, datasetOptions, downloadOptions, exchanges, pageSizes, scenarios, draft, page, pageSize, pageCount, filtered, pageRows, queryState, queryError, scenario, tableScroller, displayColumns, tableWidth, density, densities, font, motion, showFieldCodes, prefersReduced, swatches, themeHex, themeDraft, themeApplied, themeError, storageStatus, downloadApi, downloadDraft, downloadState, downloadSnapshot, textDialog, detailTitle, detailText, numberParts, numberText, numberStyle, marketClass, selectDataset, query, resetQuery, changePage, resizePage, selectDownload, download, applyTheme, setPreference, resetPreferences, go, openText, closeOnBackdrop };
  },
}).mount('#demo');
