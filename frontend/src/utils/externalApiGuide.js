const field = (name, description, example, required = true, secret = false) =>
  ({ name, description, example, required, secret });
const platform = field('platform', '平台ID（从平台列表获取的正整数）', '1');
const school = field('school', '学校名称，部分平台需要', '示例大学', false);
const user = field('user', '学生账号', 'student001');
const pass = field('pass', '学生密码', 'STUDENT_PASSWORD', true, true);
const orderNo = field('orderNo', '本平台订单编号，不是上游订单ID', 'ORD1234567890');

export const authParams = [
  field('uid', '个人中心的用户 UUID，不是数字ID', 'YOUR_UID'),
  field('api_key', '完整API密钥；兼容旧参数 key，二者不可冲突', 'YOUR_API_KEY', true, true),
];

export const externalEndpoints = [
  { id: 'getMoney', path: 'getmoney', title: '查询余额', scope: 'balance:read',
    description: '查询密钥所属账户的余额。金额字段为 data.money。', fields: [],
    example: { money: 300.00 } },
  { id: 'getPlatforms', path: 'get-platforms', title: '获取平台列表', scope: 'platforms:read',
    description: '返回已启用的平台。id 用于后续查课和下单；price 是基础价格，实际扣费按账户费率与定价规则计算。', fields: [],
    example: [{ id: '1', name: '示例平台', description: '示例课程', price: 1.5 }] },
  { id: 'queryCourses', path: 'query-courses', title: '查课', scope: 'platforms:read',
    description: '通过平台查询学生课程；上游必须已配置、验证并启用。上游安全策略不通过时返回受控错误，不会绕过 SSRF/TLS 防护。',
    fields: [platform, school, user, pass],
    example: { studentName: '示例学生', studentAccount: 'student001', schoolName: '示例大学', courses: [{ id: 'course001', name: '示例课程', description: '', endTime: null, selected: true }], message: '查询成功' } },
  { id: 'createOrder', path: 'add', title: '单下单', scope: 'orders:write',
    description: '创建真实订单并按规则扣除余额。在线测试也会产生真实业务操作；不要对超时请求自动重试，应先查单确认。',
    confirmation: '该测试将创建真实订单并可能扣除余额。确认参数无误后继续？',
    fields: [platform, school, user, pass, field('kcid', '课程ID，查课返回的 courses[].id', 'course001', false), field('kcname', '课程名称', '示例课程')],
    example: { orderNo: 'ORD1234567890' } },
  { id: 'queryOrders', path: 'chadan', title: '查单', scope: 'orders:read',
    description: '按学生账号查询当前密钥所属用户的订单，不返回其他用户订单或学生密码。',
    fields: [field('username', '学生账号（注意此接口参数名为 username）', 'student001')],
    example: [{ orderNo: 'ORD1234567890', ptname: '示例平台', school: '示例大学', name: '示例学生', user: 'student001', kcname: '示例课程', addtime: '2026-09-06 10:00:00', courseStartTime: null, courseEndTime: null, examStartTime: null, examEndTime: null, status: '学习中', process: '50%', remarks: '' }] },
  { id: 'queryProgress', path: 'query-progress', title: '查询订单进度', scope: 'orders:read',
    description: '按本平台订单编号读取已同步的进度，仅可查询自己的订单；不会在每次请求时主动查询上游。',
    fields: [orderNo],
    example: { orderNo: 'ORD1234567890', platformName: '示例平台', studentAccount: 'student001', courseName: '示例课程', orderStatus: 1, orderStatusText: '学习中', dockStatus: 1, progress: '50%', remarks: '', createTime: '2026-09-06 10:00:00', updateTime: '2026-09-06 11:00:00' } },
  { id: 'retryOrder', path: 'budan', title: '补单', scope: 'orders:write',
    description: '仅可补自己的订单；每单最多5次，须满足订单状态和补单间隔等业务规则。请勿自动重试写请求。',
    confirmation: '该测试会提交真实补单并可能消耗补单次数，确定继续？', fields: [orderNo],
    example: { orderNo: 'ORD1234567890' } },
];

export const externalErrorCodes = [
  { http: '200', code: '1', message: '操作成功', solution: '读取 data；通用消息字段为 message，不是 msg' },
  { http: '400 / 422', code: '-2', message: '参数错误', solution: '检查UUID、表单编码、必填字段及凭证别名是否冲突' },
  { http: '401', code: '-205', message: 'API密钥无效', solution: '检查完整密钥与UID、有效期；前缀不能用于认证。不需要刷新JWT' },
  { http: '403', code: '-105 / -101', message: '账户禁用 / 作用域不足', solution: '联系管理员检查账户状态与密钥作用域' },
  { http: '429', code: '-109', message: '请求过于频繁', solution: '遵守 Retry-After 秒数退避；IP与密钥分别限流' },
  { http: '503', code: '-118', message: '安全限流服务不可用', solution: '按 Retry-After 稍后重试，不要关闭安全保护' },
  { http: '400 / 409', code: '-200 / -206 / -207', message: '余额不足 / 订单已存在 / 订单不存在', solution: '检查余额、订单参数和订单归属' },
  { http: '400 / 502', code: '-1 / -503', message: '业务或上游调用失败', solution: '保存 message 与 errorId，联系管理员排查；勿重复下单' },
];

export function externalEndpointUrl(base, endpoint) {
  return `${(base || '/api').replace(/\/+$/, '')}/external/${endpoint}`;
}

// Examples intentionally contain placeholders only, never live credentials from the test form.
export function externalExamples(endpoint, url) {
  const data = Object.fromEntries([...authParams, ...endpoint.fields].map((item) => [item.name, item.example]));
  const json = JSON.stringify(data, null, 2);
  const shellQuote = (value) => `'${value.replace(/'/g, `'"'"'`)}'`;
  const phpQuote = (value) => `'${value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
  return [
    { language: 'cURL', code: `curl --request POST ${shellQuote(url)} \\\n  ${Object.entries(data).map(([key, value]) => `--data-urlencode ${shellQuote(`${key}=${value}`)}`).join(' \\\n  ')}` },
    { language: 'JavaScript', code: `const response = await fetch(${JSON.stringify(url)}, {\n  method: 'POST',\n  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n  body: new URLSearchParams(${json})\n});\nconst result = await response.json();\nif (!response.ok || result.code !== 1) throw new Error(result.message);\nconsole.log(result.data);` },
    { language: 'Python', code: `import requests\n\nresponse = requests.post(${JSON.stringify(url)}, data=${json}, timeout=30)\nresult = response.json()\nif not response.ok or result.get('code') != 1:\n    raise RuntimeError(result.get('message'))\nprint(result['data'])` },
    { language: 'PHP / 29平台', code: `// 29平台：将 YOUR_UID / YOUR_API_KEY 替换为 $a['user'] / $a['pass']。\n// 基础地址应包含 /api；不要再重复拼接 /api。\n$data = [\n${Object.entries(data).map(([key, value]) => `    ${phpQuote(key)} => ${phpQuote(value)},`).join('\n')}\n];\n$ch = curl_init(${phpQuote(url)});\ncurl_setopt_array($ch, [\n    CURLOPT_POST => true,\n    CURLOPT_POSTFIELDS => http_build_query($data),\n    CURLOPT_RETURNTRANSFER => true,\n    CURLOPT_TIMEOUT => 30,\n]);\n$body = curl_exec($ch);\nif ($body === false) throw new RuntimeException(curl_error($ch));\n$status = curl_getinfo($ch, CURLINFO_HTTP_CODE);\ncurl_close($ch);\n$result = json_decode($body, true, 512, JSON_THROW_ON_ERROR);\nif ($status >= 400 || $result['code'] != 1) {\n    throw new RuntimeException($result['message']);\n}\n// 对接器可将 message 映射为旧平台的 msg；余额字段为 data.money。\nreturn $result;` },
  ];
}
