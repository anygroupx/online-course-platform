# 课程商品密价与专用预存钱包

本功能绑定 `course_platform.id`。一个课程商品可查询出多门上游课程，专户覆盖该商品下的订单；不声称已经实现具体上游课程粒度的资金隔离。服务商城 `service_product` 和远程项目余额保持原有流程。

代码开发、测试通过、生产部署是三个独立状态。本分支不会连接、迁移或修改生产资金数据库。

## 设计及影响范围

单笔 `/orders`、批量 `/orders/batch` 和外部 `/external/add` 最终共用 `CourseOrderServiceImpl` 与 `CoursePricingService`。普通乘法价是基础价×账号费率，加法价是基础价+账号费率，金额 HALF_UP 保留两位小数。固定密价不叠加费率、邀请倍率、分类倍率；同步基础价不改协议。内部决策含锁定实体，客户端预览只返回本人价格白名单。

每个代理/商品只有一个协议和一个钱包。PENDING_ACTIVATION→ACTIVE 是一次性激活：成功转入后的真实余额达到首次门槛；未激活不能消费，故失败划转不可能制造累计预存。门槛0直接激活。激活后余额低于门槛不失去资格。暂停、尚未生效、到期、终止、专户不足都阻止新专户订单，不能降级普通价补款。终止不可恢复；暂停可在已获得资格或当前真实余额满足门槛后恢复。资金始终保留，可由财务核查后批准转回。

订单保存 amount、price_source、payment_source、协议ID/版本、钱包ID和请求身份。后续协议变更只影响新订单。成交金额与资金身份字段使用updateStrategy=NEVER，旧实体整行回写不能改变快照。管理员归档保留原有仅已完成订单退款的策略；归档其他状态不代表会退款。退款使用旧快照，专户退款要求同额原始扣款流水，不能无来源增资。refunded_at 只有原账户退款事务成功才记录；历史退款不擅自回填，空标记显示“未记录退款”。

审计发现旧批量外层事务吞异常可能整体回滚，现批量 NOT_SUPPORTED，由每笔下单代理独立提交。旧接口继续返回成功ID，新详细接口返回逐项结果。账户行锁保护旧调用方的有效订单重复校验，新调用方还用请求唯一索引及SHA256内容指纹保护网络重试。归档不释放请求唯一键。

履约事件继续 AFTER_COMMIT。供应商HTTP之前原子占用订单，已退款或取消订单禁止补单；补单也先独立提交CAS占用，不跨HTTP持锁。现有永久禁用的自营自动学习保留原待处理状态，人工倒计时不受影响。本地取消先成功时停止供应商履约，供应商结果不确定时保留进行中并交给已有执行编号核查流程，不自动退款。不在课程资金行锁事务内调用供应商HTTP。

## API

所有接口使用现有 Result（业务成功code=1），JWT登录接口由当前认证主体确定用户，不接收付款用户ID。外部API继续沿用公开UUID+API Key及 orders:write scope。

|接口|内容/权限|
|---|---|
|GET /courses/my-pricing|本人所有可销售商品报价 Map<商品ID,报价>|
|GET /courses/{platformId}/my-pricing|本人单商品 finalPrice、priceSource、paymentSource、contractVersion、walletBalance、canPurchase、unavailableReason|
|GET /course-wallets|本人专户分页|
|GET /course-wallets/{id}|本人专户详情|
|GET /course-wallets/{id}/ledger|本人资金流水分页|
|GET /course-wallets/transfers|本人划转记录分页|
|POST /course-wallets/{id}/transfer-in|本人普通余额1:1转入|
|GET /admin/course-private-contracts|协议分页，course-private-price:read/manage|
|GET /admin/course-private-contracts/price-reference|按精确userUid和platformId返回basePrice/ordinaryPrice/rateType，仅course-private-price:manage；普通参考价与普通下单共用服务端BigDecimal计算，不读取密价或钱包|
|POST /admin/course-private-contracts|创建协议，course-private-price:manage|
|PUT /admin/course-private-contracts/{id}|完整配置更新，显式version及reason|
|POST /admin/course-private-contracts/{id}/status|完整配置状态更新，同上|
|GET /admin/course-private-contracts/{id}/history|协议审计分页，协议读取/管理或资金审计权限|
|GET /admin/course-wallets|全部专户分页，course-wallet:read:any/audit/release|
|GET /admin/course-wallets/{id}/ledger|全部资金流水，同上|
|GET /admin/course-wallet-transfers|全部划转分页，同上|
|POST /admin/course-wallets/{id}/release|财务管理员直接审核处理转回，独立course-wallet:release权限|
|管理员订单导出format=5|可选财务支付与退款字段白名单，旧format=1..4兼容|
|POST /orders/batch/detailed|逐项成功/失败和成功订单ID；旧batch响应不变|

分页 page≥1，pageSize=1..100。管理员列表支持 userUid/platformId过滤。资金写接口共享现有限流机制。代理不存在或不拥有的专户均返回相同不存在错误，上下级关系不构成消费/查看权限。旧 platform:update 权限不能管理密价。默认超级管理员全部、财务资金读取/审计/转回、审计员只读、运营无新增管理权限。

协议创建使用 userUid（公开UUID）、platformId、fixedPrice、minInitialDeposit、status、validFrom、validUntil、reason。更新额外必须带 version；修改不得改变原绑定，可空日期在完整更新时清空。后台支持兼容内部 userId，但必须具有管理权限，双身份提交需一致。金额为BigDecimal正数（门槛可0）、最多两位小数、上限9999999999.99。

划转只接受 amount、requestKey、reason；幂等键为1..64位字母数字及_-，相同键不同钱包/金额/方向报冲突。转回还必须带非空审批原因，最多255字。用户端没有自行提现、转回或跨代理转账接口，也未提供代理申请/审批队列；首期由财务管理员核查后直接批准并留审计。首期没有课程钱包支付宝直充。

新订单可携带 clientRequestId、expectedAmount、expectedContractVersion、expectedPriceSource、expectedPaymentSource。服务端重新定价并拒绝旧预览，未携带这些字段的旧调用仍按服务端当前决策扣款。外部 /external/add 可传 request_id；/external/getmoney 仍返回普通余额。

## 并发与资金守恒

转入/扣款按用户→协议→钱包锁序；取消和归档先锁原用户资金行，再读取订单最新状态；整批归档先按用户ID稳定排序锁所有账户。新增协议另锁商品，与商品删除互斥。有专户协议绑定的商品只能下架，不能物理删除后同名重绑。

普通余额变动复用 AccountLedgerServiceImpl，业务类型 COURSE_TRANSFER。专户页面的累计转入、转出、消费和退款由服务端完整流水聚合为精确字符串，不对分页记录求和。钱包原子更新阻止负余额、超范围及非法精度；余额、两侧流水、划转单、激活审计处于同一事务。任何写入失败全部回滚。转回 credit(countRecharge=false)，转入也不增加 total_recharge。两侧共享同一随机划转业务单号，稳定请求键存在划转单中；时间戳不是幂等依据。

数据库独立唯一约束覆盖协议绑定、钱包绑定、钱包流水业务身份、用户划转请求、用户订单请求（含归档），复合外键保护用户/商品/协议/钱包关系，CHECK保护金额、方向、余额、流水守恒和订单来源组合。流水和历史仅提供插入与读取业务入口。

## 迁移及恢复

新库schema还补齐课程资金划转依赖的普通account_ledger（复用007/015定义）；不修改任何历史迁移。已有库须确认该表已由007建立。先升级至037，再执行 `database/migrations/038_course_private_pricing_wallet.sql` 一次。新库执行当前完整 `database/schema.sql`，其中包含同样038定义，不再次执行038。

1. 明确目标库，暂停旧/新应用的课程下单、取消、归档和资金写入；使用MySQL≥8.0.16，确认InnoDB。先执行 `database/checks/038_course_private_wallet_checks.sql` 的迁移前部分：5张新表和新增订单列首次部署应不存在。若已有对象，视为部分迁移或同名冲突，不直接重跑。
2. 对整个目标库做可恢复备份，至少包含 sys_user/account_ledger/course_order/course_platform 和全部RBAC表。示例 `mysqldump --login-path=明确的测试目标 --single-transaction --routines --triggers 明确库名 > backup.sql`；在隔离测试库确认备份可恢复。不要在命令参数或文档中粘贴密码。
3. 在与生产版本一致的隔离MySQL执行升级演练，再运行038。course_order.amount仅扩容DECIMAL(12,2)，不改历史值；历史订单默认RATE/GENERAL，协议/钱包/request/refunded_at为空，不伪造历史退款。
4. 执行完整检查SQL并对照038的 SHOW CREATE TABLE。确认CHECK被执行、唯一索引、复合外键和角色授权一致。权限变化后刷新登录会话。
5. MySQL DDL不能整体事务回滚。5个CREATE TABLE、一个订单ALTER和权限DML分别可能完成：失败时保持写入关闭，记录最后成功语句与错误，使用 SHOW CREATE TABLE / information_schema 核对已完成对象。在隔离副本验证后，仅执行尚未完成的语句。MySQL8单条ALTER具有原子DDL语义，应核对整组列/约束是否存在；不要对已完成部分全脚本重跑。
6. 已有钱包/流水/订单后，禁止DROP表、删流水、清余额或反向缩列“回滚”。应用可停止新协议与转入，仍保留安全退款和财务转回。无法确定部分迁移状态时继续暂停写入，恢复备份到独立库比对后制定续行方案，不能用危险SQL修复资金差异。

## 上线、对账和管理员使用

新代码启动前必须完成038，即使开关关闭也不免除schema前提。默认 `COURSE_PRIVATE_WALLET_ENABLED=false`，禁止创建协议和新增转入（已提交转入的原请求重试仍返回原结果）；已有协议读取/更新、原快照下单、退款和财务转回继续遵循资金隔离。验收后设置true才允许创建和预存。仅关闭开关不会把已有专户订单恢复普通扣款。

管理员精确选择代理UUID与课程商品，录入固定价和首次门槛、有效期及原因。创建前从服务端取得该代理普通参考价并与密价比较，即使管理员仅有密价管理权限、直接输入UUID，也不依赖用户搜索返回费率或浏览器计算价格。列表展示最后修改时间和操作人。代理从真实普通余额转入；首次达到门槛后激活。修改价格/状态使用当前版本和原因，冲突时刷新后再提交。财务审核转回时应核查相关订单、上游不确定履约事项和未使用余额，再以唯一请求键批准；审批身份和原因保存在划转记录，划转审计显示钱包编号、审批人和完成时间。

转入和财务转回的未知结果使用原请求键核对；关闭重开弹窗、刷新余额、路由切换或同一标签页整页重载不会生成另一笔划转。请求键及各次实际提交的金额摘要在HTTP发送前保存到按当前登录UUID隔离的sessionStorage，只有明确成功且键匹配才删除；保存失败阻止发送新请求，恢复状态损坏不自动丢弃后生成新键。只存钱包、方向、请求键及白名单金额摘要，不存登录令牌、学生口令或其他凭证；恢复时验证版本、身份格式、金额精度和双边守恒，资金权限始终由服务端复核。

已全额入账导致来源余额为0时，仍可核对原请求，由服务端返回原结果；确认文字说明原预估不能代替当前余额。失败后改额仍用同键，服务端已存在不同金额记录时返回冲突；各次实际提交金额的摘要均保留，便于核对首次失败后改额成功但响应丢失的情况。只有明确成功后才允许新请求。该恢复机制限同一浏览器标签页会话；关闭标签页、换设备等情况应先核查资金流水，不能未经核查重新发起未知结果的划转。

异常排查执行 `database/checks/038_course_private_wallet_reconciliation.sql`。先按钱包/订单/划转单号核对最后余额、两侧方向与金额、原扣款和退款。与历史普通余额对账不能直接把全部流水净额当作当前余额，历史期初资金需要独立依据。差异只读排查并保留证据，不直接UPDATE数据库余额。课程钱包与 service_project_account.remote_balance 无关。

## 验证及尚未交付的外部能力

后端命令：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -f backend/pom.xml test`。机器默认JAVA_HOME可能指向Java8，必须使用Java17。定向测试覆盖定价、协议和钱包真实Mapper事务、真实线程并发、单笔/批量/退款、提交后事件、权限和HTTP金额白名单。

数据库命令：`python3 database/tests/course_private_wallet_mysql.py`；只创建无端口、无网络的临时MySQL8容器，验证历史升级、新库初始化、约束与并发，退出只移除该测试容器。前端命令 `cd frontend && npm test && npm run build`，另运行课程钱包浏览器脚本。最终真实结果见下表；生产及真实第三方支付/履约没有自动验证或部署。

后续扩展 service_product 应新增可信商品命名空间与独立资金快照，在 ServiceCommerceServiceImpl 的报价/操作确认/扣款/退款处接入相同原则；必须保持其供应商价格版本、部分退款单位、上游不确定结果和履约审计，不应直接把远程项目余额当本地钱包。本期未实现服务商城密价、专户资金或供应商协议扩展。

## 最终测试报告（2026-10-14）

当前状态：代码已完成本期商品级密价、专户及财务直接审核转回实现；以下自动验证通过；尚未部署生产。改动位于 `feature/course-private-price-wallet` 的待审查工作区。

|范围|真实运行命令|结果|
|---|---|---|
|后端全部模块及浏览器权限|`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -f backend/pom.xml test -Drbac.browser=true`|所有模块 BUILD SUCCESS；合计2,283测试，失败0、错误0、跳过0。course-web自身2,276，其余模块7|
|前端单元及回归|`cd frontend && npm test`|285通过，失败0、跳过0|
|前端生产构建|`cd frontend && npm run build`|成功；保留现有Sass legacy-js-api及大分包提示|
|课程钱包浏览器|`cd frontend && node tests/course-wallet.browser.mjs`|PASS：金额精度、双确认、网络失败余额保全、入账后响应丢失的同键重试、弹窗重开与整页重载、存储失败阻止发送、改额冲突、全额划转后零余额核对、失败后改额首次入账的结果核对、成功后新请求、独立财务权限、仅管理权限直接UUID创建的服务端参考价、审批审计、版本冲突、服务端下单快照、运营拒绝及移动端|
|原布局浏览器回归|`cd frontend && node tests/main-layout.browser.mjs`|7/7通过|
|服务商城浏览器回归|`cd frontend && node tests/service-commerce.browser.mjs`|PASS：原商城报价、确认、订单、未知退款响应防重放、管理发布及暗色移动端|
|真实MySQL升级和新库|`python3 database/tests/course_private_wallet_mysql.py`|MySQL 8.0.44隔离容器通过：升级、初始化、旧订单默认值、唯一约束、复合外键、CHECK、RBAC、并发扣款、双边转账及异常回滚、对账SQL|
|代码格式|`git diff --check`|通过|

后端集成测试使用真实MyBatis Mapper和事务代理、H2数据库及线程并发；独立MySQL验证实际DDL、约束、锁与SQL事务。HTTP和浏览器的外部供应商、支付及账户响应使用受控模拟，不代表支付宝或真实供应商端到端验收。未连接、迁移或修改生产资金数据库。

验证中发现并修复：旧订单恢复测试夹具未包含新增列（保留39项原断言）；新库schema缺少普通账本（补齐已有007/015定义）；旧调用无请求键时的指纹约束；自营自动学习禁用时订单状态；并发补单占用窗口；开关关闭后已提交转入的幂等重试。相应测试均重新通过，没有删除断言或以跳过用例替代修复。

最终需求复核另补：管理员普通参考价由服务端提供（17个新增定价、权限与HTTP测试）；操作人/修改时间及财务审批字段展示；资金未知结果在整页重载、来源余额归零、首次失败后改额首次入账时仍可核对原请求（8个新增前端测试及浏览器案例）。未知请求恢复新增测试先出现4项失败，补持久恢复后通过；旧弹窗重开曾复现不同请求键并重复资金变动，修复后两种划转均保持只入账一次。

管理员归档的资金证据从mock扩展到实际控制器事务代理和真实Mapper：订单钱包集成14/14通过，包括改价/终止后单笔归档退原7.20、批内重复ID和再次归档退款流水保持1条、历史普通15元订单与专户混合归档分别退原来源，以及未完成订单保持原归档不退款策略。管理员自身余额不被增加。
