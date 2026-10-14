# 密价运营中心验证记录

2026-10-14，基线 c66b19f71cf027b206029e9d3d63e2fd27816a60，隔离分支 feature/course-private-operations。此记录只说明已经实际执行的范围；远程必需门禁及完整交付审查尚待完成，未合并或部署。

## 后端全量

Java 17＋正式Chromium执行 `mvn -f backend/pom.xml test -Drbac.browser=true`，2026-10-14 22:25终态BUILD SUCCESS、exit 0。common 7、web 2,546，共2,553项，失败/错误/跳过均0，包含真实登录/MFA/刷新与浏览器RBAC。原资金、支付、服务商城、AQKS、自营、权限、凭证和订单断言保留。

日志：`.cache/course-operations-backend-final.log`，JUnit报告：`backend/**/target/surefire-reports/`。本地显式设置JAVA_HOME到openjdk17及BROWSER_PATH到正式Chromium；没有跳过缺失的headless shell。

新测试实际覆盖：不可变发布及成本风险，严格权限与当前数据库撤销，时间边界及连续起算，逐笔净预存/唯一认领，首开/排程/重签/资金原子回滚，原成交绑定、可信完成、上界与退款事实，观察期终局，提交后事件失败恢复，跨实例续期及同来源幂等，原规则/期界最新版本/回退，补授与升级/差额/旧权益转换，暂停恢复终止，真实读取及分页，旧入口同步和审计失败回滚，通知去重/失败退避/本人已读/有限期Legacy，分析原事实/真实冻结成本/账龄/对账异常，显式普通价及原来源退款，人工净事实复核/异常拒绝/原请求恢复。

新增批量测试证明：同组冻结排程首次物化后第二单继续按原规则购买；原响应丢失、重新读到新的统计/物化版本后，同一金融意图仍返回原订单而不重复扣款。旧无新增字段请求指纹逐字节保持原值。

## MySQL 038与039

`python3 database/tests/course_private_wallet_mysql.py` 已exit 0，MySQL 8.0.44，验证原升级/新库、绑定隔离、金额及幂等、双账本守恒、订单快照、权限和真实并发扣款。日志`.cache/course-operations-038-final.log`。

`python3 database/tests/course_private_operations_mysql.py` 已exit 0，同版本隔离MySQL。升级/fresh/部分DDL恢复、七种原协议状态/可空时间/价格/钱包/历史订单保值、84次非法写入被拒绝、真实外键/CHECK/唯一约束、同来源并发下一期各一份、发布与审计/资金分配不可变、五角色12权限，以及直接执行生产Mapper的资金水位/原订单/确认上界/原规则版本/兼容/通知/分析/订单分页。

同商品两个RR事务先建立旧快照，再竞争共享隐藏套餐，锁定当前读分配101/102两个不同版本。2,000条历史周期数据验证真正第二页及索引访问，新增索引与fresh定义同步。只生成隔离合成数据；没有连接真实应用或生产资金数据库。日志`.cache/course-operations-039-final.log`。

## 前端

单元290/290，生产构建exit 0，npm audit全部依赖0漏洞；日志`.cache/course-operations-frontend-{unit,build}-final.log`及`.cache/course-operations-npm-audit.json`。生产构建仍有已有的大块体积提示，不影响构建成功。

完整`npm --prefix frontend run test:browser`已exit 0，35个原脚本＋运营脚本共36个全部通过，日志`.cache/course-operations-browser-final-full.log`。其中保留黑鲨真实303隔离跳转、原商城、AQKS/自营相关流程、旧订单与全部页面断言。后续仅扩展私有报价周期字段并单独复验课程钱包脚本exit 0，日志`.cache/course-wallet-final-browser.log`；不把此后扩展冒充整条命令再次运行。

运营脚本覆盖本人权益/原规则续期、转回及状态未知响应恢复、套餐/核验成本/发布、公开UUID指定、权益审核、财务双权限与未知批准响应恢复、复制草稿与真实普通价参考、关联订单、人工复核未知与明确冲突恢复、站内已读、账户切换及迟到响应隔离、分析、390px暗色与外部网络隔离。截图在`.cache/course-private-operations-*.png`，已实际查看移动分析等渲染。

## 独立审查与未完成门禁

独立审查发现并修复RR旧快照分配重复版本及明确业务拒绝后的复核恢复锁死。后续补当前数据库权限撤销、原普通余额退款、开关关闭/恢复权益后的拒绝、旧请求指纹、批量物化金融意图与对账告警测试。

远程GitHub必需RBAC、安全专项、Gitleaks、OSV、npm audit、Semgrep、Trivy尚未获得本次提交绿色结果。工作流已接入新测试并移除必需门禁的continue-on-error/失败吞并；只有实际远程成功才能标为通过。没有声明安全扫描、PR合并或生产部署完成。
