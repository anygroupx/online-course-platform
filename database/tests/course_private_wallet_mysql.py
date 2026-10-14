"""课程密价数据库回归：仅创建无网络、无端口、内存数据的独立 MySQL 8 容器。

python3 database/tests/course_private_wallet_mysql.py
python3 database/tests/course_private_wallet_mysql.py --static
不会读取或连接应用数据库；退出时仅清理本脚本创建的容器。
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
BASELINE = "198537ca0e1924ef7be5747d51da4f8380aab85a"
MIGRATION = ROOT / "database/migrations/038_course_private_pricing_wallet.sql"
MARKER = "-- 课程密价与专用钱包（038）"


def static_check():
    # 新库与升级库必须运行完全相同的定义，防止双份 DDL 漂移。
    assert MIGRATION.exists(), "缺少课程密价增量迁移"
    migration = MIGRATION.read_text()
    schema = (ROOT / "database/schema.sql").read_text()
    assert schema.split(MARKER, 1)[1].split("-- 课程密价运营中心（039）", 1)[0].strip() == migration.strip(), "新库定义与迁移不一致"
    assert "REFERENCES course_platform" not in migration, "课程删除由业务保护，不追加影响旧业务的外键"
    print("PASS 新库与增量迁移定义一致")


def verify(sql):
    baseline = subprocess.check_output(
        ["git", "show", BASELINE + ":database/schema.sql"], cwd=ROOT, text=True)
    sql("CREATE DATABASE legacy_check; CREATE DATABASE fresh_check;")
    sql(baseline, "legacy_check")
    # 旧schema快照没有汇总007的普通账本；真实037前置库已通过007创建。
    existing_security = (ROOT / "database/migrations/007_security_hardening.sql").read_text()
    ledger = re.search(r"CREATE TABLE IF NOT EXISTS `account_ledger` \(.*?;", existing_security, re.S).group()
    sql(ledger, "legacy_check")
    sql("""INSERT INTO course_order(order_no,user_id,platform_id,platform_name,
        student_account,student_password,course_name,amount)
        VALUES('legacy-ordinary',9001,1,'测试课程','masked','encrypted','课程',15.00);""",
        "legacy_check")
    sql(MIGRATION.read_text(), "legacy_check")
    assert sql("SELECT price_source,payment_source,private_contract_id IS NULL,wallet_id IS NULL,amount FROM course_order WHERE order_no='legacy-ordinary'", "legacy_check") == "RATE\tGENERAL\t1\t1\t15.00"
    sql((ROOT / "database/schema.sql").read_text(), "fresh_check")
    assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='fresh_check' AND table_name='account_ledger'") == "1", "新库资金划转必须具有真实普通账本"
    for db in ("legacy_check", "fresh_check"):
        verify_constraints(sql, db)
        verify_transfer_transaction(sql, db)
        # 文档配套检查/对账SQL必须在真实MySQL可执行；合成异常夹具不冒充正常生产账本。
        sql((ROOT / "database/checks/038_course_private_wallet_checks.sql").read_text(), db)
        diagnostic = sql((ROOT / "database/checks/038_course_private_wallet_reconciliation.sql").read_text(), db)
        assert "9001" in diagnostic, "对账SQL应识别合成夹具故意制造的余额/流水差异"
        assert "9100" not in diagnostic, "正常两侧划转不得被对账SQL误报"
    print("PASS MySQL " + sql("SELECT VERSION()") + ": 历史订单升级、新库初始化、绑定隔离、幂等约束、资金范围、流水守恒、订单快照、RBAC、真实并发扣款")


def verify_constraints(sql, db):
    sql("""
        INSERT INTO sys_user(id,uid,username,password) VALUES
          (9001,'00000000-0000-4000-8000-000000009001','fixture-a','encrypted'),
          (9002,'00000000-0000-4000-8000-000000009002','fixture-b','encrypted');
        INSERT INTO course_private_contract(id,user_id,platform_id,fixed_price,min_initial_deposit,status,activated_at,created_by,updated_by)
          VALUES(9001,9001,1,7.20,100,'ACTIVE',NOW(),9001,9001),
                (9002,9002,1,8.20,0,'ACTIVE',NOW(),9001,9001),
                (9003,9001,2,6.20,0,'PENDING_ACTIVATION',NULL,9001,9001);
        INSERT INTO course_reserved_wallet(id,contract_id,user_id,platform_id,balance)
          VALUES(9001,9001,9001,1,200),(9002,9002,9002,1,100),(9003,9003,9001,2,0);
        """, db)
    rejects = [
        "INSERT INTO course_private_contract(user_id,platform_id,fixed_price,created_by,updated_by) VALUES(9001,1,7.20,9001,9001)",
        "UPDATE course_private_contract SET fixed_price=0 WHERE id=9001",
        "UPDATE course_private_contract SET min_initial_deposit=-1 WHERE id=9001",
        "UPDATE course_private_contract SET status='INVALID' WHERE id=9001",
        "UPDATE course_private_contract SET activated_at=NULL WHERE id=9001",
        "UPDATE course_private_contract SET fixed_price=10000000000 WHERE id=9001",
        "UPDATE course_private_contract SET version=-1 WHERE id=9001",
        "UPDATE course_private_contract SET valid_from='2026-10-02',valid_until='2026-10-01' WHERE id=9001",
        "UPDATE course_reserved_wallet SET user_id=9002 WHERE id=9001",
        "UPDATE course_reserved_wallet SET platform_id=2 WHERE id=9001",
        "UPDATE course_reserved_wallet SET balance=-0.01 WHERE id=9001",
        "UPDATE course_reserved_wallet SET balance=10000000000 WHERE id=9001",
        "UPDATE course_reserved_wallet SET version=-1 WHERE id=9001",
        "INSERT INTO course_reserved_wallet(contract_id,user_id,platform_id) VALUES(9001,9001,1)",
        "INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES(9001,9002,'TRANSFER_IN','foreign',1,1,0,1)",
        "INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES(9001,9001,'TRANSFER_IN','invalid',1,0,0,0)",
        "INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES(9001,9001,'TRANSFER_IN','unbalanced',1,1,0,2)",
        "INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after) VALUES(9001,9001,'TRANSFER_IN','bad-direction',0,1,0,1)",
        "INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at) VALUES(9001,9002,1,'TRANSFER_IN','foreign','foreign','SUCCEEDED',9001,NOW())",
        "INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at) VALUES(9001,9001,1,'TRANSFER_OUT','unapproved','unapproved','SUCCEEDED',9001,NOW())",
    ]
    for stmt in rejects:
        sql(stmt, db, False)
    sql("""INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
        VALUES(9001,9001,'CONSUME','order-a',-1,7.20,200,192.80);""", db)
    sql("""INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
        VALUES(9001,9001,'CONSUME','order-a',-1,7.20,192.80,185.60);""", db, False)
    sql("""INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at)
        VALUES(9001,9001,1,'TRANSFER_IN','same-request','transfer-a','SUCCEEDED',9001,NOW());""", db)
    # 同一个用户在不同钱包、不同划转类型下仍共享请求幂等命名空间。
    sql("""INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,approved_by,completed_at)
        VALUES(9003,9001,1,'TRANSFER_OUT','same-request','transfer-b','SUCCEEDED',9001,9001,NOW());""", db, False)
    sql("""INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at)
        VALUES(9002,9002,1,'TRANSFER_IN','independent-request','transfer-a','SUCCEEDED',9001,NOW());""", db, False)
    sql("""INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,approved_by,reason,completed_at)
        VALUES(9001,9001,1,'TRANSFER_OUT','approved-release','transfer-approved','SUCCEEDED',9001,9001,'授权转回',NOW());""", db)
    sql("""INSERT INTO course_private_contract_history(contract_id,operator_id,reason,before_json,after_json)
        VALUES(9001,9001,'调整协议',NULL,JSON_OBJECT('price',7.20));""", db)
    sql("""INSERT INTO course_private_contract_history(contract_id,operator_id,reason,after_json)
        VALUES(9999,9001,'不存在协议',JSON_OBJECT('price',7.20));""", db, False)
    order = """INSERT INTO course_order(order_no,user_id,platform_id,platform_name,student_account,student_password,course_name,amount,
        price_source,payment_source,private_contract_id,wallet_id,contract_version,client_request_id,request_fingerprint)
        VALUES('{no}',9001,{platform},'测试课程','masked','encrypted','课程',7.20,'PRIVATE','COURSE_WALLET',9001,{wallet},1,{key},{fingerprint})"""
    sql(order.format(no="private-good", platform=1, wallet=9001, key="'order-request'", fingerprint="REPEAT('a',64)"), db)
    for no, platform, wallet, key, fingerprint in [
        ("cross-user",1,9002,"NULL","NULL"), ("cross-platform",2,9001,"NULL","NULL"),
        ("retry",1,9001,"'order-request'","REPEAT('b',64)"),
        ("no-fingerprint",1,9001,"'new-request'","NULL"),
        ("bad-fingerprint",1,9001,"'new-request'","REPEAT('z',64)")]:
        sql(order.format(no=no,platform=platform,wallet=wallet,key=key,fingerprint=fingerprint),db,False)
    sql("UPDATE course_order SET payment_source='GENERAL' WHERE order_no='private-good'",db,False)
    sql("UPDATE course_order SET contract_version=NULL WHERE order_no='private-good'",db,False)
    # 历史普通订单不需要协议 ID 或幂等 ID。
    sql("INSERT INTO course_order(order_no,user_id,platform_id,platform_name,student_account,student_password,course_name,amount) VALUES('ordinary-good',9001,1,'测试课程','masked','encrypted','课程',15)",db)
    expected = {
        "SUPER_ADMIN": 5, "FINANCE": 3, "AUDITOR": 3, "OPERATOR": 0, "USER": 0}
    for role,count in expected.items():
        actual=sql("SELECT COUNT(*) FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='"+role+"' AND (p.permission_code LIKE 'course-private-price:%' OR p.permission_code LIKE 'course-wallet:%')",db)
        assert actual == str(count), (role,actual)
    sql("UPDATE course_reserved_wallet SET balance=7.20 WHERE id=9001",db)
    def debit(_):
        return sql("START TRANSACTION; SELECT id FROM course_reserved_wallet WHERE id=9001 FOR UPDATE; UPDATE course_reserved_wallet SET balance=balance-7.20,version=version+1 WHERE id=9001 AND balance>=7.20; SELECT ROW_COUNT(); COMMIT;",db).splitlines()[-1]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(debit,range(2))) == ['0','1']
    assert sql("SELECT balance FROM course_reserved_wallet WHERE id=9001",db) == '0.00'


def verify_transfer_transaction(sql, db):
    # 用独立正常账户验证MySQL事务、普通账本和专户守恒，不接触任何应用数据库。
    sql("""
      INSERT INTO sys_user(id,uid,username,password,balance,total_recharge)
      VALUES(9100,'00000000-0000-4000-8000-000000009100','transaction-fixture','encrypted',500,500);
      INSERT INTO course_private_contract(id,user_id,platform_id,fixed_price,min_initial_deposit,status,created_by,updated_by)
      VALUES(9100,9100,1,7.20,100,'PENDING_ACTIVATION',9100,9100);
      INSERT INTO course_reserved_wallet(id,contract_id,user_id,platform_id) VALUES(9100,9100,9100,1);
      START TRANSACTION;
      SELECT id FROM sys_user WHERE id=9100 FOR UPDATE;
      SELECT id FROM course_private_contract WHERE id=9100 FOR UPDATE;
      SELECT id FROM course_reserved_wallet WHERE id=9100 FOR UPDATE;
      UPDATE sys_user SET balance=balance-200 WHERE id=9100 AND balance>=200;
      INSERT INTO account_ledger(user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9100,'COURSE_TRANSFER','real-transfer',-1,200,500,300);
      UPDATE course_reserved_wallet SET balance=balance+200,version=version+1 WHERE id=9100;
      INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9100,9100,'TRANSFER_IN','real-transfer',1,200,0,200);
      INSERT INTO course_reserved_transfer(wallet_id,user_id,amount,type,request_key,biz_no,status,operator_id,completed_at)
      VALUES(9100,9100,200,'TRANSFER_IN','real-request','real-transfer','SUCCEEDED',9100,NOW());
      UPDATE course_private_contract SET status='ACTIVE',activated_at=NOW(),version=version+1 WHERE id=9100;
      COMMIT;
    """, db)
    expected = "300.00\t200.00\t500.00"
    query = "SELECT u.balance,w.balance,u.total_recharge FROM sys_user u JOIN course_reserved_wallet w ON w.user_id=u.id WHERE u.id=9100"
    assert sql(query,db) == expected
    # 第二侧流水CHECK失败时，连接关闭回滚整个未提交事务，包括第一侧余额和流水。
    sql("""
      START TRANSACTION;
      SELECT id FROM sys_user WHERE id=9100 FOR UPDATE;
      UPDATE sys_user SET balance=balance-100 WHERE id=9100;
      INSERT INTO account_ledger(user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9100,'COURSE_TRANSFER','failed-transfer',-1,100,300,200);
      UPDATE course_reserved_wallet SET balance=balance+100 WHERE id=9100;
      INSERT INTO course_reserved_wallet_ledger(wallet_id,user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9100,9100,'TRANSFER_IN','failed-transfer',1,100,200,301);
      COMMIT;
    """,db,False)
    assert sql(query,db) == expected
    assert sql("SELECT COUNT(*) FROM account_ledger WHERE biz_no='failed-transfer'",db) == '0'
    # 数据库唯一键是重试的最后防线；不吞错误则余额变化一起回滚。
    sql("""
      START TRANSACTION;
      UPDATE sys_user SET balance=balance-200 WHERE id=9100;
      INSERT INTO account_ledger(user_id,biz_type,biz_no,direction,amount,balance_before,balance_after)
      VALUES(9100,'COURSE_TRANSFER','real-transfer',-1,200,300,100);
      COMMIT;
    """,db,False)
    assert sql(query,db) == expected


def main():
    static_check()
    if "--static" in sys.argv:
        return
    # 探测失败时明确报错，不伪造数据库通过结果。
    subprocess.run(["docker","info"],check=True,capture_output=True,text=True,timeout=30)
    name="course-m038-test-"+uuid.uuid4().hex[:12]
    password=secrets.token_hex(24)
    def sql(statement,db=None,succeeds=True):
        result=subprocess.run([
            "docker","exec","-i",name,"sh","-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names "$@"',
            "sh",*([db] if db else [])],input=statement,text=True,capture_output=True,timeout=60)
        if succeeds and result.returncode:
            raise AssertionError(result.stderr)
        if not succeeds and not result.returncode:
            raise AssertionError("数据库应拒绝非法写入："+statement)
        return result.stdout.strip()
    subprocess.run([
        "docker","run","--detach","--pull=missing","--name",name,
        "--network=none","--tmpfs","/var/lib/mysql","--env","MYSQL_ROOT_PASSWORD","mysql:8.0"],
        check=True,capture_output=True,text=True,timeout=300,
        env={**os.environ,"MYSQL_ROOT_PASSWORD":password})
    try:
        for _ in range(90):
            try:
                sql("SELECT 1")
                break
            except AssertionError:
                time.sleep(1)
        else:
            raise RuntimeError("独立 MySQL 未就绪")
        verify(sql)
    finally:
        subprocess.run(["docker","rm","--force",name],check=True,capture_output=True,text=True,timeout=60)


if __name__ == "__main__":
    main()
