# 密价运营中心上线与回滚

当前仍在开发，尚未推送新提交、创建 PR、合并或部署；完整业务验收及全部远程 CI 证据尚缺。以下为待执行程序，不是已上线证明。仅使用测试钱包与隔离数据库验证，不连接生产资金账户。

上线前必须取得代码审查、全部必需 GitHub 检查和真实安全扫描成功，完成管理员/代理全流程浏览器及暗色移动端验证，并确认真实成本来源、风险审批人员和运营政策。保留分支提交历史，中文提交，生产更新需明确授权。

数据库按 038 完整状态升级至 039，先只读执行 `database/checks/039_course_private_operations_checks.sql`，核对版本、十二表、索引、约束和触发器。确认代理商品唯一关系、钱包归属、余额、双账本、旧有效期/状态/订单保值后执行迁移。新库使用同步 schema；已有原始申请确认和生效周期快照受数据库保护。DDL 不整体回滚，部分执行只能按实际对象盘点续行，不重复盲跑或删除历史。

迁移后运行 039 reconciliation，核对成功划转与双边流水同额同号、申请执行 ID 与原转回一致、资金认领无重复、原订单/周期绑定、有效期重叠与来源唯一、发布成本/风险及晚退款复核。金额异常先核查原业务身份和账本，禁止通过直接改余额补齐。未核验成本保持数据不足，不填估计值冒充实测。

|配置|默认|作用|
|---|---|---|
|COURSE_PRIVATE_GENERAL_CHECKOUT_ENABLED|false|另需原冻结规则允许及代理明确确认普通价|
|COURSE_PRIVATE_OPERATIONS_LEGACY_SYNC_ENABLED|false|039后启用旧创建/改价/激活同步，不改本金|
|COURSE_PRIVATE_OPERATIONS_NOTIFICATIONS_ENABLED|false|另需worker；原周期阶段去重及收件箱发布|
|COURSE_PRIVATE_OPERATIONS_ENABLED|false|新套餐/申请及新自动权益入口|
|COURSE_PRIVATE_AUTO_RENEWAL_ENABLED|false|另需冻结 renewalAuto 同意|
|COURSE_PRIVATE_AUTO_UPGRADE_ENABLED|false|另需冻结 upgradeAuto 与 NEXT_TERM 同意|
|COURSE_PRIVATE_OPERATIONS_WORKER_ENABLED|false|既有周期对账及批准排程的有界后台执行|

原 038 钱包开关保持原配置，不把关闭新增运营等同于关闭原支付/退款。各实例保持一致灰度配置；已批准排程不因关闭自动创建而丢失，历史退款仍要对账。先小范围人员与测试商品、再低流量代理观察资金、价格、到期边界、审批幂等及通知去重，稳定后扩大；示例价格不可直接进入正式销售。

发现问题时先关闭新增运营及自动创建开关，保留必要的历史结算/退款维护；停售目标套餐阻止新销售。不得删钱包、修改旧订单价格、追收历史优惠或逆改已执行转账。代码回退需与新增订单快照及数据库兼容核验配套，039 历史保留，不执行破坏性 down 迁移。记录故障时点、业务身份、事实修正与独立人工审批。

权限采用实际 authority 与当前数据库权限，角色名不代替领域权限。运营可维护草稿和审核普通申请，发布、低毛利风险、补授、即时升级及资金转回分别独立；财务不获价格修改权，审计读取不能执行。正式权限矩阵与最新测试以 ARCHITECTURE/API/TEST_REPORT 为准。


039核对后先开启COURSE_PRIVATE_OPERATIONS_LEGACY_SYNC_ENABLED，让原创建/改价/激活同步不可变兼容快照；关闭此开关只保留038原行为，新运营中心可能显示上次快照，不能以此配置交付完整运营读取。开启站内通知需要COURSE_PRIVATE_OPERATIONS_NOTIFICATIONS_ENABLED与WORKER_ENABLED同时为true，先使用测试账号核验阶段去重、未知响应恢复及失败退避。所有开关默认false，当前未在真实环境开启。
