# 密价运营中心接口

所有业务路径以下均不写 `/api` 代理前缀。沿用平台 Result 包装，成功 `code=1,data=...`；列表为 `data.records,total`。JWT 登录主体决定本人 ID 和执行人，客户端提交 operatorId、userId、balance 不会赋予任何权限或改变资金归属。

业务默认关闭，配置 `COURSE_PRIVATE_OPERATIONS_ENABLED=true` 后才允许新增草稿、发布、成本核验和代理转回申请。读取和既有申请的财务处理不因开关关闭而丢失。关闭自动续期和自动升级开关，不影响原账本退款。

## 已实现：管理套餐及发布

|方法及路径|参数|权限|
|---|---|---|
|GET /admin/course-private-operations/plans|page=1,pageSize=20,platformId?,status?|read/manage/publish 任一|
|GET /admin/course-private-operations/plans/{id}|套餐 ID|read/manage/publish 任一|
|POST /admin/course-private-operations/plans|CoursePrivatePlanRequest|course-private-plan:manage|
|PUT /admin/course-private-operations/plans/{id}|同上，expectedDraftVersion 必须匹配|course-private-plan:manage|
|POST /admin/course-private-operations/plans/{id}/publish|expectedDraftVersion,costVersionId?,riskApprovalId?,reason|course-private-plan:publish|
|POST /admin/course-private-operations/plans/{id}/disable|expectedDraftVersion,reason|course-private-plan:publish|

完整草稿包含 platformId、planCode、planName、description、familyCode、tierCode、tierRank、visibleToAgents、applicationMode=AUTO/REVIEW、sortOrder、expectedDraftVersion、reason 及 rules。商品和编码创建后不能改变；修改草稿递增 draftVersion，使旧审批不能沿用。

rules 为强类型字段：mode、fixedPrice、initialDepositAmount、validityDays、initialFundingPolicy、activationFrom、renewalPolicy、renewalDepositAmount、renewalConsumptionTarget、settlementGraceDays、renewalAuto、renewalPricePolicy、renewalBeforeDays、renewalAfterDays、upgradePolicy、upgradeDepositPolicy、upgradeAuto、allowGeneralCheckout、expirationPolicy、effectiveFrom、effectiveUntil。未定义表达式、返利、配额和会员模式不开放。

详情返回 `{plan,readOnly,draftRules,versions,assessment}`。旧兼容套餐 readOnly=true，draftRules=null、assessment.costStatus=LEGACY_PRESERVED；不从历史协议猜测现代套餐规则或采购成本，也不能转换草稿。现代 assessment 提供核验单位成本、成本版本、单位贡献额、贡献率、风险审批需求；缺真实成本对应字段为空、状态 PENDING_CONFIGURATION。未指定代理时 ordinaryPrice 为空，不能从套餐造出代理费率价；原 `/admin/course-private-contracts/price-reference` 可按代理公开 UUID 查询普通成交参考。

发布冻结完整元数据及规则快照。相同草稿并发/重复发布返回唯一版本；提交的成本或风险版本已变化时拒绝新发布。停用只阻止新销售，不清除已发布历史或已有周期。

## 已实现：成本与独立风险

|方法及路径|参数|权限|
|---|---|---|
|GET /admin/course-private-operations/costs|page,pageSize,platformId?|cost:verify/risk:approve/analytics:read 任一|
|POST /admin/course-private-operations/costs|platformId,unitCost,evidence,validFrom?,validUntil?,reason|course-private-cost:verify|
|GET /admin/course-private-operations/risk-approvals|page,pageSize,planId?|risk:approve/plan:publish 任一|
|POST /admin/course-private-operations/risk-approvals|planId,draftVersion,costVersionId,reason|course-private-risk:approve|

低毛利风险绑定当前草稿及已核验成本版本。审核人不能是草稿维护人或发布执行人。成本、已发布版本和风险审核记录不可覆盖。

## 已实现：本人可见套餐

`GET /course-private-operations/plans?page=1&pageSize=20&platformId=9`。

仅返回公开且已发布套餐的冻结名称、档位、描述、发布版本 ID 和规则，不返回草稿、内部成本证据、审批人或其他代理钱包。`canApply,unavailableReason` 根据灰度开关、商品/分类可用性及规则销售窗口计算；展示未来或过期规则不代表允许申请。页大小上限 100。

## 已实现：资金释放申请

|方法及路径|参数|权限|
|---|---|---|
|GET /course-private-operations/releases|page,pageSize,status?|本人登录|
|POST /course-private-operations/releases|walletId,amount,requestKey,reason|本人登录且拥有钱包|
|POST /course-private-operations/releases/{id}/cancel|申请 ID|本人登录且拥有申请|
|GET /admin/course-private-operations/releases|page,pageSize,status?|release:review/wallet:audit 任一|
|POST /admin/course-private-operations/releases/{id}/approve|version,reviewReason|course-wallet-release:review 且 course-wallet:release|
|POST /admin/course-private-operations/releases/{id}/reject|version,reviewReason|course-wallet-release:review|

本人响应为白名单 id、walletId、amount、status、reason、reviewReason、reviewedAt、executedTransferId、version、createdAt、requestKey，不返回内部用户 ID、审批人 ID 和操作摘要。

金额最多两位小数；幂等键为 1 到 64 位字母、数字、下划线或横线，相同用户同键异内容冲突。申请不冻结本金，也不提前增加普通余额。财务批准重新核验真实余额，成功必须返回 EXECUTED 及原转账 ID；重复批准返回同一结果。审批执行写入失败整体回滚，余额不足保持 PENDING，不能把申请成功或 APPROVED 宣传为已转回。

## 已实现：申请及首次开通

|方法及路径|参数|权限|
|---|---|---|
|GET /course-private-operations/applications|page,pageSize,platformId?,status?|本人登录|
|POST /course-private-operations/applications|planId,planVersionId,applicationType,sourceTermId?,effectivePolicy,acknowledgeLegacyChange,reason,requestKey|本人登录|
|POST /course-private-operations/applications/{id}/cancel|申请 ID|本人拥有申请|
|GET /admin/course-private-operations/applications|page,pageSize,platformId?,status?,userUid?|course-private-contract:review|
|POST /admin/course-private-operations/applications|同本人申请，另加公开 userUid|course-private-contract:review|
|POST /admin/course-private-operations/applications/{id}/approve|version,reviewReason|course-private-contract:review|
|POST /admin/course-private-operations/applications/{id}/reject|version,reviewReason|course-private-contract:review|

首次本人申请不能领取隐藏套餐，管理员可以按公开 UUID 指定代理申请已发布套餐。首次和升级申请必须确认当前发布版本；原已提交申请的同键重试返回原记录，不能用草稿编辑静默改变申请内容。原始申请原因独立保留为 applicationReason，审核意见为 reviewReason。存量 RENEW 按原冻结续期规则核验，停止新销售和隐藏套餐不撤销已有续期政策。

当前 OPEN 与 REJOIN 审批已接入真实周期。OPEN 创建待激活协议和唯一钱包，公开 AUTO 套餐可自动完成审批，但仍须满足真实资金门槛。REJOIN 要求原关系已终止、人工审批及代理明确确认，复用原钱包并增加关系代次，旧资金不能在 NEW_DEPOSIT 下重复激活。默认开通政策 ON_ACTIVATION，NEXT_TERM 首开输入作为相同首期语义兼容；不能用 IMMEDIATE 参数绕过实际资格及允许生效时间。

首次资格只认审批后同额成功划转，使用 ID 水位排除同秒旧资金并扣除批准转回；其他周期已认领资金不得复用。延期排程在生效前重新核验，跌破门槛后再次达标用新的真实时刻。余额不足不退回普通价。交易和周期订单关联同事务写入，关联失败整体回滚。

新价格预览增加 privateTermId、privateTermVersion、privatePlanVersionId、operationsMode、expiresAt；未知历史值保持空且不补绑定。排程在购买事务内物化为 ACTIVE 时，仅同一冻结周期、相同价格和日期允许原确认版本，避免重复回滚的确认循环；改价、改期或换周期仍必须重确认。现代权益不能通过旧整表协议配置接口修改。

RENEW 审批已接入真实执行。PERFORMANCE/NEW_DEPOSIT 必须满足来源期资格；同一来源周期的自动任务和人工审核共用唯一下一期，审批同键重试返回原证据所指周期。SAME_VERSION 可在申请窗口内提前排程；LATEST_PUBLISHED 等待下一期起点后冻结当时已发布且有效的版本，迟到任务不能采用该起点之后发布的规则。跨模式、档位或家族的版本变化不能由续期隐式执行。

下一期从来源期 endAt 连续起算。原期消费结算尚有待定订单时不能提前激活；观察期内最终确认仍只取得剩余期限。排程真正生效前重查消费、退款和净新增资金，已经执行的下一期保留购买权，原期晚退款进入复核。新预存资格认领成功转入流水但不扣本金或冻结钱包；初次/升级门槛基数不会因下一期的新版本 N 较大而被虚构补齐。

自动创建需要 `COURSE_PRIVATE_OPERATIONS_ENABLED=true`、`COURSE_PRIVATE_AUTO_RENEWAL_ENABLED=true` 以及来源周期冻结 `renewalAuto=true`。已有批准排程的激活独立于自动创建开关。人工补授及升级已经接入以下独立审核流程；完整运营页面和整体灰度验收仍未完成。

## 已实现：人工补授与升级

`POST /admin/course-private-operations/applications/{id}/grant-renewal`，body `{version,reviewReason}`，需要 `course-private-contract:review`、`course-private-term:adjust` 和 `course-private-settlement:execute` 三项会话及当前数据库权限。来源期冻结规则必须 `allowManualGrant=true`。只补授下一期，不修改原期未达标消费、伪造预存或产生资金流水；已排程的普通续期可由明确补授转换资格来源，价格和期限不变。不可变 MANUAL_RENEWAL_GRANTED 事件必须存在，后台才可执行该排程。

UPGRADE 使用已有申请审批接口，按来源期冻结 `upgradePolicy` 及 `upgradeDepositPolicy` 执行。默认 NEXT_TERM、DIFFERENCE；即时升级只有 REVIEW_IMMEDIATE 政策和上述独立调整/结算权限齐备时可执行。原期保存 supersededAt 截断边界，保留原 endAt/订单价格和退款目标；结算截止按有效边界加原观察期计算。现代升级须为同商品同家族更高档位。

DIFFERENCE 为目标 N 减已证明原档门槛基数，最低零；FULL_TARGET 须证明全部目标 N 的新增转入；EXISTING_BALANCE 是明确独立的存量余额政策。新增升级资金窗口从原资格关闭水位至生效前，成功转入减批准转回、排除退款和其他期已认领资金。排程生效前重新核验，资金不足保留未生效状态，后续重新达标仍沿用已冻结起止时间。升级与续期共用来源唯一下一期，已确认其他下一期时返回明确冲突，不能覆盖原批准权益。

旧协议转换须明确确认旧权益变化、申请时保存资金水位、独立即时审核。未知旧基数按零处理，不猜测旧本金曾满足新门槛；原钱包及旧成交、旧退款路径保留。

`COURSE_PRIVATE_AUTO_UPGRADE_ENABLED=true`、新运营开关开启、原期冻结 `upgradeAuto=true` 且策略 NEXT_TERM，才可自动选取最近的资金达标公开档位。候选按发布快照判定，草稿档位编辑不改变判断；同级多个可行候选保留人工选择。系统申请与批准、资格认领及下一期在同事务完成。隐藏/停售目标和资格复核来源不会自动分配。

## 已实现：权益读取及全局生命周期

|接口|权限与行为|
|---|---|
|GET /course-private-operations/rights|本人关系分页，platformId/status，忽略其他拥有者参数|
|GET /course-private-operations/terms/{id}|本人周期，重算原事实并由统一报价核验当前购买资格|
|GET /admin/course-private-operations/contracts|course-private-term:read，公开 userUid、商品及协议状态分页|
|GET /admin/course-private-operations/terms|course-private-term:read，真实历史/下一期分页，独立周期状态过滤|
|GET /admin/course-private-operations/terms/{id}|course-private-term:read，金额、有效边界、原单截止、近期历史和操作依据|
|POST /admin/course-private-operations/contracts/{id}/state|course-private-term:adjust，expectedVersion/action/reason/requestKey；action=SUSPEND/RESUME/TERMINATE|

读取财务金额以字符串返回，未知 Legacy 资格基数与消费保持空，不补造零或时间。外部响应不返回内部用户 ID 或完整审计原 JSON。历史周期按原绑定读取，只有当前关系/周期通过统一报价才标记可购。停售时可从本人权益读取原约定续期提议；版本选择与续期执行共用服务，窗口能力由服务器时间判定。

暂停不延长期限；恢复只取得原剩余购买期，到期后恢复仍为 EXPIRED；终止取消本代次未生效周期，保留余额/旧成交/退款路径，原活跃周期记录实际截断边界并结算。已终止不能通过恢复绕过重新签约。状态幂等记录保留原响应，相同请求在后来状态变化后也不再次执行。后台扫描包含已终止的原期，不能遗漏晚履约/历史退款。

## 仍待实现的接口合同

退款复核处理及普通价显式购买已接入；整体交付仍需通过远程必需门禁。分析及通知接口如下；旧入口兼容同步由原操作事务内部调用，无独立开放写接口。

## 已实现：可信履约及结算维护

`POST /admin/course-private-operations/orders/{id}/verify-fulfillment`，body `{actualFulfilledAt,reason,evidence}`。需要 `order:update` 且 `course-private-settlement:execute`。实际时间必须在原付款后且不晚于业务 Clock，精确到秒。人工依据与事实同事务保存为不可变事件，不能通过普通状态编辑或伪造用户 ID 补资格。

原手动及批量上游刷新只在原状态/绑定未被并发退款或归档改变时记录完成确认上界。自营完成原子写入实际操作时刻。上游只给“已完成”而没有实际时间时保留 actual 字段为空，截止前确认作为独立上界证据；截止后观察不冒充早先实际完成。

原事实及退款提交后，独立事务按原周期核验原订单与账本。后台 `COURSE_PRIVATE_OPERATIONS_WORKER_ENABLED=true` 后分批轮转兜底，默认关闭；它独立于是否允许新申请，不发送外部消息。后台已经接入续期和升级排程激活，以及独立开关控制的自动续期/升档；同来源先处理明确同意且资金达标的升级。

原 038 接口继续保留：本人钱包、预存转入、原账本及划转查询、课程价格预览、原协议维护和财务直接转回。新审批流程复用原资金服务，不新建现金余额系统。

后台资金申请列表与审核响应使用公开 userUid/username，不返回内部 userId、reviewedBy 或 operationFingerprint。原提交主体 submittedBy 由会话写入并保护；代理重签或旧权益转换必须本人提交且明确确认，后台指定不能代确认。


## 经营分析与站内通知

|接口|权限及行为|
|---|---|
|GET /admin/course-private-operations/analytics?platformId=&planId=|course-private-analytics:read；当前数据库权限复核、REPEATABLE_READ一致性只读快照；逐批读取全部周期，按原扣款、可信履约及原退款核验成交|
|GET /course-private-operations/notifications?page=1&pageSize=20|当前本人会话；只返回已发布收件箱，含total/unread与安全payload，不返回拥有者ID|
|POST /course-private-operations/notifications/{id}/read|当前本人已发布通知；首次readAt保留，重复调用不改时间，跨拥有者拒绝|

分析金额使用字符串，未知成本、实际利润及账龄使用null。返回按套餐、商品与申请来源分组；历史未知来源为UNKNOWN。成本取发布版本绑定的已核验单位成本，仅估算贡献额，不能称实际利润；核验晚于版本发布时间的成本不冒充发布依据。原038未绑定周期的历史成交单独标记覆盖不足。

通知独立灰度notifications-enabled，默认关闭；周期＋阶段唯一，提前7/3/1天只发布当前窗口提醒，SCHEDULED不会宣称成功，旧来源周期不会在新期生效后冒充当前期。站内发布在独立事务中执行，失败状态/次数/指数退避持久化，最多10次自动尝试；无短信或邮件发送。worker-enabled也需要开启才能持续观察并发布。


## 完整运营读取与受控复核

|接口|规则|
|---|---|
|GET /admin/course-private-operations/plans/{id}/price-reference?userUid=|套餐读取/维护/发布权限；真实代理UUID与统一普通费率，不返回内部用户ID或余额|
|GET /course-private-operations/terms/{id}/orders|本人归属；原周期订单分页、可信完成证据、退款及资格投影，不返回学生口令|
|GET /admin/course-private-operations/terms/{id}/orders|course-private-term:read；同一原关联和分页规则|
|POST /admin/course-private-operations/terms/{id}/resolve-review|term:adjust与settlement:execute双权限；expectedVersion、expectedSettlementId、reason、evidence、requestKey|

人工复核只确认当前原净事实，保留原finalizedAt、金额、本金与已授予下一期，追加MANUAL结算及不可变审计。仍有扣款/退款/履约矛盾或原单待定时拒绝。成功后响应丢失复用原请求；明确业务拒绝后允许重新读取当前结算并准备新请求。

订单checkoutIntent=GENERAL_EXPLICIT须确认RATE/GENERAL、expectedAmount、expectedContractVersion、expectedPolicyTermId和expectedPolicyVersionId，并有clientRequestId。只有原冻结allowGeneralCheckout及独立开关都允许、当前密价不可用时才报价；正常有效期内专户不足不提供普通价。普通成交清空私有合同/钱包/周期绑定，退款回原普通余额。旧API不传此意图，继续保留原密价拒绝语义。

现代私有报价提交expectedPolicyTermId、expectedPolicyVersionId及expectedPolicyExpiresAt，与原PRIVATE/COURSE_WALLET及金额一起校验。冻结身份一致时，首次排程物化递增协议版本不使同组第二单或原成功请求无法恢复；身份、价格或期限变化仍拒绝。旧无新增字段的请求指纹逐字节兼容，新增现代私有请求以冻结金融意图而非统计版本识别。

分析还返回walletLedgerMismatchCount、walletOpeningUnverifiedCount、walletSourceUnverifiedCount及walletReconciliationStatus。成功划转必须有同额普通账本对边；消费/退款须有原支付快照及原扣款。余额或连续账本矛盾才标为明确异常；缺失历史期初/来源单独显示证据不足，不修复余额或冻结资金。
