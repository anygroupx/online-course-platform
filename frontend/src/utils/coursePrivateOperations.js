// 只展示服务端金额与资格；累计值允许为负，不在浏览器重新计算消费目标。
export const privateMoney=value=>{
  const text=String(value??'').trim();if(!/^-?\d+(?:\.\d{1,2})?$/.test(text))return '—';
  const [whole,fraction='']=text.split('.');return `${text.startsWith('-')&&BigInt(whole)===0n?'-':''}${BigInt(whole)}.${fraction.padEnd(2,'0')}`;
};
export const privateDate=value=>value?String(value).replace('T',' '):'未记录';
export const privateState=value=>({DRAFT:'草稿',PUBLISHED:'已发布',DISABLED:'已停售',PENDING:'待处理',PENDING_ACTIVATION:'待预存激活',SCHEDULED:'已排程',ACTIVE:'当前有效',PENDING_SETTLEMENT:'观察期结算中',QUALIFIED:'已达标',NOT_QUALIFIED:'未达标',REVIEW_REQUIRED:'待运营复核',RENEWED:'已续期',EXPIRED:'已到期',SUPERSEDED:'已被新权益接替',SUSPENDED:'已暂停',TERMINATED:'已终止',CANCELLED:'已取消',APPROVED:'已批准',REJECTED:'已拒绝',EXECUTED:'已转回'}[value]||'状态未知');
export const privateMode=value=>({PERMANENT_PREPAID:'长期预存',TIME_PREPAID:'限时预存',PERFORMANCE_RENEWAL:'消费续期',TIERED_PRICING:'阶梯成长',LEGACY_PERMANENT:'旧版兼容'}[value]||'模式未知');
export const qualificationLabel=value=>({INITIAL_FUNDS:'首次资金资格',PERFORMANCE:'消费续期',NEW_DEPOSIT:'新增预存续期',UPGRADE_FUNDS:'升级资金资格',MANUAL_GRANT:'人工补授',LEGACY_UNKNOWN:'历史资格未知'}[value]||'资格来源未知');

const canonical=value=>{
  if(value===null||typeof value==='string'||typeof value==='boolean')return JSON.stringify(value);
  if(typeof value==='number'&&Number.isFinite(value))return JSON.stringify(value);
  if(Array.isArray(value))return `[${value.map(canonical).join(',')}]`;
  if(value&&Object.getPrototypeOf(value)===Object.prototype)return `{${Object.keys(value).sort().map(key=>`${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
  throw new Error('恢复请求包含无效字段');
};
// 会话级恢复只存白名单请求，不保存登录令牌，账户切换不复用其他代理的请求身份。
export function createPrivateRequests({userUid,storage=globalThis.sessionStorage,makeKey=()=>`op_${globalThis.crypto.randomUUID().replaceAll('-','')}`}={}){
  function read(){
    try{const uid=typeof userUid==='function'?userUid():userUid;if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(String(uid)))throw new Error();
      const key=`course-private-operations:v1:${uid}`;const raw=storage.getItem(key);const state=raw===null?{version:1,entries:[]}:JSON.parse(raw);if(state.version!==1||!Array.isArray(state.entries)||state.entries.length>100)throw new Error();
      const scopes=new Set();for(const entry of state.entries){if(!entry||typeof entry.scope!=='string'||entry.scope.length>200||scopes.has(entry.scope)||!/^[A-Za-z0-9_-]{1,64}$/.test(entry.requestKey)||!entry.body||Array.isArray(entry.body)||typeof entry.body!=='object')throw new Error();canonical(entry.body);scopes.add(entry.scope);}return {key,state};
    }catch{throw new Error('原请求恢复记录无法读取，请先核对申请和状态后操作');}
  }
  function write(key,state){try{storage.setItem(key,JSON.stringify(state));}catch{throw new Error('无法保存原请求，已阻止提交，请先核对申请记录');}}
  return {
    peek(scope){return read().state.entries.find(entry=>entry.scope===scope)||null;},
    begin(scope,body){const {key,state}=read();const encoded=canonical(body);const prior=state.entries.find(entry=>entry.scope===scope);if(prior){if(canonical(prior.body)!==encoded)throw new Error('前次结果尚未核对，请先恢复原请求再修改内容');return prior;}
      if(typeof scope!=='string'||!scope||scope.length>200||state.entries.length>=100)throw new Error('请求恢复范围无效');const requestKey=makeKey();if(!/^[A-Za-z0-9_-]{1,64}$/.test(requestKey))throw new Error('请求身份无效');const entry={scope,body:JSON.parse(encoded),requestKey};state.entries.push(entry);write(key,state);return entry;},
    confirmed(scope,requestKey){const {key,state}=read();state.entries=state.entries.filter(entry=>entry.scope!==scope||entry.requestKey!==requestKey);write(key,state);},
  };
}
