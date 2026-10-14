"""039真实回归：仅使用无网络、无端口、内存数据的独立MySQL8，绝不连接应用库。

python3 database/tests/course_private_operations_mysql.py [--static]
"""
import concurrent.futures
import os
import pathlib
import re
import secrets
import subprocess
import sys
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE = "c66b19f71cf027b206029e9d3d63e2fd27816a60"
MIGRATION = ROOT / "database/migrations/039_course_private_operations.sql"
MARKER = "-- 课程密价运营中心（039）"
TABLES = ("course_private_plan", "course_private_plan_version", "course_private_contract_term",
          "course_private_term_order", "course_private_application", "course_wallet_release_request",
          "course_private_term_event", "course_private_term_settlement", "course_private_funding_allocation",
          "course_private_cost_version", "course_private_risk_approval", "course_private_notification")


def static_check():
    assert MIGRATION.exists(), "缺少039课程运营迁移"
    migration = MIGRATION.read_text()
    schema = (ROOT / "database/schema.sql").read_text()
    assert schema.split(MARKER, 1)[1].strip() == migration.strip(), "039新库与升级定义漂移"
    assert tuple(re.findall(r"CREATE TABLE (\w+)", migration)) == TABLES, "必须只有合同内12张表"
    assert "DROP " not in migration.upper(), "恢复不能删除资金历史"
    assert "REFERENCES course_platform" not in migration, "商品删除继续使用业务保护"
    assert "DATETIME(6)" not in migration, "业务时间统一到秒"
    before_039 = schema.split(MARKER, 1)[0]
    old = (ROOT / "database/migrations/038_course_private_pricing_wallet.sql").read_text()
    assert before_039.split("-- 课程密价与专用钱包（038）", 1)[1].strip() == old.strip(), "038不可被改写"
    for table in TABLES:
        name = "".join(word.title() for word in table.split("_"))
        entity = ROOT / "backend/course-domain/src/main/java/com/course/platform/domain/entity" / (name + ".java")
        mapper = ROOT / "backend/course-infrastructure/src/main/java/com/course/platform/infra/persistence/mapper" / (name + "Mapper.java")
        assert entity.exists() and mapper.exists(), "缺少领域实体/Mapper：" + name
        columns = re.search(r"CREATE TABLE " + table + r" \((.*?)\n\) ENGINE", migration, re.S).group(1)
        for column in re.findall(r"^  ([a-z_]+) (?:BIGINT|INT|TINYINT|VARCHAR|CHAR|JSON|DECIMAL|DATETIME)", columns, re.M):
            field = column.split("_")[0] + "".join(word.title() for word in column.split("_")[1:])
            assert re.search(r"private \w+ " + field + r";", entity.read_text()), "实体缺少字段：" + name + "." + field
    print("PASS 039静态定义、038完整性与12表/实体合同")


def seed_legacy(sql, db):
    sql("""INSERT INTO sys_user(id,uid,username,password,balance,total_recharge) VALUES
      (9001,'00000000-0000-4000-8000-000000009001','ops-a','encrypted',300,500),
      (9002,'00000000-0000-4000-8000-000000009002','ops-b','encrypted',100,100);
      INSERT INTO course_private_contract(id,user_id,platform_id,fixed_price,min_initial_deposit,status,
        activated_at,valid_from,valid_until,version,created_by,updated_by,create_time,update_time) VALUES
      (9001,9001,1,7.20,100,'ACTIVE','2026-10-01',NULL,NULL,4,9001,9001,'2026-09-01','2026-10-01'),
      (9002,9001,2,8.20,150,'PENDING_ACTIVATION',NULL,NULL,NULL,3,9001,9001,'2026-09-01','2026-10-01'),
      (9003,9001,3,9.20,160,'SUSPENDED','2026-10-01','2026-10-01','2026-11-01',5,9001,9001,'2026-09-01','2026-10-01'),
      (9004,9001,4,10.20,170,'EXPIRED','2026-09-01','2026-09-01','2026-10-01',6,9001,9001,'2026-09-01','2026-10-01'),
      (9005,9001,5,11.20,180,'TERMINATED','2026-09-01',NULL,'2026-10-01',7,9001,9001,'2026-09-01','2026-10-01'),
      (9006,9001,6,12.20,190,'ACTIVE','2026-10-01',NULL,'2026-09-01',8,9001,9001,'2026-09-01','2026-10-01'),
      (9007,9002,1,13.20,0,'ACTIVE','2026-10-01',NULL,NULL,1,9001,9001,'2026-09-01','2026-10-01');
      INSERT INTO course_reserved_wallet(id,contract_id,user_id,platform_id,balance,version)
        SELECT id,id,user_id,platform_id,200,3 FROM course_private_contract;
      INSERT INTO course_order(order_no,user_id,platform_id,platform_name,student_account,student_password,course_name,
        amount,order_status,price_source,payment_source,private_contract_id,wallet_id,contract_version) VALUES
      ('legacy-cash',9001,1,'测试课程','masked','encrypted','课程',15,2,'RATE','GENERAL',NULL,NULL,NULL),
      ('legacy-wallet',9001,1,'测试课程','masked2','encrypted','课程',7.20,2,'PRIVATE','COURSE_WALLET',9001,9001,4);
      INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
        VALUES(9001,9001,'ORDER','legacy-wallet',-1,7.20,207.20,200);""", db)


def financial_snapshot(sql, db):
    queries = [
        "SELECT id,user_id,platform_id,fixed_price,min_initial_deposit,status,activated_at,valid_from,valid_until,version,created_by,updated_by,create_time,update_time FROM course_private_contract ORDER BY id",
        "SELECT * FROM course_reserved_wallet ORDER BY id",
        "SELECT * FROM course_reserved_wallet_ledger ORDER BY id",
        "SELECT id,balance,total_recharge FROM sys_user WHERE id>=9001 ORDER BY id",
        "SELECT order_no,user_id,platform_id,amount,order_status,price_source,payment_source,private_contract_id,wallet_id,contract_version,refunded_at,is_deleted FROM course_order ORDER BY id",
    ]
    return [sql(q, db) for q in queries]


def verify_legacy(sql, db):
    assert sql("SELECT COUNT(*) FROM course_private_contract c JOIN course_private_contract_term t ON t.id=c.current_term_id WHERE t.contract_id=c.id AND t.user_id=c.user_id AND t.platform_id=c.platform_id AND t.operations_mode='LEGACY_PERMANENT' AND c.operations_mode='LEGACY_PERMANENT' AND t.status=c.status AND t.effective_fixed_price=c.fixed_price AND t.effective_initial_deposit=c.min_initial_deposit AND t.start_at<=>c.valid_from AND t.end_at<=>c.valid_until AND t.activated_at<=>c.activated_at", db) == "7"
    assert sql("SELECT COUNT(*) FROM course_private_plan_version v JOIN course_private_contract c ON c.id=v.legacy_contract_id WHERE v.fixed_price=c.fixed_price AND v.initial_deposit_amount=c.min_initial_deposit", db) == "7"
    assert sql("SELECT COUNT(*) FROM course_private_plan WHERE visible_to_agents<>0", db) == "0"
    assert sql("SELECT COUNT(*) FROM course_order WHERE private_term_id IS NOT NULL OR fulfilled_at IS NOT NULL", db) == "0", "历史成交与完成事实不可猜测"
    print("PASS 七类旧协议原值/时间保全、原钱包和历史订单不变")


def verify_constraints(sql, db):
    sql("""INSERT INTO course_private_plan(id,platform_id,plan_code,plan_name,mode,status,created_by,updated_by)
      VALUES(9200,1,'time','限时套餐','TIME_PREPAID','DRAFT',9001,9001),(9201,2,'time','其他商品','TIME_PREPAID','DRAFT',9001,9001);
      INSERT INTO course_private_plan_version(id,plan_id,platform_id,version_no,fixed_price,initial_deposit_amount,validity_days,
        initial_funding_policy,renewal_policy,renewal_deposit_amount,settlement_grace_days,created_by,config_snapshot)
      VALUES(9200,9200,1,1,6.20,100,30,'NEW_DEPOSIT','NEW_DEPOSIT',100,7,9001,JSON_OBJECT()),
      (9201,9201,2,1,6.20,100,30,'NEW_DEPOSIT','NEW_DEPOSIT',100,7,9001,JSON_OBJECT());""", db)
    term = """INSERT INTO course_private_contract_term(id,contract_id,user_id,platform_id,plan_id,version_id,term_no,
      relation_generation,operations_mode,effective_fixed_price,effective_initial_deposit,status,activated_at,start_at,end_at,settlement_deadline_at,config_snapshot)
      VALUES({id},9001,9001,1,9200,9200,{no},1,'TIME_PREPAID',6.20,100,'ACTIVE','2026-10-01','2026-10-01','2026-10-31','2026-11-07',JSON_OBJECT())"""
    sql(term.format(id=9200, no=2), db)
    # 尚未取得资格的申请可以取消，取消不会凭空创造购买时间。
    sql("INSERT INTO course_private_contract_term(id,contract_id,user_id,platform_id,plan_id,version_id,term_no,operations_mode,effective_fixed_price,status,config_snapshot) VALUES(9260,9001,9001,1,9200,9200,99,'TIME_PREPAID',6.20,'PENDING_ACTIVATION',JSON_OBJECT())", db)
    sql("UPDATE course_private_contract_term SET status='CANCELLED' WHERE id=9260", db)
    sql(term.format(id=9250, no=20).replace("9250,9001,9001,1", "9250,9007,9002,1"), db)
    rejects = [
        term.format(id=9201, no=2),
        "UPDATE course_private_contract_term SET user_id=9002 WHERE id=9200",
        "UPDATE course_private_contract_term SET version_id=9201 WHERE id=9200",
        "UPDATE course_private_contract_term SET end_at=start_at WHERE id=9200",
        "UPDATE course_private_contract_term SET end_at=NULL WHERE id=9200",
        "UPDATE course_private_contract_term SET settlement_deadline_at='2026-10-01' WHERE id=9200",
        "UPDATE course_private_contract_term SET effective_fixed_price=0 WHERE id=9200",
        "UPDATE course_private_contract_term SET settled_consumption_amount=-0.01 WHERE id=9200",
        "UPDATE course_private_contract SET current_term_id=9250 WHERE id=9001",
        "UPDATE course_private_plan SET current_version_id=9201 WHERE id=9200",
    ]
    for statement in rejects:
        sql(statement, db, False)
    sql("UPDATE course_private_contract_term SET settled_consumption_amount=10000000000.00 WHERE id=9200", db)
    sql("""INSERT INTO course_private_application(user_id,platform_id,plan_id,version_id,application_type,status,request_key,operation_fingerprint)
      VALUES(9001,1,9200,9200,'OPEN','PENDING','same-key',REPEAT('a',64));""", db)
    sql("""INSERT INTO course_private_application(user_id,platform_id,plan_id,version_id,application_type,status,request_key,operation_fingerprint)
      VALUES(9001,2,9201,9201,'REJOIN','PENDING','same-key',REPEAT('b',64));""", db, False)
    sql("""INSERT INTO course_wallet_release_request(wallet_id,user_id,amount,reason,request_key,operation_fingerprint)
      VALUES(9001,9001,20,'退出申请','release-key',REPEAT('a',64));""", db)
    sql("UPDATE course_private_application SET funding_start_ledger_id=-1 WHERE request_key='same-key'", db, False)
    sql("UPDATE course_private_application SET funding_start_ledger_id=100 WHERE request_key='same-key'", db, False)
    sql("UPDATE course_private_application SET application_reason='改写原确认' WHERE request_key='same-key'", db, False)
    sql("UPDATE course_private_application SET submitted_by=9001 WHERE request_key='same-key'", db, False)
    sql("UPDATE course_wallet_release_request SET status='EXECUTED' WHERE request_key='release-key'", db, False)
    sql("UPDATE course_wallet_release_request SET user_id=9002 WHERE request_key='release-key'", db, False)
    # 同源自动/人工续期并发，只能成功创建一份下一期，实际执行数据库唯一约束。
    renewal = """INSERT INTO course_private_contract_term(contract_id,user_id,platform_id,plan_id,version_id,term_no,relation_generation,
      operations_mode,effective_fixed_price,effective_initial_deposit,status,start_at,end_at,settlement_deadline_at,renewal_source_term_id,config_snapshot)
      VALUES(9001,9001,1,9200,9200,{no},1,'TIME_PREPAID',6.20,100,'SCHEDULED','2026-10-31','2026-11-30','2026-12-07',9200,JSON_OBJECT())"""
    def create(no):
        try:
            sql(renewal.format(no=no), db)
            return 1
        except AssertionError as error:
            assert "Duplicate entry" in str(error), error
            return 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(create, [3, 4])) == [0, 1]
    assert sql("SELECT COUNT(*) FROM course_private_contract_term WHERE renewal_source_term_id=9200", db) == "1"
    # 秒精度半开区间，边界归下一期；观察期结束不延长购买期。
    assert sql("SELECT COUNT(*) FROM course_private_contract_term WHERE id=9200 AND '2026-10-30 23:59:59'>=start_at AND '2026-10-30 23:59:59'<end_at", db) == "1"
    assert sql("SELECT COUNT(*) FROM course_private_contract_term WHERE id=9200 AND '2026-10-31'>=start_at AND '2026-10-31'<end_at", db) == "0"
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'course_private_%' AND data_type='datetime' AND datetime_precision<>0", db) == "0"
    verify_order_and_funding(sql, db)
    verify_audit_constraints(sql, db)
    verify_mapper_queries(sql, db)
    verify_permissions(sql, db)
    print("PASS 绑定隔离、现代时间约束、累计金额、申请幂等、真实并发续期与秒边界")


def verify_order_and_funding(sql, db):
    sql("""INSERT INTO course_order(order_no,user_id,platform_id,platform_name,student_account,student_password,course_name,
      amount,price_source,payment_source,private_contract_id,wallet_id,contract_version,private_term_id,private_term_version,
      private_plan_version_id,private_operations_mode) VALUES('modern-order',9001,1,'测试商品','masked-modern','encrypted','课程',6.20,
      'PRIVATE','COURSE_WALLET',9001,9001,4,9200,0,9200,'TIME_PREPAID');
      INSERT INTO course_private_term_order(term_id,contract_id,user_id,platform_id,order_id,order_no,ordered_amount,paid_at)
      SELECT 9200,9001,9001,1,id,order_no,amount,'2026-10-30 23:59:59' FROM course_order WHERE order_no='modern-order';""", db)
    sql("UPDATE course_order SET private_plan_version_id=9201 WHERE order_no='modern-order'", db, False)
    sql("UPDATE course_private_term_order SET term_id=9250 WHERE order_no='modern-order'", db, False)
    sql("INSERT INTO course_private_term_order(term_id,contract_id,user_id,platform_id,order_id,order_no,ordered_amount,paid_at) SELECT 9200,9001,9001,1,id,order_no,6.20,NOW() FROM course_order WHERE order_no='modern-order'", db, False)
    sql("UPDATE course_private_term_order SET qualified_amount=7 WHERE order_no='modern-order'", db, False)
    sql("UPDATE course_order SET fulfilled_at=NOW() WHERE order_no='modern-order'", db, False)
    sql("""INSERT INTO course_reserved_wallet_ledger(id,wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9200,9001,9001,'TRANSFER_IN','qualification-in',1,100,200,300),
      (9201,9001,9001,'REFUND','qualification-refund',1,6.20,300,306.20);
      INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at) VALUES(9001,9001,100,'TRANSFER_IN','qualification-key','qualification-in','SUCCEEDED',9001,'2026-10-15');
      INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount)
      VALUES(9200,9001,9001,9200,100);""", db)
    sql("INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount) VALUES(9200,9001,9001,9200,100)", db, False)
    sql("INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount) VALUES(9200,9007,9002,9201,6.20)", db, False)
    sql("""INSERT INTO course_private_term_settlement(term_id,revision_no,evaluation_type,settled_amount,order_count,target_amount,pending_count,result,operation_fingerprint)
      VALUES(9200,1,'FINAL',0,0,100,1,'NOT_QUALIFIED',REPEAT('a',64));""", db)
    sql("INSERT INTO course_private_term_settlement(term_id,revision_no,evaluation_type,settled_amount,order_count,target_amount,pending_count,result,operation_fingerprint) VALUES(9200,2,'FINAL',0,0,100,1,'NOT_QUALIFIED',REPEAT('a',64))", db, False)
    sql("INSERT INTO course_private_notification(term_id,user_id,stage,payload_json) VALUES(9200,9001,'EXPIRING',JSON_OBJECT())", db)
    sql("INSERT INTO course_private_notification(term_id,user_id,stage,payload_json) VALUES(9200,9001,'EXPIRING',JSON_OBJECT())", db, False)
    print("PASS 订单确定绑定、金额上限、资金认领唯一、结算及通知身份唯一")


def verify_audit_constraints(sql, db):
    sql("UPDATE course_private_contract_term SET effective_fixed_price=5 WHERE id=9200", db, False)
    sql("UPDATE course_private_contract_term SET end_at=\'2026-11-01\',settlement_deadline_at=\'2026-11-08\' WHERE id=9200", db, False)
    sql("UPDATE course_private_contract_term SET activated_at=NULL WHERE id=9200", db, False)
    sql("UPDATE course_private_contract_term SET renewal_source_term_id=id WHERE id=9200", db, False)
    sql("UPDATE course_private_contract_term SET funding_start_ledger_id=20,funding_end_ledger_id=10 WHERE id=9200", db, False)
    # 草稿版本可编辑，发布后SQL也不能改价，历史记录只能追加。
    sql("UPDATE course_private_plan_version SET fixed_price=6.30 WHERE id=9200", db)
    sql("UPDATE course_private_plan_version SET published_at='2026-10-01' WHERE id=9200", db)
    sql("UPDATE course_private_plan_version SET fixed_price=6.40 WHERE id=9200", db, False)
    sql("DELETE FROM course_private_plan_version WHERE id=9200", db, False)
    sql("UPDATE course_private_term_settlement SET settled_amount=100 WHERE term_id=9200", db, False)
    sql("DELETE FROM course_private_term_settlement WHERE term_id=9200", db, False)
    sql("UPDATE course_private_funding_allocation SET amount=1 WHERE ledger_id=9200", db, False)
    # 同账户其他钱包和同钱包退款流水均不能认领为新增预存。
    sql("INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount) VALUES(9200,9001,9001,9201,6.20)", db, False)
    sql("INSERT INTO course_reserved_wallet_ledger(id,wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES(9300,9001,9001,'TRANSFER_IN','no-successful-transfer',1,10,0,10)", db)
    sql("INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount) VALUES(9200,9001,9001,9300,10)", db, False)
    sql("INSERT INTO course_reserved_wallet_ledger(id,wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES(9301,9002,9001,'TRANSFER_IN','wrong-platform-transfer',1,10,0,10)", db)
    sql("INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount) VALUES(9200,9002,9001,9301,10)", db, False)
    print("PASS 激活事实、自续约拒绝、发布冻结、不可变审计及真实转入来源")


def mapper_statement(mapper, method, parameters, kind="Select"):
    # 直接执行生产Mapper注解SQL，验证与迁移列名/窗口语义一致，值仅来自隔离夹具。
    source = (ROOT / "backend/course-infrastructure/src/main/java/com/course/platform/infra/persistence/mapper" / (mapper + ".java")).read_text()
    pattern = r'@' + kind + r'\(("""(?:(?!""").)*"""|"[^"\n]*")\)\s+[^;\n]+?\b' + method + r'\('
    value = re.search(pattern, source, re.S).group(1)
    statement = value[3:-3] if value.startswith('"""') else value[1:-1]
    def literal(match):
        value = parameters[match.group(1)]
        return "NULL" if value is None else str(value) if isinstance(value, int) else "'" + str(value).replace("'", "''") + "'"
    return re.sub(r"#\{(\w+)\}", literal, statement)


def verify_mapper_queries(sql, db):
    sql("""INSERT INTO course_reserved_wallet_ledger(id,wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES
      (9400,9001,9001,'TRANSFER_IN','window-new',1,40,0,40),
      (9401,9001,9001,'TRANSFER_OUT','window-approved-out',-1,20,40,20),
      (9402,9001,9001,'REFUND','window-refund',1,500,20,520),
      (9403,9001,9001,'TRANSFER_IN','window-excluded-end',1,90,520,610);
      INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,approved_by,completed_at) VALUES
      (9001,9001,40,'TRANSFER_IN','window-new-key','window-new','SUCCEEDED',9001,NULL,'2026-10-15'),
      (9001,9001,20,'TRANSFER_OUT','window-out-key','window-approved-out','SUCCEEDED',9001,9001,'2026-10-15'),
      (9001,9001,90,'TRANSFER_IN','window-end-key','window-excluded-end','SUCCEEDED',9001,NULL,'2026-10-31');""", db)
    params = dict(walletId=9001,userId=9001,startLedgerId=9200,endLedgerId=None,windowStart="2026-10-15",windowEnd="2026-10-31")
    mapper = "CoursePrivateFundingAllocationMapper"
    assert sql(mapper_statement(mapper,"sumNetDeposit",params), db) == "20.00", "批准前同秒资金/退款/期末资金不能满足新预存"
    rows = sql(mapper_statement(mapper,"selectUnallocatedTransferIn",params), db).splitlines()
    assert len(rows) == 1 and rows[0].startswith("9400\t"), "必须只选尚未认领的成功转入"
    assert sql(mapper_statement(mapper,"selectWalletLedgerWatermark",dict(walletId=9001,userId=9001)), db) == "9403"
    assert sql(mapper_statement(mapper,"selectTransferWatermarkBefore",dict(walletId=9001,userId=9001,anchor="2026-10-31")), db) == "9401", "迟到续期不能吞掉期界后已到账的转入"
    sql("INSERT INTO course_private_plan_version(id,plan_id,platform_id,version_no,fixed_price,initial_deposit_amount,validity_days,published_at,created_by,config_snapshot) VALUES(9420,9200,1,2,5,800,30,\'2026-11-01\',9001,JSON_OBJECT())", db)
    version = sql(mapper_statement("CoursePrivatePlanVersionMapper","selectLatestAt",dict(planId=9200,anchor="2026-10-31")), db)
    assert version.startswith("9200\t"), "续期按期界选择当时已发布版本"
    later = sql(mapper_statement("CoursePrivatePlanVersionMapper","selectLatestAt",dict(planId=9200,anchor="2026-11-01")), db)
    assert later.startswith("9420\t"), "只能从实际发布时间起采用新版本"
    params["startLedgerId"] = 9400
    assert sql(mapper_statement(mapper,"sumNetDeposit",params), db) == "-20.00", "财务转回导致负净额仍保留真实值"
    snapshot = sql(mapper_statement("CoursePrivateTermOrderMapper","selectFactsByTerm",dict(termId=9200)), db)
    assert "modern-order" in snapshot
    # 非同额的人工异常不能成为资格来源；不关闭数据库对成功及财务批准的原约束。
    sql("""INSERT INTO course_reserved_wallet_ledger(id,wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9404,9001,9001,'TRANSFER_IN','window-amount-mismatch',1,50,610,660);
      INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at)
      VALUES(9001,9001,60,'TRANSFER_IN','window-mismatch-key','window-amount-mismatch','SUCCEEDED',9001,'2026-10-15');""", db)
    fact_params = dict(walletId=9001,userId=9001,startLedgerId=9200,endLedgerId=None,windowStart="2026-10-15",windowEnd="2026-10-31",asOf="2026-10-30")
    facts = sql(mapper_statement(mapper,"selectFundingFacts",fact_params), db).splitlines()
    assert [row.split("\t")[0] for row in facts] == ["9400", "9401"], "资格只读同额成功转入/批准转回，排除期外、退款和金额不一致"
    sql("INSERT INTO course_private_funding_allocation(term_id,wallet_id,user_id,ledger_id,amount) VALUES(9200,9001,9001,9400,40)", db)
    claimed = sql(mapper_statement(mapper,"selectFundingFacts",fact_params), db).splitlines()
    assert claimed[0].split("\t")[-1] == "9200", "必须保留原认领周期供纯策略区分本人认领与重复使用"
    fact_params["asOf"] = "2026-10-14"
    assert sql(mapper_statement(mapper,"selectFundingFacts",fact_params), db) == "", "未来资金事实不能提前生效"
    fact_params["asOf"] = "2026-10-30"
    fact_params["endLedgerId"] = 9400
    assert len(sql(mapper_statement(mapper,"selectFundingFacts",fact_params), db).splitlines()) == 1, "期末流水水位必须是包含式边界"
    order_id = int(sql("SELECT order_id FROM course_private_term_order WHERE term_id=9200", db))
    sql("""UPDATE course_order SET order_status=2,api_provider_id=1,third_order_id='mysql-proof' WHERE order_no='modern-order';
      INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9001,9001,'ORDER','modern-order',-1,6.20,200,193.80);""", db)
    strong = sql(mapper_statement("CoursePrivateTermOrderMapper","selectConsumptionFacts",dict(termId=9200)), db).split("\t")
    assert strong[26] == "1" and strong[27] == "0", "强类型消费事实必须连接原同额扣款与真实退款"
    completion = dict(id=order_id,providerId=1,remoteId="mysql-proof",confirmedAt="2026-10-31 06:00:00",actualAt=None,replace=True)
    sql(mapper_statement("CourseOrderMapper","recordUpstreamCompletion",completion,"Update"), db)
    observed = sql(mapper_statement("CoursePrivateTermOrderMapper","selectConsumptionFacts",dict(termId=9200)), db).split("\t")
    assert observed[21] == "NULL" and observed[23] == "2026-10-31 06:00:00" and observed[24] == "UPSTREAM_STATUS", "观察确认不能冒充实际履约时间"
    completion.update(actualAt="2026-10-31 03:00:00",confirmedAt="2026-11-01 06:00:00",replace=False)
    sql(mapper_statement("CourseOrderMapper","recordUpstreamCompletion",completion,"Update"), db)
    exact = sql(mapper_statement("CoursePrivateTermOrderMapper","selectConsumptionFacts",dict(termId=9200)), db).split("\t")
    assert exact[21] == "2026-10-31 03:00:00" and exact[22] == "UPSTREAM_CONFIRMED" and exact[23] == "2026-10-31 06:00:00", "验证MySQL SET顺序及原确认上界不被迟到任务重置"
    sql("UPDATE course_order SET is_deleted=1 WHERE id=" + str(order_id), db)
    completion["replace"] = True
    assert sql(mapper_statement("CourseOrderMapper","recordUpstreamCompletion",completion,"Update") + ";SELECT ROW_COUNT()", db) == "0", "已归档不能被上游新确认覆盖"
    sql("UPDATE course_order SET is_deleted=0 WHERE id=" + str(order_id), db)
    next_id = int(sql("SELECT id FROM course_private_contract_term WHERE renewal_source_term_id=9200", db))
    sql("UPDATE course_private_contract_term SET qualification_kind='PERFORMANCE' WHERE id=" + str(next_id), db)
    before = sql("SELECT effective_fixed_price,start_at,end_at,qualified_deposit_amount,settled_consumption_amount FROM course_private_contract_term WHERE id=" + str(next_id), db)
    grant = mapper_statement("CoursePrivateContractTermMapper","grantScheduled",dict(id=next_id,sourceId=9200,expectedVersion=0,now="2026-10-25"),"Update")
    assert sql(grant + ";SELECT ROW_COUNT()", db) == "1", "人工补授必须显式更新未生效排程"
    assert sql(grant + ";SELECT ROW_COUNT()", db) == "0", "重复旧版本不能再次改变补授依据"
    assert sql("SELECT effective_fixed_price,start_at,end_at,qualified_deposit_amount,settled_consumption_amount FROM course_private_contract_term WHERE id=" + str(next_id), db) == before, "人工补授不能伪造资金/消费或改写冻结价格与期限"
    assert sql("SELECT qualification_kind FROM course_private_contract_term WHERE id=" + str(next_id), db) == "MANUAL_GRANT"
    contract_params = dict(userId=9001,platformId=1,status=None,limit=20,offset=0)
    assert sql(mapper_statement("CoursePrivateContractMapper","countOperations",contract_params), db) == "1"
    assert sql(mapper_statement("CoursePrivateContractMapper","selectOperationsPage",contract_params), db).startswith("9001\t")
    expected_periods = 4 if db == "upgrade_check" else 3  # 升级库另有原兼容周期，新库在建表后才放入旧入口夹具。
    assert int(sql(mapper_statement("CoursePrivateContractTermMapper","countOperations",contract_params), db)) == expected_periods
    assert len(sql(mapper_statement("CoursePrivateContractTermMapper","selectOperationsPage",contract_params), db).splitlines()) == expected_periods
    assert len(sql(mapper_statement("CoursePrivateContractTermMapper","selectRecentByContract",dict(contractId=9001,limit=2)), db).splitlines()) == 2
    sql("INSERT INTO course_private_term_event(contract_id,term_id,event_type,biz_no,reason,happened_at) VALUES(9001,9200,'RECONCILIATION_OBSERVED','read-fixture-event','隔离夹具核验读取','2026-10-15')", db)
    assert len(sql(mapper_statement("CoursePrivateTermEventMapper","selectRecentByTerm",dict(termId=9200,limit=10)), db).splitlines()) == 1
    sql("UPDATE course_private_term_event SET reason='覆写原记录' WHERE biz_no='read-fixture-event'", db, False)
    sql("DELETE FROM course_private_term_event WHERE biz_no='read-fixture-event'", db, False)
    sql("UPDATE course_private_contract_term SET status='TERMINATED' WHERE id=9200", db)
    batch = sql(mapper_statement("CoursePrivateContractTermMapper","selectReconciliationBatch",dict(afterId=9199,limit=100)), db).splitlines()
    assert any(row.startswith("9200\t") for row in batch), "终止原期不能漏掉后续履约或历史退款兜底"
    sql("UPDATE course_private_contract_term SET status='ACTIVE' WHERE id=9200", db)
    # 分析查询与通知发布直接使用生产注解，真实MySQL核验分页、归属、阶段唯一及已读CAS。
    analysis = dict(after=0,platformId=1,planId=None)
    assert sql(mapper_statement("CoursePrivateAnalyticsMapper","terms",analysis), db)
    assert len(sql(mapper_statement("CoursePrivateAnalyticsMapper","wallets",analysis), db).splitlines()) == 2
    assert sql(mapper_statement("CoursePrivateAnalyticsMapper","ledgers",dict(walletId=9001,after=0)), db)
    assert sql(mapper_statement("CoursePrivateAnalyticsMapper","publishedPlans",analysis), db) == "0"
    sql(mapper_statement("CoursePrivateAnalyticsMapper","pendingRelease",analysis), db)
    assert sql(mapper_statement("CoursePrivateAnalyticsMapper","automaticRenewal",dict(termId=9200)), db) == "0"
    sql(mapper_statement("CoursePrivateAnalyticsMapper","origin",dict(contractId=9001)), db)
    assert sql(mapper_statement("CoursePrivateTermOrderMapper","selectConsumptionFactsReadOnly",dict(termId=9200)), db) == sql(mapper_statement("CoursePrivateTermOrderMapper","selectConsumptionFacts",dict(termId=9200)), db)
    assert sql(mapper_statement("CoursePrivateAnalyticsMapper","wallets",dict(after=0,platformId=1,planId=99999)), db) == ""
    sql("INSERT INTO course_private_notification(id,term_id,user_id,stage,payload_json,status,available_at) VALUES(9800,9200,9001,'ACTIVATED',JSON_OBJECT('title','隔离站内通知'),'PENDING','2026-10-15')", db)
    sql("INSERT INTO course_private_notification(term_id,user_id,stage,payload_json,status,available_at) VALUES(9200,9001,'ACTIVATED',JSON_OBJECT(),'PENDING','2026-10-15')", db, False)
    n = "CoursePrivateNotificationMapper"
    assert any(row.startswith("9800\t") for row in sql(mapper_statement(n,"selectReady",dict(now="2026-10-15",maxAttempts=10,limit=100)), db).splitlines())
    assert sql(mapper_statement(n,"countInbox",dict(userId=9001)), db) == "0"
    delivery=dict(id=9800,expectedVersion=0,status="DELIVERED",deliveredAt="2026-10-15",availableAt="2026-10-15")
    assert sql(mapper_statement(n,"updateDelivery",delivery,"Update")+";SELECT ROW_COUNT()", db) == "1"
    assert sql(mapper_statement(n,"updateDelivery",delivery,"Update")+";SELECT ROW_COUNT()", db) == "0"
    assert sql(mapper_statement(n,"countInbox",dict(userId=9001)), db) == "1"
    assert sql(mapper_statement(n,"countUnread",dict(userId=9001)), db) == "1"
    assert sql(mapper_statement(n,"selectInbox",dict(userId=9002,offset=0,limit=20)), db) == ""
    assert sql(mapper_statement(n,"markRead",dict(id=9800,userId=9002,now="2026-10-16"),"Update")+";SELECT ROW_COUNT()", db) == "0"
    assert sql(mapper_statement(n,"markRead",dict(id=9800,userId=9001,now="2026-10-16"),"Update")+";SELECT ROW_COUNT()", db) == "1"
    assert sql(mapper_statement(n,"markRead",dict(id=9800,userId=9001,now="2026-10-17"),"Update")+";SELECT ROW_COUNT()", db) == "0"
    assert sql(mapper_statement(n,"countUnread",dict(userId=9001)), db) == "0"
    assert sql(mapper_statement(n,"selectInbox",dict(userId=9001,offset=0,limit=20)), db).startswith("9800\t")
    assert sql("SELECT read_at FROM course_private_notification WHERE id=9800", db) == "2026-10-16 00:00:00"
    before_contract = sql("SELECT version,update_time,fixed_price,activated_at,valid_from,valid_until FROM course_private_contract WHERE id=9001", db)
    legacy_params = dict(platformId=1,operator=9001,now="2026-10-16")
    sql(mapper_statement("CoursePrivatePlanMapper","ensureLegacy",legacy_params,"Insert"), db)
    sql(mapper_statement("CoursePrivatePlanMapper","ensureLegacy",legacy_params,"Insert"), db)
    assert sql("SELECT COUNT(*) FROM course_private_plan WHERE platform_id=1 AND plan_code='__LEGACY_PERMANENT' AND mode='LEGACY_PERMANENT' AND visible_to_agents=0 AND status='DISABLED'", db) == "1"
    sql("INSERT INTO course_private_plan_version(id,plan_id,platform_id,version_no,fixed_price,initial_deposit_amount,created_by,published_at,config_snapshot) SELECT 9995,id,platform_id,100,7.20,100,9001,'2026-10-16',JSON_OBJECT('operationsMode','LEGACY_PERMANENT') FROM course_private_plan WHERE platform_id=1 AND plan_code='__LEGACY_PERMANENT'", db)
    sql("INSERT INTO course_private_contract_term(id,contract_id,user_id,platform_id,plan_id,version_id,term_no,operations_mode,effective_fixed_price,effective_initial_deposit,status,qualification_kind,activated_at,config_snapshot) SELECT 9995,9001,9001,1,plan_id,9995,100,'LEGACY_PERMANENT',7.20,100,'ACTIVE','LEGACY_UNKNOWN','2026-10-01',config_snapshot FROM course_private_plan_version WHERE id=9995", db)
    assert sql(mapper_statement("CoursePrivateContractTermMapper","maximumTermNo",dict(contractId=9001)), db) == "100"
    pointer = dict(id=9001,expectedVersion=4,termId=9995)
    assert sql(mapper_statement("CoursePrivateContractMapper","syncLegacyPointer",pointer,"Update")+";SELECT ROW_COUNT()", db) == "1"
    assert sql("SELECT version,update_time,fixed_price,activated_at,valid_from,valid_until FROM course_private_contract WHERE id=9001", db) == before_contract
    assert sql(mapper_statement("CoursePrivateContractMapper","syncLegacyPointer",dict(id=9001,expectedVersion=3,termId=9200),"Update")+";SELECT ROW_COUNT()", db) == "0"
    # 两个RR事务先建立相同旧快照，再竞争同商品隐藏套餐，必须分配不同的当前版本号。
    legacy_plan_id = int(sql("SELECT id FROM course_private_plan WHERE platform_id=1 AND plan_code='__LEGACY_PERMANENT'", db))
    lock = mapper_statement("CoursePrivatePlanMapper","selectByCodeForUpdate",dict(platformId=1,planCode="__LEGACY_PERMANENT"))
    allocate = mapper_statement("CoursePrivatePlanVersionMapper","latestVersionNoForUpdate",dict(planId=legacy_plan_id)).replace("SELECT version_no FROM", "SELECT version_no INTO @latest FROM")
    def sync_rr(index):
        ident = 10010 + index
        script = "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;START TRANSACTION;SELECT COUNT(*) FROM course_private_plan_version;DO SLEEP(0.1);" + mapper_statement("CoursePrivatePlanMapper","ensureLegacy",legacy_params,"Insert") + ";" + lock + ";DO SLEEP(0.1);" + allocate + ";INSERT INTO course_private_plan_version(id,plan_id,platform_id,version_no,fixed_price,created_by,published_at,config_snapshot) VALUES(" + str(ident) + "," + str(legacy_plan_id) + ",1,@latest+1,7.20,9001,'2026-10-16',JSON_OBJECT());COMMIT;SELECT version_no FROM course_private_plan_version WHERE id=" + str(ident)
        return int(sql(script, db).splitlines()[-1])
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(sync_rr,[0,1])) == [101,102], "RR等待套餐锁后必须读取当前版本号"
    assert sql(mapper_statement("CoursePrivateContractTermMapper","latestTermNoForUpdate",dict(contractId=9001)), db) == "100"
    assert "9995" in sql(mapper_statement("CoursePrivateNotificationMapper","selectObservationBatch",dict(afterId=9800,limit=100)), db).splitlines(), "原有限期/永久兼容周期的当前指针必须可被通知轮转观察"
    plans_mapper = "CoursePrivatePlanMapper"
    assert int(sql(mapper_statement(plans_mapper,"countRelatedContracts",dict(planId=9200)), db)) >= 1
    assert int(sql(mapper_statement(plans_mapper,"countRelatedPeriods",dict(planId=9200)), db)) >= 3
    sql(mapper_statement(plans_mapper,"countActiveContracts",dict(planId=9200,now="2026-10-15")), db)
    assert sql(mapper_statement("CoursePrivateTermOrderMapper","countByTerm",dict(termId=9200)), db) == "1"
    assert len(sql(mapper_statement("CoursePrivateTermOrderMapper","selectByTermPage",dict(termId=9200,offset=0,limit=20)), db).splitlines()) == 1
    # 两千条历史周期验证真正第二页及索引访问，数据只在隔离库中生成。
    sql("SET SESSION cte_max_recursion_depth=2100;INSERT INTO course_private_contract_term(id,contract_id,user_id,platform_id,plan_id,version_id,term_no,operations_mode,effective_fixed_price,status,qualification_kind,config_snapshot) WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n<2000) SELECT 20000+n,9001,9001,1," + str(legacy_plan_id) + ",9995,200+n,'LEGACY_PERMANENT',7.20,'EXPIRED','LEGACY_UNKNOWN',JSON_OBJECT() FROM seq", db)
    paging = dict(userId=9001,platformId=1,status="EXPIRED",offset=20,limit=20)
    page_sql = mapper_statement("CoursePrivateContractTermMapper","selectOperationsPage",paging)
    page_rows = sql(page_sql, db).splitlines()
    assert len(page_rows) == 20 and page_rows[0].startswith("21980\t"), "历史周期不能截断为最近一百条"
    assert "\tALL\t" not in sql("EXPLAIN " + page_sql, db), "历史分页须使用实际索引访问"
    assert sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='course_private_term_order' AND index_name='idx_cpop_term_order_page'", db) == "2"
    print("PASS 生产Mapper SQL：同秒水位/资金事实同额、认领身份和asOf/净预存/原订单事实查询")


def verify_permissions(sql, db):
    permissions = re.findall(r"\('((?:course-private-(?:plan|contract|term|settlement|analytics|cost|risk)|course-wallet-release):[^']+)'", MIGRATION.read_text())
    codes = ",".join("'" + code + "'" for code in permissions)
    for role, count in {"SUPER_ADMIN":12,"OPERATOR":6,"FINANCE":4,"AUDITOR":4,"USER":0}.items():
        actual = sql("SELECT COUNT(*) FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='" + role + "' AND p.permission_code IN (" + codes + ")", db)
        assert actual == str(count), (role,actual)
    assert sql("SELECT COUNT(*) FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='OPERATOR' AND p.permission_code IN ('course-wallet:release','course-wallet-release:review')", db) == "0"
    assert sql("SELECT COUNT(*) FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='FINANCE' AND p.permission_code IN ('course-private-price:manage','course-private-plan:manage','course-private-plan:publish')", db) == "0"
    print("PASS 新领域12权限：五角色精确授予，运营无转回权、财务无价格修改权")


def verify(sql):
    baseline = subprocess.check_output(["git", "show", BASELINE + ":database/schema.sql"], cwd=ROOT, text=True)
    sql("CREATE DATABASE upgrade_check; CREATE DATABASE fresh_check; CREATE DATABASE recovery_check;")
    sql(baseline, "upgrade_check")
    seed_legacy(sql, "upgrade_check")
    before = financial_snapshot(sql, "upgrade_check")
    sql(MIGRATION.read_text(), "upgrade_check")
    assert financial_snapshot(sql, "upgrade_check") == before, "迁移改变了原资金/权益字段"
    verify_legacy(sql, "upgrade_check")
    sql((ROOT / "database/schema.sql").read_text(), "fresh_check")
    # 新库没有历史兼容记录，但正常038入口仍可建旧协议，现代规则需显式开通。
    seed_legacy(sql, "fresh_check")
    for db in ("upgrade_check", "fresh_check"):
        verify_constraints(sql, db)
        sql((ROOT / "database/checks/039_course_private_operations_checks.sql").read_text(), db)
        sql((ROOT / "database/checks/039_course_private_operations_reconciliation.sql").read_text(), db)
    # DDL整体不回滚：在显式阶段标记暂停，只读清单准确指出已建对象，续行不DROP。
    sql(baseline, "recovery_check")
    migration = MIGRATION.read_text()
    prefix, suffix = migration.split("-- 039阶段二：绑定快照与兼容迁移", 1)
    sql(prefix, "recovery_check")
    inventory = sql((ROOT / "database/checks/039_course_private_operations_checks.sql").read_text(), "recovery_check")
    assert all(table in inventory for table in TABLES), "恢复清单遗漏已建对象"
    sql(suffix, "recovery_check")
    assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='recovery_check' AND table_name='course_order' AND column_name='private_term_id'") == "1"
    print("PASS 部分DDL只读盘点与续行恢复；MySQL " + sql("SELECT VERSION()"))


def main():
    static_check()
    if "--static" in sys.argv:
        return
    subprocess.run(["docker", "info"], check=True, capture_output=True, text=True, timeout=30)
    name = "course-m039-test-" + uuid.uuid4().hex[:12]
    password = secrets.token_hex(24)
    checks = {"rejected_writes": 0}
    def sql(statement, db=None, succeeds=True):
        result = subprocess.run(["docker", "exec", "-i", name, "sh", "-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names "$@"', "sh", *([db] if db else [])],
            input=statement, text=True, capture_output=True, timeout=90)
        if succeeds and result.returncode:
            raise AssertionError(result.stderr)
        if not succeeds and not result.returncode:
            raise AssertionError("数据库应拒绝非法写入：" + statement)
        if not succeeds:
            checks["rejected_writes"] += 1
        return result.stdout.strip()
    subprocess.run(["docker", "run", "--detach", "--pull=missing", "--name", name, "--network=none",
        "--tmpfs", "/var/lib/mysql", "--env", "MYSQL_ROOT_PASSWORD", "mysql:8.0"], check=True, capture_output=True,
        text=True, timeout=300, env={**os.environ, "MYSQL_ROOT_PASSWORD": password})
    try:
        for _ in range(90):
            try:
                sql("SELECT 1")
                break
            except AssertionError:
                time.sleep(1)
        else:
            raise RuntimeError("独立MySQL未就绪")
        verify(sql)
        print("PASS 数据库拒绝非法写入断言：" + str(checks["rejected_writes"]) + "；两个库并发续约各一份；生产Mapper窗口/事实/分析/通知/兼容同步与历史分页已实际执行")
    finally:
        subprocess.run(["docker", "rm", "--force", name], check=True, capture_output=True, text=True, timeout=60)


if __name__ == "__main__":
    main()
