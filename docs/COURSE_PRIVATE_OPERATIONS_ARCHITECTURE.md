# 课程密价运营中心：架构与实施计划

> 实施使用 superpowers:subagent-driven-development；每个领域先验证失败场景，再实现并检查真实 Mapper、事务与并发。本文也是本次 P0–P3 的实施计划，避免再生成一套重复的阶段文档。

## 基线与范围

基线为完整 038 功能提交 `c66b19f71cf027b206029e9d3d63e2fd27816a60`，工作分支 `feature/course-private-operations`。支持 A 长期预存、B 限时预存、C 消费续期、D 阶梯组合；限量、返利和付费会员不开放销售或真实资金发放。钱包仍唯一绑定 `user_id + course_platform.id`。所有模式共用报价、订单与原资金账本。

## 核心边界

- `course_private_plan` 管理可销售套餐及阶梯；`course_private_plan_version` 保存白名单规则，草稿可改，已发布版本不可覆盖。
- 原 `course_private_contract` 保留唯一代理商品关系与全局暂停/终止状态；多个 `course_private_contract_term` 表示历史、当前和待生效周期，不新建第二个钱包。
- 周期保存规则、价格、时间、资格条件的不可变快照；订单支付事务保存所属周期及版本，`course_private_term_order` 唯一绑定订单，不根据姓名或日期猜测归属。
- `course_private_application` 记录开通、续期、升级、重新签约申请和幂等身份；`course_wallet_release_request` 单独记录转回审批，不将申请视为已转账。
- `course_private_term_event` 和结算记录保存不可变事件及结算证据；资金事实继续以 038 原账本为准。
- 独立运营通知 outbox 以周期、提醒阶段唯一去重，提供站内可读通知；外部短信/邮件不在本次自动发送范围。
- 独立成本配置记录已核验成本、证据、核验人和版本，不能把 `base_price` 当采购成本。

## 明确的金融与资格规则

1. 金额仍使用 BigDecimal/DECIMAL，两位小数；周期累计金额使用足够宽的 DECIMAL，客户端统计以字符串展示。预存不是收入，不收会员费、不自动没收本金。
2. 所有资金和周期事务先锁用户资金行，再按协议→钱包→周期/申请锁序执行；批量订单/归档先按用户 ID 排序。发布规则只锁套餐/版本，不持资金锁。HTTP 和通知发送必须在提交后。
3. 业务时区显式配置为 `Asia/Shanghai`（可配置），通过注入 Clock 读取；不依赖各 JVM 默认时区。购买窗口为 `[start_at,end_at)`，报价和扣款都直接核对可信时间。
4. 限期资格起点为 `max(真实资格满足时间,允许生效时间)`，期限为连续 T 天。观察期默认七天，只等待原期支付订单完成，不延长购买资格。
5. 续期从原周期结束时连续起算。迟到确认前的空窗不能使用旧价；迟到确认不能重置完整 T 天。若下一周期已经过去，保留真实结算结果和历史权益，不能伪造仍有效的资格。
6. 订单完成必须有可信完成事实时间；课程排课结束时间、任意更新时间和任务观察时刻均不能冒充完成时间。归档记录和已退款记录仍参与核查。当前只支持课程全额退款，不能宣传部分退款。
7. 有效消费仅为支付时绑定该周期、真实已扣款、在结算截止内达到有效完成状态且未退款的净金额。充值、转入、转出、其他商品和其他周期排除。仅在可信时间已到原周期 end_at（即时升级为明确的 supersededAt 边界）后，全部订单终局或截止到达时才能最终结算；ACTIVE 期内即使现有订单全部完成或没有订单，也不能提前 finalized。截止后完成不静默回填已经终局的结果。
8. 自动、人工续期与下一期升级共享 `renewal_source_term_id` 唯一身份，并在原协议锁下防止重复和周期重叠。提前达标可记录待生效周期，不能通过每次成交延长当前 end_at。
9. 续期后发生历史退款：修正原期统计并追加资格复核事件；不追收差价、不改历史订单、不默认撤回已执行权益。未来续期限制必须有复核状态及人工处理依据。
10. 新增预存资格按公开的窗口统计成功 TRANSFER_IN 减该窗口批准 TRANSFER_OUT；退款、奖励、旧资金不计。用资金资格分配记录避免同一笔转入被首次开通或多期续期重复认领。允许复用存量余额必须是独立公示策略。
11. 升级默认下一期；即时升级需独立审批并记录原周期截断/结算边界。默认新增预存差额为目标门槛减已满足的原档门槛，其他口径必须由明确规则选择。旧订单不重新计价。
12. 普通价选项默认禁止。仅套餐明确允许、密价资格失效、代理显式确认 GENERAL_EXPLICIT 时才重新报价普通余额；生效专户余额不足不能借此补差。旧外部 API 不带新意图仍拒绝失效协议。最终 RATE/GENERAL 订单不写专户资金绑定，不计入过期权益消费。
13. 转回申请期间不冻结、不提前增加普通余额。批准时重新核验原拥有者、真实可用余额和未结算风险，在同一事务执行原双边流水及 EXECUTED 状态；余额不足明确失败且申请不冒充执行完成。同一申请重复批准返回同一转账。
14. LEGACY_PERMANENT 迁移保留原价格、状态、activated_at、validFrom/validUntil、余额和全部历史订单。不强加期限或新门槛；历史订单不猜测补绑定周期。终止后重新签约复用原关系和钱包，以新的关系代次、周期和审批事件追溯，不能删除重建。
15. 新套餐没有核验成本时显示数据不足并待配置，不伪造毛利。价格不高于核验成本时只能由独立权限和不同审批人对该规则/成本版本批准风险后发布；草稿修改使旧风险批准失效。运营管理权限不授予资金转回权限。

## 新增表与约束

默认角色的新增领域权限如下；实际执行仍核验当前数据库 authority 和账号，不凭角色名放行。低毛利发布还须满足不同参与者及版本/成本绑定，人工补授和即时升级须合同审核、周期调整、结算执行同时具备。

|新增能力|超级管理员|运营|财务|审计|
|---|---|---|---|---|
|套餐读取|是|是|否|是|
|草稿维护|是|是|否|否|
|套餐发布|是|否|否|否|
|权益申请审核|是|是|否|否|
|周期读取|是|是|否|是|
|周期调整|是|否|否|否|
|结算读取|是|是|是|是|
|结算执行|是|否|否|否|
|转回申请审核|是|否|是|否|
|运营分析读取|是|是|是|是|
|成本核验|是|否|是|否|
|低毛利风险批准|是|否|否|否|

转回实际执行另需原 `course-wallet:release`，读取资金申请可由原资金审计权限授权；新权限不删除原领域权限。普通登录用户只能操作本人申请、钱包与权益，不得到以上管理员能力。自定义角色需明确配置对应 authority，不能默认扩大运营或财务权限。

资金资格、套餐发布及结算事务明确采用 READ_COMMITTED；原资金行锁与原订单锁仍保证串行核验。原因是默认 REPEATABLE_READ 的普通查询可能保留等待账户锁之前建立的旧快照，而外层 FOR UPDATE 不会自动锁住嵌套查询。结算强类型查询同时锁原关联和订单，防止状态在资格判定与提交之间变化；提交后监听使用独立事务。依据：[MySQL 一致性读取](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html)、[MySQL 锁定读取](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking-reads.html)。

039 建立套餐、版本、周期、订单关联、业务申请、转回申请、权益事件、不可变结算、资金资格分配、成本版本/风险审批、运营通知 outbox。表名使用 `course_private_*` / `course_wallet_release_request`，不与服务商城共用业务状态。

- 协议周期唯一 `(contract_id,term_no)`，续期来源唯一，成交订单关联唯一。
- 所有跨实体绑定校验代理、商品、协议与钱包一致；无法用 MySQL CHECK 表达的周期不重叠约束由协议行锁、条件更新及真实并发测试保障。
- 申请以当前用户及 request_key 唯一，并保存规范操作指纹；相同键不同内容报冲突。转回执行身份由申请 ID 确定。
- 发布版本禁止普通业务 UPDATE；审核、结算和状态迁移使用版本条件更新，历史记录只新增。
- schema 与增量迁移定义一致。MySQL DDL 不承诺整体事务回滚，部分失败时逐对象核验续行，禁止 DROP/清余额/删流水恢复。

### 实施数据合同

数据库名称与 Java 实体按下表固定。常规 ID 使用 BIGINT、布尔用 TINYINT、状态用白名单 VARCHAR、实际单笔金额 DECIMAL(12,2)、累计金额 DECIMAL(30,2)、业务时间 DATETIME(0)。规则和事件 JSON 均由已验证的强类型对象生成，不执行表达式。所有新业务时间由业务 Clock 截断到秒，窗口排序还以唯一划转/流水 ID 辨识；不依赖通用 create_time 判断资金资格。

|表 / 实体|主要字段与职责|
|---|---|
|course_private_plan / CoursePrivatePlan|platformId、planCode、planName、description、mode、familyCode、tierCode/rank、visibleToAgents、applicationMode(AUTO/REVIEW)、status(DRAFT/PUBLISHED/DISABLED)、sortOrder、draftVersion、draftConfigJson、currentVersionId、创建/修改人及时间；商品+code唯一|
|course_private_plan_version / CoursePrivatePlanVersion|planId/platformId、versionNo、fixedPrice、initialDepositAmount、validityDays、initialFundingPolicy(NEW_DEPOSIT/EXISTING_BALANCE)、activationFrom、renewalPolicy(NONE/NEW_DEPOSIT/PERFORMANCE/MANUAL)、renewalDepositAmount、renewalConsumptionTarget、settlementGraceDays、renewalAuto、renewalPricePolicy(SAME_VERSION/LATEST_PUBLISHED)、renewalBeforeDays/AfterDays、upgradePolicy(DISABLED/NEXT_TERM/REVIEW_IMMEDIATE)、upgradeDepositPolicy(DIFFERENCE/FULL_TARGET/EXISTING_BALANCE)、allowGeneralCheckout、effectiveFrom/Until、costVersionId、riskApprovalId、legacyContractId、publishedAt、createdBy/At、configSnapshot；plan+version及legacyContractId唯一|
|course_private_contract_term / CoursePrivateContractTerm|contractId/userId/platformId、planId/versionId、termNo、relationGeneration、operationsMode、tierCode/rank、effectiveFixedPrice、effectiveInitialDeposit、renewalTarget、status、qualificationStatus、startAt/endAt/settlementDeadlineAt、activatedAt/qualifiedAt、renewalSourceTermId、fundingWindowStart/End、qualifiedDepositAmount、settledConsumptionAmount/orderCount、adjustmentAmount、finalizedAt、reviewRequired、supersededAt、configSnapshot、version、createdAt/updatedAt|
|course_private_term_order / CoursePrivateTermOrder|termId/contractId/userId/platformId、orderId/no、orderedAmount、qualifiedAmount、qualificationStatus、paidAt、completedAt、refundedAt、lastReconciledAt；orderId和orderNo唯一|
|course_private_application / CoursePrivateApplication|userId/platformId、planId/versionId、contractId、sourceTermId、applicationType(OPEN/RENEW/UPGRADE/REJOIN)、effectivePolicy、acknowledgeLegacyChange、status、submittedAt、reviewedBy/At/reason、requestKey、operationFingerprint、version；用户+requestKey唯一|
|course_wallet_release_request / CourseWalletReleaseRequest|walletId/userId、amount、status、reason、requestKey、operationFingerprint、reviewedBy/At/reason、executedTransferId、version、createdAt/updatedAt；用户+requestKey及executedTransferId唯一|
|course_private_term_event / CoursePrivateTermEvent|contractId、termId、sourceTermId、eventType、beforeJson/afterJson、sourceVersionId/targetVersionId、operatorId、reason、bizNo、happenedAt；bizNo唯一，只新增|
|course_private_term_settlement / CoursePrivateTermSettlement|termId、revisionNo、evaluationType、settledAmount/orderCount、targetAmount、pendingCount、result、evaluatedAt、createdAt、operationFingerprint；term+revision及term+fingerprint唯一，只新增|
|course_private_funding_allocation / CoursePrivateFundingAllocation|termId、walletId/userId、ledgerId、amount、createdAt；ledgerId唯一，记录真实转入的资格认领，不冻结或扣除钱包资金|
|course_private_cost_version / CoursePrivateCostVersion|platformId、unitCost、evidence、verifiedBy/At、validFrom/Until、reason、createdAt；只新增可信人工核验成本版本|
|course_private_risk_approval / CoursePrivateRiskApproval|planId、draftVersion、costVersionId、reviewedBy/At、status、reason、bizNo；审批绑定草稿及成本版本，审批人与草稿修改人不同|
|course_private_notification / CoursePrivateNotification|termId/userId、stage、payloadJson、status、availableAt/deliveredAt/readAt、attempts、version；term+stage唯一；站内出站记录|

协议仅增 `operations_mode`（默认 LEGACY_PERMANENT）、`current_term_id`、`relation_generation`，原金融绑定和字段语义保留。订单增不可变 `private_term_id`、`private_term_version`、`private_plan_version_id`、`private_operations_mode`，另增受控 `fulfilled_at` 及 `fulfillment_source`；历史订单这些值均保持空，不猜测补事实。

LEGACY 版本/周期为隐藏兼容记录，按每份原合同绑定其原固定价和门槛；可空 validFrom/Until 逐值复制，尤其允许旧 validUntil 早于 activatedAt 的记录，不推导新开始时间、也不以现代周期的 end>start 条件排除旧合法数据。期限窗口 CHECK 对 LEGACY 使用独立兼容条件，原状态和全部原数据不变。新规则不在迁移中默认发布。

首次/重新签约默认认领申请批准后未认领的成功转入，减同窗口批准转回；资金资格认领以转入流水唯一 ID 防重用。周期续期的默认新增预存窗口为原期 `[start,end)`，申请可在结束前/后配置天数内办理，但延期申请不会将期外资金伪装为期内新增。存量复用须选 EXISTING_BALANCE 并公示。现代开通和重新签约不走旧仅按余额激活的分支。

资格窗口同时保存 `funding_start_ledger_id`：批准事务在原用户/钱包锁下读取当前钱包最大流水 ID，NEW_DEPOSIT 只认领 ID 大于该水位且满足日期窗口的真实资金，转回也按同一水位及窗口扣减。同一秒批准前入账不能伪装批准后新增。最终认领可保存 `funding_end_ledger_id` 便于重跑与审计；激活当秒的真实资金依 ID 和唯一认领确定，而不是提高时间精度猜测。

LATEST_PUBLISHED 续期版本选择的时间锚点为下一周期 startAt（默认原 endAt），候选必须在该时点之前已发布，且 effectiveFrom/Until 覆盖该时点。任务迟跑时不能把后来发布的版本追溯套给已经开始的下一期，也不能提前采用未来版本。没有候选时沿用原已发布版本并记录 SAME_VERSION_FALLBACK 事件；停售阻止新申请销售，不抹除原期/约定续期权益。原期目标、观察期和资金资格仍固定原快照。

周期状态合同统一为 PENDING_ACTIVATION、SCHEDULED、ACTIVE、PENDING_SETTLEMENT、QUALIFIED、RENEWED、EXPIRED、CANCELLED、SUPERSEDED；兼容周期可保留旧 SUSPENDED/TERMINATED 标记，但现代全局暂停/终止主要由原协议状态承担。qualificationStatus 为 PENDING/QUALIFIED/NOT_QUALIFIED/REVIEW_REQUIRED；term_order 为 PAID/PENDING/QUALIFIED/REFUNDED/EXCLUDED；application 为 PENDING/APPROVED/REJECTED/CANCELLED；release 为 PENDING/APPROVED/EXECUTED/REJECTED/CANCELLED，仅 EXECUTED 可有 executedTransferId。

规则模式枚举为 PERMANENT_PREPAID、TIME_PREPAID、PERFORMANCE_RENEWAL、TIERED_PRICING，后者使用同一规则模型的续期策略及 tier/family 维度组合；LEGACY_PERMANENT 只用于兼容，不公开销售。限量/返利/收费会员配置在本期发布校验中拒绝。

## 服务及集成点

- `CoursePrivatePlanService`：草稿、版本发布、成本与风险评估、停售、用户可见套餐。
- `CoursePrivateApplicationService / CoursePrivateRenewalService / CoursePrivateUpgradeService`：申请审核、同钱包开通/重新签约、人工续期与升级。
- `CoursePrivateTermService`：可信时间、资格激活、周期选择、预存资格分配、不可变事件。
- `CoursePrivateSettlementService`：确定性订单核对、观察期、统计、终局结算、幂等下一期与退款追溯。
- `CourseWalletReleaseRequestService`：用户退出申请、独立财务审批和原资金服务执行。
- `CoursePrivateRightsReadService / CoursePrivateAnalyticsService`：本人权益、周期进度、管理员列表、运营统计和资金风险/账龄；不在前端加总订单决定资格。
- `CoursePrivateOperationsWorker`：周期结算/续期、到期提醒和重算对账，支持多实例及人工重跑。业务事件与任务互为补充，不依赖页面在线。

改造 `CoursePricingService`、`CourseReservedWalletService`、`CourseOrderServiceImpl`、退款服务和所有可信状态写入口；保留原 038 账本、支付宝/卡密真实充值、服务商城履约和 AQKS 自营行为。新订单领域增加 termId、termVersion、planVersionId、operationsMode 的不可变快照；升级后历史值允许空。内部上下文与客户端白名单分离。

## 连续实施计划

### P0 基线、规则与工程修复

- [x] 核验远程 dev/密价分支、实际工作区和 c66b19f；从完整实现创建隔离工作分支。
- [x] 前端旧单元测试 285/285、生产构建通过。
- [x] 当前后端全量 2,553 项通过，其中 course-web 2,546 项、common 7 项，包含真实浏览器 RBAC，零跳过。显式使用正式 Chromium，不依赖缺失的 headless-shell。
- [ ] 旧全量浏览器、038 MySQL、CI 黑鲨真实303隔离夹具、安全门禁和正确 Maven 参数。
- [x] 架构规格独立审查及接口/schema 合同确认；时间、资金水位、续期版本选择审查修订已合入规格。

### P1 数据库、领域与服务端

- [x] 新建 `database/migrations/039_course_private_operations.sql`、同步 schema、迁移前后检查与隔离 MySQL 脚本。MySQL 8.0.44 升级、fresh、七类旧协议保值、84 个非法写入拒绝、并发来源唯一、部分 DDL 恢复通过。
- [ ] 先写 Legacy 保值、时间边界、净预存、版本冻结和跨实例续期失败用例，再实现实体、Mapper、规则校验及服务。
- [ ] 套餐发布/成本审核、申请/重新签约、激活/升级、结算/续期、转回申请形成实际闭环。
  - 已实现套餐草稿 CAS、版本发布、成本核验、独立风险审批、公开套餐查询及相应 HTTP 和权限代理。
  - 已实现转回申请、撤销、拒绝、双权限财务审批、原双账本原子执行及响应重试；真实事务覆盖中途失败、消费后不足和并发审批。
  - 已实现本人及公开 UUID 指定代理申请、人工/公开 AUTO 首次开通、明确确认的终止后重新签约、真实新增预存激活、延期排程及生效前转回重验；原始申请原因与审核意见分别保留。
  - 已将周期 ID、版本、发布版本及模式接入统一报价和原钱包扣款事务；周期关联失败回滚订单与扣款，现代订单不能冒充未知历史周期。
  - 已实现可信完成及独立观察上界、强类型原订单/原扣款/退款核验、观察期终局、不可变结算、终局后事实复核、提交后事件和独立有界对账。续期目标PENDING不影响已激活购买资格。
  - 已实现 PERFORMANCE/NEW_DEPOSIT 自动与人工续期、同来源唯一下一期、连续起算、提前排程和期界最终资格核验；迟到执行按下一期起点选择版本，无候选及不兼容候选追加 SAME_VERSION_FALLBACK 审计。新版本初次门槛不会虚构已预存基数，迟到资金水位不吞掉新期转入。
  - 已实现冻结规则允许的人工补授、同家族下一期/独立即时升级、原资金资格差额/全额/存量政策、旧协议明确确认及申请水位转换、后台排程重验和冻结授权自动升档。已生效周期与原始申请快照由数据库保护。
  - 已实现全局暂停/恢复/终止、取消本代次未生效下一期、原期实际截断及历史退款兜底；状态幂等、失败原子回滚、本人白名单、权威详情、真正周期分页及停售续期提议。
  - 经营分析与站内通知已接入；旧入口同步、净事实复核与普通价显式成交已接入；完整远程验收仍待执行，不能以已有结构和测试替代。
- [ ] 订单创建事务绑定周期；补可信完成事实及退款/归档追溯；单笔/批量/外部 API 统一。

### P2 前端与运营

- [ ] 全部管理员及代理页面交付。已接入代理套餐/权益/申请/续期/转回、管理员协议/真实周期页面及恢复请求，已继续接入管理员套餐维护/成本核验/发布、指定代理与权益审核、财务双权限转回；经营分析及本人通知也已接入，完整验收继续进行。
- [ ] 服务端进度、明确资金和期限文案、临期通知、可访问的加载/空/失败状态、移动端与暗色。
- [ ] 独立 RBAC、当前数据库权限和对象归属、请求白名单、限流与用户会话隔离。

### P3 验证与交付

- [ ] 真实 Mapper 事务和线程并发，MySQL 038→039/新库/部分DDL恢复/唯一约束/数据保值。
- [ ] 后端全部模块、前端单元及构建、新运营和全部旧浏览器；新脚本进入实际 GitHub CI。
- [ ] 七份用户要求的实施文档、真实测试报告、灰度开关及安全部署说明。
- [ ] 独立规格/代码/资金复审；按三术提交最终反馈。不连接或修改生产资金数据库。


### 分析及通知实现

分析复用消费事实策略，在REPEATABLE_READ只读快照中核验原订单，使用无写锁的事实查询。后台统计不会支付、自动补授或触发资金对账。完整ID游标扫描防止第一页结果冒充财务总额。成本版本在发布时冻结，核验时间须不晚于发布；历史成本/渠道/资金年龄未知时明确返回null或UNKNOWN。

通知观察遵循用户→协议→钱包→周期锁序，发布按周期阶段唯一身份去重；收件箱发布每条使用REQUIRES_NEW事务，失败先回滚再独立保存失败次数与退避。只有DELIVERED可读取，拥有者来自会话，已读CAS不会重置readAt。所有当前周期由独立通知轮转观察，包含有原截止时间的Legacy，不依赖现代结算批次或页面在线。

旧入口同步追加隐藏LEGACY_PERMANENT规则版本与周期，原current_term_id切换不额外递增旧配置版本，原审计时间不改写。此前周期仅推进SUPERSEDED/CANCELLED和实际替代时间，核心价格/期限不覆写；LEGACY_UNKNOWN不会得到虚构首次预存基数。创建/改价/原转入激活共用原审计入口，所有快照和资金操作同事务。


### 完整金融与会话边界

普通价显式选择与正常私有报价均通过统一定价服务；私有批量报价以不可变周期、发布规则与期限确认，统计/物化版本变化不改变原金融意图。普通购买仍严格RATE/GENERAL，原专户与私有快照为空；旧请求指纹保持兼容。

人工复核解除只追加MANUAL与不可变事件，要求当前调整/结算双权限、原版本/结算ID和证据。原矛盾未解决拒绝，已授予权益不被逆改。HTTP未知保留原键，明确拒绝允许重新读取当前事实。

当前账户与路由共同构成页面缓存身份。本人权益页面清空旧状态、弹窗及迟到请求，通知和订单明细各有请求序号保护。新查询只有数量或财务关联白名单，不返回内部拥有者、学生口令、上游凭证或原审计JSON。
