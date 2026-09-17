import assert from 'node:assert/strict'
import { existsSync, mkdirSync } from 'node:fs'
import { chromium } from 'playwright'
import { createTestServer as createServer } from './fixtures/test-server.mjs';const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="margin:0;padding:16px"><div id="app"></div><script type="module">
import {createApp,ref,h} from 'vue';import {createPinia} from 'pinia';import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Usage from '/src/components/projectcenter/ProjectUsage.vue';import Clients from '/src/views/ProjectClients.vue';import Admin from '/src/views/AdminProjectCenter.vue';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600}))+'.signature',userId:7,isAdmin:true});
const entry=new URLSearchParams(location.search).get('entry');const component=entry==='owner'?Clients:entry==='admin'?Admin:{setup(){const admin=ref(false),visible=ref(true);return ()=>h('div',[
  h('nav',{style:'display:flex;gap:12px;margin-bottom:20px'},[
    h('button',{onClick:()=>{admin.value=false;visible.value=true}},'经营者视图'),
    h('button',{onClick:()=>{admin.value=true;visible.value=true}},'管理员视图'),
    h('button',{onClick:()=>{visible.value=false}},'关闭统计')]),visible.value?h(Usage,{admin:admin.value}):null])}};
createApp(component).use(createPinia()).use(ElementPlus).mount('#app');</script></body></html>`
const server = await createServer({ logLevel: 'error', server: { host: '127.0.0.1', port: 0 }, plugins: [{ name: 'project-report-fixture', configureServer(vite) { vite.middlewares.use(async (req,res,next) => {
  if (!req.url?.startsWith('/__project_reporting')) return next()
  res.setHeader('Content-Type','text/html;charset=utf-8');res.end(await vite.transformIndexHtml(req.url,html))
}) } }] })
const calls={total:120,failed:10,last24Hours:46,failedLast24Hours:3,actionKinds:3}
const localFunding={settledOperations:25,debited:'320.00',returned:'60.00',netDebited:'260.00',unresolvedOperations:0}
const owner={window:{from:'2026-09-08 22:00:00',through:'2026-09-09 22:00:00',timezone:'Asia/Shanghai'},calls,
  actions:[{action:'SELF',total:80,failed:8,last24Hours:30,failedLast24Hours:2},{action:'TICKETS',total:30,failed:2,last24Hours:10,failedLast24Hours:1},{action:'USAGE',total:10,failed:0,last24Hours:6,failedLast24Hours:0}],moreActions:false,
  clients:{total:22,active:21,suspended:1,closed:0,projects:21},tickets:{total:3,open:1,inProgress:1,resolved:1,closed:0,pendingCompensation:1},localFunding}
const global={...owner,publishedProjects:21,activeUpstreamBindings:3,activeUpstreamOwners:2,activeLocalOwners:1,
  upstreamFunding:{settledOperations:14,debited:'1260.11',returned:'260.01',netDebited:'1000.10',unresolvedOperations:2}}
const projects=Array.from({length:21},(_,i)=>({projectId:i+1,title:i===0?'项目甲 · 独立实习额度':i===1?'项目乙 · 周期服务次数':`本地项目 ${i+1}`,customers:i===0?2:1,active:1,activeUnits:i===0?'120.000001':i===1?'8':'0',suspendedUnits:i===0?'20':'0',refundBudget:i===0?'35.00':i===1?'24.00':'0.00'}))
let browser, failOwner=false, denyAdmin=false, malformedAdmin=false, failPage=false, empty=false, holdOwner=false, releaseOwner, ownerHeld
let reads=[],writes=[],errors=[],unexpected=[]
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
const listing=(records,total=records.length,current=1)=>({records,total,current,size:20})
try {
  await server.listen();const base=server.resolvedUrls.local[0].replace(/\/$/,'')
  browser=await chromium.launch({executablePath:existsSync('/snap/bin/chromium')?'/snap/bin/chromium':undefined,args:['--no-sandbox','--disable-dev-shm-usage']})
  const context=await browser.newContext({viewport:{width:1440,height:1100},timezoneId:'America/Los_Angeles'})
  await context.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url())
    if(url.origin!==new URL(base).origin){unexpected.push(url.origin);return route.abort()}
    if(!url.pathname.startsWith('/api/'))return route.continue()
    const path=url.pathname.slice(4);if(req.method()!=='GET'){writes.push(path);return route.abort()}
    reads.push(path);assert.ok(req.headers().authorization?.startsWith('Bearer '))
    const ok=async data=>{try{await route.fulfill({json:{code:1,success:true,data},headers:{'Cache-Control':'no-store'}})}catch{}}
    if(path==='/project-clients/usage'){
      if(holdOwner){ownerHeld.resolve();await releaseOwner.promise}
      if(failOwner)return route.fulfill({status:500,json:{code:-1,message:'模拟统计读取失败'}})
      if(empty)return ok({...owner,calls:{total:0,failed:0,last24Hours:0,failedLast24Hours:0,actionKinds:0},actions:[],clients:{total:0,active:0,suspended:0,closed:0,projects:0},tickets:{total:0,open:0,inProgress:0,resolved:0,closed:0,pendingCompensation:0},localFunding:{settledOperations:0,debited:'0.00',returned:'0.00',netDebited:'0.00',unresolvedOperations:0}})
      return ok(owner)
    }
    if(path==='/project-clients/usage/projects'){
      const page=Number(url.searchParams.get('page'));assert.equal(url.searchParams.get('pageSize'),'20')
      if(failPage&&page===2)return route.fulfill({status:500,json:{code:-1,message:'模拟项目页读取失败'}})
      return ok(listing(empty?[]:projects.slice((page-1)*20,page*20),empty?0:21,page))
    }
    if(path==='/admin/project-reports/overview'){
      if(denyAdmin)return route.fulfill({status:403,json:{code:403,message:'拒绝访问'}})
      return ok(malformedAdmin?{...global,upstreamFunding:undefined}:global)
    }
    if(['/project-clients','/project-clients/catalog','/project-client-operations','/admin/service-projects'].includes(path))return ok(listing([]))
    if(path==='/project-clients/stats')return ok({customers:0,active:0,appliedOperations:0,totalDebited:'0.00',totalReturned:'0.00'})
    unexpected.push(path);return route.abort()
  })
  const page=await context.newPage();page.setDefaultTimeout(45000);page.setDefaultNavigationTimeout(90000);page.on('pageerror',e=>errors.push(e.message))
  await page.goto(base+'/__project_reporting')
  await page.getByText('120.000001',{exact:true}).waitFor()
  await page.getByText('滚动 24 小时',{exact:false}).waitFor()
  await page.getByText('2026-09-08 22:00:00',{exact:false}).waitFor()
  await page.getByText('2026-09-09 22:00:00',{exact:false}).waitFor()
  assert.equal(await page.getByText('项目账户兑换',{exact:true}).count(),0)
  mkdirSync('../.cache/native-service-ui',{recursive:true})
  await page.screenshot({path:'../.cache/native-service-ui/project-usage-owner-desktop.png',animations:'disabled'})
  failPage=true
  await page.getByRole('button',{name:'Go to next page'}).click()
  await page.getByText('项目额度读取失败；请重试本页，未显示伪零余额。',{exact:true}).waitFor()
  assert.equal(await page.getByRole('heading',{name:'本地项目 21'}).count(),0)
  failPage=false;await page.getByRole('button',{name:'重试本页'}).click()
  await page.getByRole('heading',{name:'本地项目 21'}).waitFor()
  failOwner=true;await page.getByRole('button',{name:'刷新只读统计'}).click()
  await page.getByText('统计读取失败或数据不完整；不会将失败显示为零。请重试只读请求。',{exact:true}).waitFor()
  assert.equal(await page.locator('.metrics').count(),0)
  failOwner=false;empty=true;await page.getByRole('button',{name:'刷新只读统计'}).click()
  await page.getByText('暂无已记录的 API 调用',{exact:true}).waitFor();await page.getByText('暂无客户项目',{exact:true}).waitFor()
  empty=false
  denyAdmin=true;await page.getByRole('button',{name:'管理员视图',exact:true}).click()
  await page.getByText('需要接口管理与资金核对双权限；未读取任何全局统计。',{exact:true}).waitFor()
  assert.equal(await page.getByRole('heading',{name:'平台人民币流向'}).count(),0)
  denyAdmin=false;malformedAdmin=true;await page.getByRole('button',{name:'刷新只读统计'}).click()
  await page.getByText('统计读取失败或数据不完整；不会将失败显示为零。请重试只读请求。',{exact:true}).waitFor()
  malformedAdmin=false;await page.getByRole('button',{name:'刷新只读统计'}).click()
  await page.getByRole('heading',{name:'项目账户兑换'}).waitFor()
  await page.getByText('¥1000.10',{exact:true}).waitFor();await page.getByText('¥260.00',{exact:true}).waitFor()
  await page.getByText('2 笔操作仍在处理中或结果未知',{exact:false}).waitFor()
  await page.setViewportSize({width:390,height:960});await page.evaluate(()=>document.documentElement.classList.add('dark'))
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1))
  await page.waitForFunction(()=>document.querySelectorAll('.el-message').length===0)
  await page.screenshot({path:'../.cache/native-service-ui/project-usage-admin-mobile-dark.png',fullPage:true,animations:'disabled'})
  releaseOwner=deferred();ownerHeld=deferred();holdOwner=true
  await page.getByRole('button',{name:'经营者视图',exact:true}).click();await ownerHeld.promise
  await page.getByRole('button',{name:'管理员视图',exact:true}).click();await page.getByRole('heading',{name:'项目账户兑换'}).waitFor()
  holdOwner=false;releaseOwner.resolve();await page.getByRole('button',{name:'关闭统计',exact:true}).click()
  assert.equal(await page.locator('.project-usage').count(),0)
  await page.goto(base+'/__project_reporting?entry=owner')
  await page.getByRole('button',{name:'用量与统计',exact:true}).click()
  await page.getByRole('dialog',{name:'项目用量与统计'}).getByText('120.000001',{exact:true}).waitFor()
  await page.getByRole('dialog',{name:'项目用量与统计'}).getByRole('button',{name:'Close this dialog'}).click()
  await page.goto(base+'/__project_reporting?entry=admin')
  const before=reads.filter(x=>x==='/admin/service-projects').length
  await page.getByRole('tab',{name:'运营统计',exact:true}).click()
  await page.getByRole('heading',{name:'项目账户兑换'}).waitFor()
  assert.equal(reads.filter(x=>x==='/admin/service-projects').length,before)
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1))
  const stored=await page.evaluate(()=>JSON.stringify({...localStorage,...sessionStorage}))
  for(const forbidden of ['1260.11','120.000001','upstreamFunding','secret'])assert.ok(!stored.includes(forbidden))
  assert.deepEqual(writes,[]);assert.deepEqual(unexpected,[]);assert.deepEqual(errors,[])
  console.log('PASS project reports: owner-only usage, explicit rolling window, paged separate units, exact money strings, admin dual-permission feedback, unknown excluded, error/empty/malformed/stale results, real page entries and mobile dark; all APIs simulated, GET only.')
} finally {await browser?.close();await server.close()}
