import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as metadata from '../api/dataSources.js'
import * as integrity from '../api/integrityChecks.js'
import { ApiError } from '../api/errors.js'
import IntegrityView from './IntegrityView.vue'

vi.mock('../api/dataSources.js', () => ({ listDataSources: vi.fn(), listApis: vi.fn() }))
vi.mock('../api/integrityChecks.js', async (original) => ({ ...(await original()), getIntegrityCapabilities: vi.fn(), submitIntegrityCheck: vi.fn(), listIntegrityChecks: vi.fn(), getIntegrityCheck: vi.fn() }))

const CHECK_ID='33333333-3333-4333-8333-333333333333'
const OTHER_CHECK_ID='44444444-4444-4444-8444-444444444444'
const source={pluginId:'tushare_pro',displayName:'Tushare Pro',enabled:true,downloadAvailable:false,credentialConfigured:false,unavailableReason:'缺Token'}
const api=(apiName,scopeKind='STOCK_DATE')=>({apiName,displayName:apiName,descriptor:scopeKind?{scopeKind,dateLabel:'交易日期',dateField:'trade_date',datasetKey:{pluginId:'tushare_pro',apiName},dependencies:[],limitations:[],rules:[]}:null})
const capability=()=>({pluginId:'tushare_pro',localCheckAvailable:true,unavailableReason:null,capabilityHash:'a'.repeat(64),limits:{maxSymbols:10,maxRangeDays:100,maxUnits:100},apis:[api('daily'),api('calendar','NON_STOCK'),api('unknown',null)]})
const page=(items=[])=>({page:1,pageSize:20,total:BigInt(items.length),items})
function deferred(){let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject}}
async function mounted(path='/integrity'){
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/integrity',name:'integrity',component:{template:'<div />'}},{path:'/integrity/checks/:checkId',name:'integrity-check',component:{template:'<div />'}}]})
  await router.push(path);await router.isReady()
  const wrapper=mount(IntegrityView,{global:{plugins:[router]}});await flushPromises();return{wrapper,router}
}
beforeEach(()=>{
  vi.resetAllMocks();sessionStorage.clear();vi.spyOn(globalThis.crypto,'randomUUID').mockReturnValue('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
  metadata.listDataSources.mockResolvedValue([source]);metadata.listApis.mockResolvedValue([{apiName:'daily',category:'行情'}])
  integrity.getIntegrityCapabilities.mockResolvedValue(capability());integrity.listIntegrityChecks.mockResolvedValue(page())
  integrity.getIntegrityCheck.mockResolvedValue({ checkId: CHECK_ID, scope: { pluginId: 'tushare_pro', symbols: ['600000.SH'], startDate: '2026-09-01', endDate: '2026-09-02', apiNames: ['daily'] } })
  integrity.submitIntegrityCheck.mockImplementation(async(request)=>({checkId:CHECK_ID,submissionId:request.submissionId,pluginId:request.pluginId,status:'QUEUED'}))
})

it('auto-selects the only source, defaults all capability APIs, and keeps local checks available without a token',async()=>{
  metadata.listApis.mockRejectedValueOnce(Object.assign(new Error('category'),{requestId:'category-request'}))
  const{wrapper}=await mounted()
  expect(wrapper.text()).toContain('下载不可用，本地检查可用')
  expect(wrapper.text()).toContain('接口分类获取失败')
  expect(wrapper.findAll('input[type="checkbox"]').filter((item)=>['daily','calendar','unknown'].some((name)=>item.element.parentElement.textContent.includes(name))).every((item)=>item.element.checked)).toBe(true)
})

it('counts and submits unadded draft symbols once after confirmation',async()=>{
  const{wrapper,router}=await mounted()
  await wrapper.get('[aria-label="股票代码"]').setValue('999999.sz，999999.SZ 600000.sh')
  await wrapper.get('[aria-label="开始日期"]').setValue('2026-09-01')
  await wrapper.get('[aria-label="结束日期"]').setValue('2026-09-02')
  expect(wrapper.text()).toContain('计划单元数')
  await wrapper.get('[aria-label="我已确认以上检查口径"]').setValue(true)
  await wrapper.get('button[aria-label="开始检查"]').trigger('click')
  await flushPromises()
  expect(integrity.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  expect(integrity.submitIntegrityCheck.mock.calls[0][0].symbols).toEqual(['999999.SZ','600000.SH'])
  expect(router.currentRoute.value.fullPath).toBe(`/integrity/checks/${CHECK_ID}`)
})

it('retries a prepared request after storage failure without generating another submission',async()=>{
  const write=vi.spyOn(Storage.prototype,'setItem').mockImplementationOnce(()=>{throw new Error('quota')})
  const{wrapper}=await mounted()
  await wrapper.get('[aria-label="股票代码"]').setValue('600000.SH');await wrapper.get('[aria-label="开始日期"]').setValue('2026-09-01');await wrapper.get('[aria-label="结束日期"]').setValue('2026-09-01');await wrapper.get('[aria-label="我已确认以上检查口径"]').setValue(true)
  await wrapper.get('button[aria-label="开始检查"]').trigger('click');await flushPromises()
  const firstId=wrapper.vm.flow.pendingSubmission.value.submissionId
  expect(integrity.submitIntegrityCheck).not.toHaveBeenCalled()
  expect(wrapper.get('[aria-label="股票代码"]').attributes('disabled')).toBeDefined()
  write.mockRestore();await wrapper.get('button[aria-label="开始检查"]').trigger('click');await flushPromises()
  expect(integrity.submitIntegrityCheck.mock.calls[0][0].submissionId).toBe(firstId)
})

it('keeps pending recovery and its original scope visible when source metadata is empty',async()=>{
  const request={submissionId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',pluginId:'tushare_pro',capabilityHash:'a'.repeat(64),symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-01',apiNames:['daily']}
  sessionStorage.setItem('tensor.integrityChecks.pending.v1',JSON.stringify({schemaVersion:1,request}))
  metadata.listDataSources.mockResolvedValue([])
  integrity.listIntegrityChecks.mockImplementation(async(criteria)=>criteria.submissionId?Promise.reject(Object.assign(new Error('offline'),{requestId:'recover-request'})):page())
  const{wrapper}=await mounted()
  expect(wrapper.text()).toContain(request.submissionId)
  expect(wrapper.text()).toContain('600000.SH')
  expect(wrapper.get('button[aria-label="查询提交结果"]')).toBeDefined()
  expect(wrapper.text()).toContain('recover-request')
})

it('defaults the newly selected source to its own capability APIs',async()=>{
  const other={...source,pluginId:'fixture',displayName:'Fixture'}
  metadata.listDataSources.mockResolvedValue([source,other])
  integrity.getIntegrityCapabilities.mockImplementation(async(pluginId)=>({...capability(),pluginId,apis:pluginId==='fixture'?[api('fixture_daily')]:[api('daily')]}))
  const{wrapper}=await mounted()
  await wrapper.get('[aria-label="数据源"]').setValue('tushare_pro');await flushPromises()
  await wrapper.get('[aria-label="数据源"]').setValue('fixture');await flushPromises()
  expect(wrapper.text()).toContain('fixture_daily')
  expect(wrapper.text()).not.toContain('此接口已失效')
})

it('does not reselect cleared APIs when a late category response arrives',async()=>{
  const other={...source,pluginId:'fixture',displayName:'Fixture'}
  const categories=deferred()
  metadata.listDataSources.mockResolvedValue([source,other])
  metadata.listApis.mockReturnValueOnce(categories.promise)
  const{wrapper}=await mounted()
  await wrapper.get('[aria-label="数据源"]').setValue('tushare_pro')
  await flushPromises()
  await wrapper.get('button[aria-label="清空"]').trigger('click')
  categories.resolve([{apiName:'daily',category:'行情'}])
  await flushPromises()
  expect(wrapper.text()).toContain('已选择 0 个')
})

it('recovers a pending snapshot by GET on mount without loading capability or posting',async()=>{
  const request={submissionId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',pluginId:'tushare_pro',capabilityHash:'a'.repeat(64),symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-01',apiNames:['daily']}
  sessionStorage.setItem('tensor.integrityChecks.pending.v1',JSON.stringify({schemaVersion:1,request}))
  integrity.listIntegrityChecks.mockImplementation(async(criteria)=>criteria.submissionId?page([{checkId:CHECK_ID,submissionId:request.submissionId}]):page())
  const{router}=await mounted()
  expect(integrity.submitIntegrityCheck).not.toHaveBeenCalled()
  expect(integrity.getIntegrityCapabilities).not.toHaveBeenCalled()
  expect(router.currentRoute.value.fullPath).toBe(`/integrity/checks/${CHECK_ID}`)
})

it('copies the saved scope by reference, refreshes current capability, and does not select newly added APIs',async()=>{
  integrity.getIntegrityCapabilities.mockResolvedValue({...capability(),apis:[api('daily'),api('new_api')]})
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  expect(integrity.getIntegrityCheck).toHaveBeenCalledWith(CHECK_ID,expect.objectContaining({signal:expect.any(AbortSignal)}))
  expect(integrity.getIntegrityCapabilities).toHaveBeenCalledWith('tushare_pro',expect.anything())
  expect(wrapper.get('[aria-label="股票代码"]').element.value).toBe('')
  expect(wrapper.text()).toContain('600000.SH')
  const boxes=wrapper.findAll('input[type="checkbox"]')
  expect(boxes.find((box)=>box.element.parentElement.textContent.includes('daily')).element.checked).toBe(true)
  expect(boxes.find((box)=>box.element.parentElement.textContent.includes('new_api')).element.checked).toBe(false)
  expect(wrapper.get('[aria-label="我已确认以上检查口径"]').element.checked).toBe(false)
  expect(integrity.submitIntegrityCheck).not.toHaveBeenCalled()
})

it('gives pending recovery priority over a fromCheckId copy',async()=>{
  const request={submissionId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',pluginId:'tushare_pro',capabilityHash:'a'.repeat(64),symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-01',apiNames:['daily']}
  sessionStorage.setItem('tensor.integrityChecks.pending.v1',JSON.stringify({schemaVersion:1,request}))
  integrity.listIntegrityChecks.mockRejectedValue(Object.assign(new Error('offline'),{requestId:'recover-request'}))
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  expect(integrity.getIntegrityCheck).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('请先确认上一次检查的提交结果，再从报告发起新检查')
})

it('shows copy failure request ids and retries only the report GET',async()=>{
  integrity.getIntegrityCheck.mockRejectedValueOnce(Object.assign(new Error('offline'),{requestId:'copy-request'}))
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  expect(wrapper.text()).toContain('copy-request')
  await wrapper.get('button[aria-label="重新读取原条件"]').trigger('click');await flushPromises()
  expect(integrity.getIntegrityCheck).toHaveBeenCalledTimes(2)
  expect(integrity.submitIntegrityCheck).not.toHaveBeenCalled()
})

it('does not let a late report copy overwrite user edits',async()=>{
  const pending=deferred();integrity.getIntegrityCheck.mockReturnValueOnce(pending.promise)
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  await wrapper.get('[aria-label="股票代码"]').setValue('999999.SZ')
  pending.resolve({checkId:CHECK_ID,scope:{pluginId:'tushare_pro',symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-02',apiNames:['daily']}})
  await flushPromises()
  expect(wrapper.text()).not.toContain('600000.SH')
  expect(integrity.getIntegrityCapabilities).not.toHaveBeenCalled()
})

it('blocks a copied scope whose saved source is now disabled',async()=>{
  metadata.listDataSources.mockResolvedValue([{...source,enabled:false}])
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  expect(wrapper.text()).toContain('原数据源目前不可用，请选择新的数据源')
  expect(wrapper.get('[aria-label="开始检查"]').attributes('disabled')).toBeDefined()
  expect(wrapper.text()).toContain('600000.SH')
})

it('ignores a late copy after fromCheckId changes',async()=>{
  const old=deferred(),current=deferred()
  integrity.getIntegrityCheck.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
  const{wrapper,router}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  await router.push(`/integrity?fromCheckId=${OTHER_CHECK_ID}`)
  current.resolve({checkId:OTHER_CHECK_ID,scope:{pluginId:'tushare_pro',symbols:['000001.SZ'],startDate:'2026-09-03',endDate:'2026-09-04',apiNames:['daily']}})
  await flushPromises()
  old.resolve({checkId:CHECK_ID,scope:{pluginId:'tushare_pro',symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-02',apiNames:['daily']}})
  await flushPromises()
  expect(wrapper.text()).toContain('000001.SZ')
  expect(wrapper.text()).not.toContain('600000.SH')
})

it('accepts an uppercase UUID without leaving the copy request loading',async()=>{
  const uppercase='ABCDEF12-3456-4ABC-8DEF-1234567890AB'
  const{wrapper}=await mounted(`/integrity?fromCheckId=${uppercase}`)
  expect(integrity.getIntegrityCheck).toHaveBeenCalledWith(uppercase.toLowerCase(),expect.anything())
  expect(wrapper.text()).toContain('已复制原报告的固定执行范围')
  expect(wrapper.text()).not.toContain('正在读取原报告条件')
})

it('clears confirmation and blocks the old scope when a replacement report fails',async()=>{
  const{wrapper,router}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  await wrapper.get('[aria-label="我已确认以上检查口径"]').setValue(true)
  expect(wrapper.get('[aria-label="我已确认以上检查口径"]').element.checked).toBe(true)
  integrity.getIntegrityCheck.mockRejectedValueOnce(Object.assign(new Error('missing'),{requestId:'replacement-request'}))
  await router.push(`/integrity?fromCheckId=${OTHER_CHECK_ID}`);await flushPromises()
  expect(wrapper.text()).toContain('replacement-request')
  expect(wrapper.text()).not.toContain('600000.SH')
  expect(wrapper.get('[aria-label="我已确认以上检查口径"]').element.checked).toBe(false)
  expect(wrapper.get('[aria-label="开始检查"]').attributes('disabled')).toBeDefined()
})

it('lets an unavailable copied source be explicitly replaced and submitted through the normal flow',async()=>{
  const replacement={...source,pluginId:'fixture',displayName:'Fixture',enabled:true}
  metadata.listDataSources.mockResolvedValue([replacement])
  integrity.getIntegrityCapabilities.mockImplementation(async(pluginId)=>pluginId==='fixture'
    ?{...capability(),pluginId:'fixture',apis:[api('fixture_daily')]}:capability())
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  expect(wrapper.text()).toContain('原数据源目前停用或不存在')
  await wrapper.get('[aria-label="数据源"]').setValue('fixture');await flushPromises()
  expect(wrapper.text()).toContain('fixture_daily')
  await wrapper.get('[aria-label="我已确认以上检查口径"]').setValue(true)
  await wrapper.get('button[aria-label="开始检查"]').trigger('click');await flushPromises()
  expect(integrity.submitIntegrityCheck).toHaveBeenCalledTimes(1)
  expect(integrity.submitIntegrityCheck.mock.calls[0][0]).toMatchObject({pluginId:'fixture',symbols:['600000.SH'],apiNames:['fixture_daily']})
})

it('blocks and exposes a rejected-pending copy attempt while loading and after failure',async()=>{
  const request={submissionId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',pluginId:'tushare_pro',capabilityHash:'a'.repeat(64),symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-01',apiNames:['daily']}
  sessionStorage.setItem('tensor.integrityChecks.pending.v1',JSON.stringify({schemaVersion:1,request}))
  integrity.listIntegrityChecks.mockResolvedValue(page())
  integrity.submitIntegrityCheck.mockRejectedValue(new ApiError({requestId:'rejected-request',code:'PARAM_INVALID',message:'rejected',retryable:false,fieldErrors:[]}))
  const{wrapper}=await mounted(`/integrity?fromCheckId=${CHECK_ID}`)
  await wrapper.get('button[aria-label="使用原请求重发"]').trigger('click');await flushPromises()
  const copy=deferred();integrity.getIntegrityCheck.mockReturnValueOnce(copy.promise)
  await wrapper.get('button[aria-label="读取这份报告的条件"]').trigger('click');await flushPromises()
  expect(wrapper.text()).toContain('正在读取原报告条件')
  expect(wrapper.get('[aria-label="我已确认以上检查口径"]').attributes('disabled')).toBeDefined()
  copy.reject(Object.assign(new Error('copy failed'),{requestId:'rejected-copy-request'}));await flushPromises()
  expect(wrapper.text()).toContain('rejected-copy-request')
  expect(wrapper.get('[aria-label="开始检查"]').attributes('disabled')).toBeDefined()
})

it('clears and blocks a rejected pending scope before rejecting an invalid referenced id',async()=>{
  const request={submissionId:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',pluginId:'tushare_pro',capabilityHash:'a'.repeat(64),symbols:['600000.SH'],startDate:'2026-09-01',endDate:'2026-09-01',apiNames:['daily']}
  sessionStorage.setItem('tensor.integrityChecks.pending.v1',JSON.stringify({schemaVersion:1,request}))
  integrity.listIntegrityChecks.mockResolvedValue(page())
  integrity.submitIntegrityCheck.mockRejectedValue(new ApiError({requestId:'rejected-request',code:'PARAM_INVALID',message:'rejected',retryable:false,fieldErrors:[]}))
  const{wrapper}=await mounted('/integrity?fromCheckId=invalid-id')
  await wrapper.get('button[aria-label="使用原请求重发"]').trigger('click');await flushPromises()
  await wrapper.vm.flow.loadCapabilities('tushare_pro');await flushPromises()
  await wrapper.get('[aria-label="我已确认以上检查口径"]').setValue(true)
  expect(wrapper.get('[aria-label="开始检查"]').attributes('disabled')).toBeUndefined()
  await wrapper.get('button[aria-label="读取这份报告的条件"]').trigger('click');await flushPromises()
  expect(wrapper.text()).toContain('原检查编号无效')
  expect(wrapper.find('.chips').text()).not.toContain('600000.SH')
  expect(wrapper.get('[aria-label="我已确认以上检查口径"]').element.checked).toBe(false)
  expect(wrapper.get('[aria-label="开始检查"]').attributes('disabled')).toBeDefined()
})
