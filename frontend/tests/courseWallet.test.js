import assert from 'node:assert/strict';
import test from 'node:test';
import { transferSummary, purchaseSummary, courseMoney, formatRecordedCourseMoney, orderFundingLabels, createOrderRequestKeys, createWalletTransferRequestKeys } from '../src/utils/courseWallet.js';
import { canAccessPath } from '../src/utils/routePermissions.js';

test('转入按分计算并保持普通与专户资金守恒', () => {
  assert.deepEqual(transferSummary('500.00','200.00','7.20','IN'), { amount:'7.20', generalBefore:'500.00', generalAfter:'492.80', walletBefore:'200.00', walletAfter:'207.20' });
  assert.equal(transferSummary('0.10','0.20','0.01','OUT').generalAfter,'0.11');
});
test('非法金额、超精度和余额不足无法进入确认', () => {
  for (const amount of ['0','-1','1.001','10000000000.00','abc','1e2']) assert.throws(() => transferSummary('500','200',amount,'IN'));
  assert.throws(() => transferSummary('5','200','6','IN')); assert.throws(() => transferSummary('5','200','201','OUT'));
});
test('服务端专属价用于批量确认，不允许专户不足回退', () => {
  const preview={ finalPrice:'7.20', priceSource:'PRIVATE', paymentSource:'COURSE_WALLET', walletBalance:'200', canPurchase:true, contractVersion:2 };
  const result=purchaseSummary(preview,3); assert.equal(result.total,'21.60'); assert.equal(result.after,'178.40'); assert.equal(result.paymentLabel,'课程专户');
  assert.throws(() => purchaseSummary({...preview,canPurchase:false,unavailableReason:'专户不足'},1));
  assert.throws(() => purchaseSummary({...preview,walletBalance:'7.20'},2)); assert.throws(() => purchaseSummary(null,1));
});
test('历史订单按普通余额展示，专户退款标识原来源', () => {
  assert.deepEqual(orderFundingLabels({}),{ price:'普通费率价',payment:'普通余额',refund:'普通余额' });
  assert.equal(orderFundingLabels({priceSource:'PRIVATE',paymentSource:'COURSE_WALLET'}).refund,'原课程专户');
  assert.equal(courseMoney('9999999999.99'),'9999999999.99');
});
test('财务与审计菜单权限不授予运营密价写权限', () => {
  assert.equal(canAccessPath({permissions:['course-wallet:release']},'/admin/course-wallets'),true);
  assert.equal(canAccessPath({permissions:['course-private-price:read']},'/admin/course-private-contracts'),true);
  assert.equal(canAccessPath({permissions:['platform:update','user:update']},'/admin/course-wallets'),false);
});
test('相同语义请求在网络未知结果重试时保留原请求键', async () => {
  const keys=createOrderRequestKeys(); const data={platformId:9,studentAccount:'agent',studentPassword:'hidden',courseId:'x',courseName:'X'};
  assert.equal(await keys.forOrder(data),await keys.forOrder({...data})); assert.notEqual(await keys.forOrder(data),await keys.forOrder({...data,courseId:'y'}));
});
test('累计统计允许超过余额上限，保持字符串精度且不虚构缺失值', () => {
  assert.equal(formatRecordedCourseMoney('10000000000000.10'),'10000000000000.10');
  assert.equal(formatRecordedCourseMoney('0'),'0.00');
  assert.equal(formatRecordedCourseMoney(null),'—');
  assert.equal(formatRecordedCourseMoney('1.001'),'—');
});
test('明确成功后开启新的合法购买尝试，旧响应不能清除新尝试', async () => {
  const keys=createOrderRequestKeys(); const data={platformId:9,studentAccount:'agent',studentPassword:'hidden',courseId:'x',courseName:'X'};
  const first=await keys.forOrder(data); assert.equal(await keys.forOrder({...data}),first);
  await keys.confirmed(data,first); const next=await keys.forOrder(data); assert.notEqual(first,next);
  await keys.confirmed(data,first); assert.equal(await keys.forOrder(data),next);
});

test('未知结果划转在弹窗重开或改金额后保留原请求键，避免另一笔入账', () => {
  const keys=createWalletTransferRequestKeys();
  const first=keys.forTransfer(5,'IN','10');
  assert.equal(keys.forTransfer('5','IN','10.00'),first);
  assert.notEqual(keys.forTransfer(6,'IN','10'),first);
  assert.notEqual(keys.forTransfer(5,'OUT','10'),first);
  assert.equal(keys.forTransfer(5,'IN','11'),first);
  assert.throws(()=>keys.forTransfer(5,'IN','0'));
  assert.throws(()=>keys.forTransfer(5,'IN','1.001'));
});
test('明确入账后同额划转获得新键，旧成功响应不能清除新尝试', () => {
  const keys=createWalletTransferRequestKeys();
  const first=keys.forTransfer(5,'OUT','20');
  keys.confirmed(5,'OUT','20',first);
  const next=keys.forTransfer(5,'OUT','20');assert.notEqual(first,next);
  keys.confirmed(5,'OUT','20',first);assert.equal(keys.forTransfer(5,'OUT','20'),next);
});

test('全额划转未知结果保留原确认摘要，刷新后的零余额不阻止结果核对', () => {
  const keys=createWalletTransferRequestKeys();const preview=transferSummary('10','100','10','IN');
  const key=keys.forTransfer(5,'IN','10',preview);
  assert.deepEqual(keys.pending(5,'IN','10.00').preview,preview);
  assert.equal(keys.pending(5,'IN','11'),null);
  keys.confirmed(5,'IN','10',key);assert.equal(keys.pending(5,'IN','10'),null);
});

test('失败后改额可能首次入账，保留同键下每次实际提交的摘要以核对未知结果', () => {
  const keys=createWalletTransferRequestKeys();const first=transferSummary('11','100','10','IN'),next=transferSummary('11','100','11','IN');
  const key=keys.forTransfer(5,'IN','10',first);
  assert.equal(keys.forTransfer(5,'IN','11',next),key);
  assert.deepEqual(keys.pending(5,'IN','10').preview,first);
  assert.deepEqual(keys.pending(5,'IN','11').preview,next);
  keys.confirmed(5,'IN','11',key);assert.equal(keys.pending(5,'IN','10'),null);
});

const memoryStorage=()=>{const values=new Map();return {getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key),values};};
const recoveryUid='00000000-0000-4000-8000-000000000007';
test('整页重载后的未知划转恢复原请求键和金额摘要，明确成功才删除恢复记录', () => {
  const storage=memoryStorage(),preview=transferSummary('10','100','10','IN');
  const first=createWalletTransferRequestKeys({userUid:recoveryUid,storage});const key=first.forTransfer(5,'IN','10',preview);
  const restored=createWalletTransferRequestKeys({userUid:recoveryUid,storage});
  assert.equal(restored.forTransfer(5,'IN','10',preview),key);assert.deepEqual(restored.pending(5,'IN','10').preview,preview);
  restored.confirmed(5,'IN','10',key);assert.equal(storage.values.size,0);
  assert.notEqual(createWalletTransferRequestKeys({userUid:recoveryUid,storage}).forTransfer(5,'IN','10',preview),key);
});
test('恢复记录按当前登录UUID隔离，账号切换不能读取或复用其他人的请求身份', () => {
  const storage=memoryStorage(),preview=transferSummary('10','100','10','IN');let uid=recoveryUid;
  const keys=createWalletTransferRequestKeys({userUid:()=>uid,storage});const original=keys.forTransfer(5,'IN','10',preview);
  uid='00000000-0000-4000-8000-000000000008';assert.equal(keys.pending(5,'IN','10'),null);assert.notEqual(keys.forTransfer(5,'IN','10',preview),original);
  uid=recoveryUid;assert.equal(keys.forTransfer(5,'IN','10',preview),original);
  for(const value of storage.values.values()){assert.equal(value.includes('studentPassword'),false);assert.equal(value.includes('token'),false);}
});
test('持久化失败时拒绝创建可发送的新请求，不能静默退回内存键', () => {
  const storage={getItem:()=>null,setItem:()=>{throw new Error('quota');},removeItem:()=>{}};
  const keys=createWalletTransferRequestKeys({userUid:recoveryUid,storage});
  assert.throws(()=>keys.forTransfer(5,'IN','10',transferSummary('10','0','10','IN')),/保存/);
});
test('损坏或未知版本的恢复记录阻止新划转，不能丢弃原记录后生成新键', () => {
  const storage=memoryStorage();storage.setItem(`course-wallet-transfer:v1:${recoveryUid}`,JSON.stringify({version:2,entries:[]}));
  assert.throws(()=>createWalletTransferRequestKeys({userUid:recoveryUid,storage}).forTransfer(5,'IN','10',transferSummary('10','0','10','IN')),/恢复/);
});
