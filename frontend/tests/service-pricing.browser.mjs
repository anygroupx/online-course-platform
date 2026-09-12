import assert from 'node:assert/strict';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';
import { createTestServer } from './fixtures/test-server.mjs';

// Exercise the real confirmation component/API client; every business request is intercepted.
const names = ['exact', 'legacy', 'refund', 'admin', 'daily', 'free', 'invalid', 'long', 'refresh'];
const ids = Object.fromEntries(names.map((name, index) => [name, `91000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`]));
const html = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><div id="app"></div>
<script type="module">
import {createApp,h,ref} from 'vue';import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';import 'element-plus/theme-chalk/dark/css-vars.css';import '/src/styles/variables.scss';import '/src/styles/global.css';import '/src/styles/element-overrides.scss';
import Confirm from '/src/components/ServiceQuoteConfirm.vue';import {getServiceOperation,getAdminServiceOperation} from '/src/api/serviceCommerce.js';import {applyAuthSession} from '/src/utils/authSession.js';
applyAuthSession({token:'test.'+btoa(JSON.stringify({exp:Date.now()/1000+3600,sid:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'}))+'.signature',uid:'20000000-0000-4000-8000-000000000007',role:'USER',isAdmin:false,balance:'10.00'});
window.expireAccessToken=()=>{const now=Date.now();Date.now=()=>now+7200000;};
const ids=${JSON.stringify(ids)};
createApp({setup(){const kind=ref('exact'),quote=ref(null),administrative=ref(false),outcome=ref('');
async function open(){outcome.value='';administrative.value=kind.value==='admin';quote.value=await(administrative.value?getAdminServiceOperation:getServiceOperation)(ids[kind.value]);}
return()=>h('main',{style:'padding:24px'},[h('h1','费用确认回归'),h('label',[h('span','演示情形'),h('select',{'aria-label':'演示情形',value:kind.value,onChange:e=>kind.value=e.target.value},Object.keys(ids).map(k=>h('option',{value:k},k)))]),h('button',{onClick:open},'打开费用预览'),h('p',{role:'status'},outcome.value),h(Confirm,{quote:quote.value,administrative:administrative.value,onClose:()=>quote.value=null,onResult:value=>outcome.value=value.state})]);}}).use(ElementPlus).mount('#app');
</script></body></html>`;
const server = await createTestServer({logLevel:'error',plugins:[{name:'pricing-fixture',configureServer(vite){vite.middlewares.use(async(req,res,next)=>{if(!req.url?.startsWith('/__pricing'))return next();res.setHeader('Content-Type','text/html;charset=utf-8');res.end(await vite.transformIndexHtml(req.url,html));});}}]});
const baseQuote = {orderId:null,action:'CREATE',state:'READY',title:'每次公里计划',quantity:3,quantityUnit:'次',unitCharge:'0.05499989',amount:'0.16',amountLabel:'本次余额扣款',expiresAt:'2099-01-01T00:00:00'};
const quotes = new Map(names.map(name=>[ids[name], {...baseQuote,id:ids[name]}]));
Object.assign(quotes.get(ids.legacy), {unitCharge:'0.05500000',amount:'0.17'});
Object.assign(quotes.get(ids.refund), {action:'REFUND',quantity:6,amount:'0.32',amountLabel:'预计退款上限（按实际核实次数结算）'});
Object.assign(quotes.get(ids.admin), {action:'SETTLE_REFUND',quantity:6,amount:'0.32',amountLabel:'预计退款上限（按实际核实次数结算）'});
Object.assign(quotes.get(ids.daily), {action:'EDIT_SCHEDULE',quantity:4,quantityUnit:'天',unitCharge:'1.23456700',amount:'4.94'});
Object.assign(quotes.get(ids.free), {action:'PAUSE',amount:'0.00',amountLabel:'无需额外扣款'});
Object.assign(quotes.get(ids.invalid), {unitCharge:'5.499989e-2'});
Object.assign(quotes.get(ids.long), {unitCharge:'499850.00995001',amount:'1499550.03'});
const writes = [], reads = [], errors = [], unexpected = [];
let refreshes = 0;
let browser;
try {
  await server.listen();
  const base=`http://127.0.0.1:${server.httpServer.address().port}`;
  browser=await chromium.launch({executablePath:process.env.BROWSER_PATH||(existsSync('/snap/bin/chromium')?'/snap/bin/chromium':undefined),headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const page=await browser.newPage({viewport:{width:1440,height:1080}});page.setDefaultTimeout(20000);
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
    const req=route.request(),url=new URL(req.url());
    if(url.origin!==base){unexpected.push(url.origin);return route.abort();}
    if(!url.pathname.startsWith('/api/'))return route.continue();
    const endpoint=url.pathname.slice(4),method=req.method();
    if(endpoint==='/auth/refresh'&&method==='POST'){
      refreshes++;assert.equal(req.headers()['x-csrf-token'],'fixture-csrf');
      return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:1,data:{
        token:'test.'+Buffer.from(JSON.stringify({exp:Date.now()/1000+14400,sid:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'})).toString('base64')+'.rotated',
        uid:'20000000-0000-4000-8000-000000000007',role:'USER',isAdmin:false,balance:'9.84',
      }})});
    }
    const match=endpoint.match(/^\/(admin\/)?service-order-operations\/([0-9a-f-]+)(?:\/(confirm|settle-refund))?$/);
    if(!match||!quotes.has(match[2])){unexpected.push(`${method} ${endpoint}`);return route.abort();}
    const quote=quotes.get(match[2]);assert.equal(!!match[1],quote.id===ids.admin);
    const respond=()=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:1,data:quote,message:'操作成功'})});
    if(method==='GET'&&!match[3]){reads.push(endpoint);return respond();}
    if(method==='POST'&&match[3]){
      assert.equal(match[3],quote.id===ids.admin?'settle-refund':'confirm');
      assert.deepEqual(JSON.parse(req.postData()||'{}'),{},'the browser sends only the original operation ID, no price or quantity');
      writes.push(endpoint);quote.state='SUCCEEDED';
      if(quote.id===ids.exact||quote.id===ids.refresh)return route.abort('connectionreset');
      return respond();
    }
    unexpected.push(`${method} ${endpoint}`);return route.abort();
  });
  await page.goto(`${base}/__pricing`);
  const dialog=page.getByRole('dialog',{name:'确认本次操作',exact:true});
  async function open(name){await page.getByLabel('演示情形').selectOption(name);await page.getByRole('button',{name:'打开费用预览',exact:true}).click();await dialog.waitFor();}
  async function close(){await dialog.getByRole('button',{name:'返回修改',exact:true}).click();await dialog.waitFor({state:'hidden'});}
  await open('exact');
  await dialog.getByText('¥0.16',{exact:true}).waitFor();
  assert.match(await dialog.locator('[aria-label="费用明细"]').innerText(),/¥0\.05499989 \/ 次/);
  assert.match(await dialog.innerText(),/总额保留两位小数/);
  assert.equal(writes.length,0);await close();assert.equal(writes.length,0);
  await open('exact');
  const out=path.resolve('../.cache/native-service-ui');mkdirSync(out,{recursive:true});
  await page.screenshot({path:`${out}/pricing-confirm-desktop.png`,fullPage:true,animations:'disabled'});
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>document.documentElement.classList.add('dark'));
  for(const button of await dialog.locator('.el-dialog__footer .el-button').all())assert.ok((await button.boundingBox()).height>=44);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:`${out}/pricing-confirm-mobile-dark.png`,fullPage:true,animations:'disabled'});
  await dialog.getByRole('button',{name:'确认并下单',exact:true}).click();
  await dialog.getByRole('button',{name:'检查提交结果',exact:true}).waitFor();
  assert.equal(writes.length,1);assert.equal(await dialog.getByRole('button',{name:'确认并下单',exact:true}).count(),0);
  assert.match(await dialog.locator('[aria-label="费用明细"]').innerText(),/0\.05499989/);
  await dialog.getByRole('button',{name:'检查提交结果',exact:true}).click();await dialog.waitFor({state:'hidden'});
  assert.equal(writes.length,1);assert.ok(reads.filter(p=>p.endsWith(ids.exact)).length===3);
  await open('legacy');assert.match(await dialog.innerText(),/0\.05500000/);assert.match(await dialog.innerText(),/¥0\.17/);await close();
  await open('refund');assert.match(await dialog.innerText(),/¥0\.32/);assert.match(await dialog.innerText(),/可退余额限制/);assert.ok(!(await dialog.innerText()).includes('¥0.33'));await close();
  await open('admin');assert.match(await dialog.innerText(),/0\.05499989/);assert.match(await dialog.innerText(),/可退余额限制/);
  await page.screenshot({path:`${out}/pricing-refund-mobile-dark.png`,fullPage:true,animations:'disabled'});
  await dialog.getByRole('button',{name:'确认退款入账',exact:true}).click();await dialog.waitFor({state:'hidden'});
  assert.equal(writes.length,2);assert.ok(writes[1].endsWith('/settle-refund'));
  await open('daily');assert.match(await dialog.locator('[aria-label="费用明细"]').innerText(),/1\.23456700 \/ 天/);assert.match(await dialog.innerText(),/4 天/);await close();
  for(const name of ['free','invalid']){await open(name);assert.equal(await dialog.locator('[aria-label="费用明细"]').count(),0);await close();}
  await open('long');assert.match(await dialog.innerText(),/499850\.00995001/);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await close();
  // Expiry itself does not mutate the session ref. The request interceptor rotates it while
  // the original confirmation is in flight; that must not close the dialog or lose its ID.
  await open('refresh');
  await page.context().addCookies([{name:'course_csrf',value:'fixture-csrf',url:base}]);
  await page.evaluate(()=>window.expireAccessToken());
  await dialog.getByRole('button',{name:'确认并下单',exact:true}).click();
  await dialog.getByRole('button',{name:'检查提交结果',exact:true}).waitFor();
  assert.equal(refreshes,1);assert.equal(writes.length,3);
  assert.ok(writes.at(-1).includes(ids.refresh));
  assert.equal(await dialog.getByRole('button',{name:'确认并下单',exact:true}).count(),0);
  await dialog.getByRole('button',{name:'检查提交结果',exact:true}).click();
  await dialog.waitFor({state:'hidden'});
  assert.equal(writes.length,3,'token renewal and lost-response recovery must not replay the write');
  assert.equal(reads.at(-1).split('/').at(-1),ids.refresh);
  assert.equal(await page.getByRole('status').innerText(),'SUCCEEDED');
  const storage=await page.evaluate(()=>JSON.stringify([Object.entries(localStorage),Object.entries(sessionStorage)]));
  for(const marker of [ids.exact,ids.admin,'0.05499989','499850.00995001'])assert.ok(!storage.includes(marker));
  assert.deepEqual(errors,[]);assert.deepEqual(unexpected,[]);
  console.log('PASS exact service pricing: real Vue quote details, eight-decimal strings, legacy prices, capped admin refund, service-day units, invalid/free suppression, lost confirmations across token renewal and original GET-only recovery, 44px mobile dark/long values, no business storage; mocked APIs only');
} finally {if(browser)await browser.close();await server.close();}
