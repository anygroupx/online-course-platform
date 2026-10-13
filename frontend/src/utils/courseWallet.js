const MAX = 999999999999n;
// 仅用于展示和一致性校验；成交和真实资金变化由服务端事务决定。
const cents = value => {
  const text = String(value ?? '').trim();
  if (!/^\d{1,10}(?:\.\d{1,2})?$/.test(text)) throw new Error('金额必须为合法数字，最多两位小数');
  const [whole, fraction = ''] = text.split('.');
  const result = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  if (result > MAX) throw new Error('金额超出上限');
  return result;
};
const decimal = value => `${value / 100n}.${String(value % 100n).padStart(2, '0')}`;
export const courseMoney = value => decimal(cents(value));
// 累计统计可能远超单笔与余额范围；仅展示后端字符串，不用于支付金额校验。
export const formatRecordedCourseMoney = value => {
  const text=String(value??'').trim();
  if (!/^\d+(?:\.\d{1,2})?$/.test(text)) return '—';
  const [whole,fraction='']=text.split('.');
  return `${BigInt(whole)}.${fraction.padEnd(2,'0')}`;
};
export const contractStatusLabel = status => ({ PENDING_ACTIVATION:'待预存激活', ACTIVE:'已激活', SUSPENDED:'已暂停', EXPIRED:'已到期', TERMINATED:'已终止' }[status] || '状态未知');
export function transferSummary(general, wallet, amount, direction) {
  const g=cents(general), w=cents(wallet), a=cents(amount);
  if (a<=0n) throw new Error('划转金额必须大于0');
  const nextG=direction==='IN'?g-a:g+a, nextW=direction==='IN'?w+a:w-a;
  if (nextG<0n || nextW<0n) throw new Error(direction==='IN'?'普通余额不足':'课程专户余额不足');
  if (nextG>MAX || nextW>MAX) throw new Error('划转后余额超出上限');
  return { amount:decimal(a),generalBefore:decimal(g),generalAfter:decimal(nextG),walletBefore:decimal(w),walletAfter:decimal(nextW) };
}
export function purchaseSummary(preview,count=1) {
  if (!preview || !preview.canPurchase) throw new Error(preview?.unavailableReason || '报价不可用，请重新获取服务端报价');
  if (!Number.isSafeInteger(count) || count<1) throw new Error('订单数量无效');
  const price=cents(preview.finalPrice), balance=cents(preview.walletBalance), total=price*BigInt(count);
  if (price<=0n || total>balance) throw new Error(preview.paymentSource==='COURSE_WALLET'?'课程专户余额不足，请转入预存款':'普通余额不足');
  return { unit:decimal(price),total:decimal(total),before:decimal(balance),after:decimal(balance-total),count,priceLabel:preview.priceSource==='PRIVATE'?'专属密价':'普通费率价',paymentLabel:preview.paymentSource==='COURSE_WALLET'?'课程专户':'普通余额' };
}
export function orderFundingLabels(order={}) {
  const wallet=order.paymentSource==='COURSE_WALLET';
  return { price:order.priceSource==='PRIVATE'?'专属密价':'普通费率价',payment:wallet?'课程专户':'普通余额',refund:wallet?'原课程专户':'普通余额' };
}
export function createWalletTransferRequestKeys(options={}) {
  let keys=new Map(),storage,keySpace;
  const persistent=Object.hasOwn(options,'userUid');
  const prefix='course-wallet-transfer:v1:';
  const identity=(walletId,direction,amount)=>{
    if (!/^[1-9]\d{0,18}$/.test(String(walletId))) throw new Error('课程钱包ID无效');
    if (!['IN','OUT'].includes(direction)) throw new Error('划转方向无效');
    if (courseMoney(amount)==='0.00') throw new Error('划转金额必须大于0');
    return JSON.stringify([String(walletId),direction]);
  };
  const normalizePreview=(amount,direction,preview)=>{
    // 缓存仅用于核对原请求，恢复时仍验证金额精度及双边守恒，不赋予资金权限。
    const expected=transferSummary(preview.generalBefore,preview.walletBefore,amount,direction);
    if (Object.keys(expected).some(field=>courseMoney(preview[field])!==expected[field])) throw new Error('划转摘要无效');
    return expected;
  };
  function recover(){
    if (!persistent) return;
    try {
      const uid=typeof options.userUid==='function'?options.userUid():options.userUid;
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(uid))) throw new Error('登录身份无效');
      storage=options.storage??globalThis.sessionStorage;keySpace=prefix+uid;
      const raw=storage.getItem(keySpace),state=raw===null?{version:1,entries:[]}:JSON.parse(raw);
      if (state.version!==1 || !Array.isArray(state.entries)) throw new Error('恢复版本无效');
      const restored=new Map();
      for (const [id,value] of state.entries){
        const [walletId,direction]=JSON.parse(id);
        if (identity(walletId,direction,'0.01')!==id || restored.has(id)
          || !/^[A-Za-z0-9_-]{1,64}$/.test(value.requestKey) || !Array.isArray(value.previews)) throw new Error('恢复身份无效');
        const previews=new Map();
        for (const [amount,preview] of value.previews){
          if (courseMoney(amount)!==amount || previews.has(amount)) throw new Error('恢复金额无效');
          previews.set(amount,normalizePreview(amount,direction,preview));
        }
        restored.set(id,{requestKey:value.requestKey,previews});
      }
      // 每次按当前登录UUID读取，账户切换和组件重建不会复用其他人的身份。
      keys=restored;
    } catch {throw new Error('划转恢复状态无法读取，请先核对资金流水再操作');}
  }
  function save(next,failureMessage='无法保存划转恢复状态，已阻止发起新请求，请先核对资金流水'){
    if (persistent) {
      try {
        if (!next.size) storage.removeItem(keySpace);
        else storage.setItem(keySpace,JSON.stringify({version:1,entries:Array.from(next,([id,value])=>[id,{requestKey:value.requestKey,previews:Array.from(value.previews)}])}));
      } catch {throw new Error(failureMessage);}
    }
    keys=next;
  }
  return {
    assertRecoverable:recover,
    forTransfer(walletId,direction,amount,preview) {
      recover();const id=identity(walletId,direction,amount),existing=keys.get(id);
      const value={requestKey:existing?.requestKey??crypto.randomUUID(),previews:new Map(existing?.previews)};
      // 改额也保留原键，服务端已入账时拒绝金额冲突；各次实际提交摘要均可恢复。
      if (preview) value.previews.set(courseMoney(amount),normalizePreview(amount,direction,preview));
      else if (persistent) throw new Error('无法保存划转恢复摘要，请重新确认金额');
      const next=new Map(keys);next.set(id,value);save(next);return value.requestKey;
    },
    pending(walletId,direction,amount) {
      recover();const value=keys.get(identity(walletId,direction,amount));
      const normalized=courseMoney(amount),preview=value?.previews.get(normalized);
      return preview?{requestKey:value.requestKey,amount:normalized,preview}:null;
    },
    confirmed(walletId,direction,amount,requestKey) {
      recover();const id=identity(walletId,direction,amount);
      // 只有明确成功且键匹配才删除持久记录；旧响应不能清除新的合法尝试。
      if (keys.get(id)?.requestKey===requestKey){const next=new Map(keys);next.delete(id);save(next,'服务端已确认划转，但恢复记录未能清理，请核对流水后用原请求重试');}
    }
  };
}
export function createOrderRequestKeys() {
  const keys=new Map();
  const fingerprintFor = async data => {
    // 仅保留内存中的请求摘要，不持久化学生密码；未知结果重试保持相同身份。
    const content=JSON.stringify([data.platformId,data.schoolName??'',data.studentAccount,data.studentPassword,data.courseId??'',data.courseName]);
    const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(content));
    return Array.from(new Uint8Array(digest),v=>v.toString(16).padStart(2,'0')).join('');
  };
  return { async forOrder(data) {
    const fingerprint=await fingerprintFor(data);
    if (!keys.has(fingerprint)) keys.set(fingerprint,crypto.randomUUID());
    return keys.get(fingerprint);
  }, async confirmed(data,requestKey) {
    // 只有明确成功才结束此次尝试，晚到的旧响应不能清除新尝试的身份。
    const fingerprint=await fingerprintFor(data);
    if (keys.get(fingerprint)===requestKey) keys.delete(fingerprint);
  } };
}
