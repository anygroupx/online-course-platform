"""Opt-in MySQL 8 regression for migration 035 and the fresh schema.

Run from any directory: python3 database/tests/providerless_mysql.py
Requires Docker and the mysql:8.0 image. Creates a private temporary container
(no published ports, tmpfs data), never connects to an existing database, and
removes only that container on exit. The baseline is the last pre-035 schema.
"""
import os
import pathlib
import secrets
import subprocess
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE = "e9966c9e6b876a1212cbb058d9d2a744b4b8780b"


def main():
    root = ROOT
    name = "course-m035-test-" + uuid.uuid4().hex[:12]
    password = secrets.token_hex(24)

    def sql(text, db=None, succeeds=True):
        result = subprocess.run([
            "docker", "exec", "-i", name, "sh", "-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --batch --skip-column-names "$@"',
            "sh", *([db] if db else []),
        ], input=text, text=True, capture_output=True, timeout=60)
        if succeeds and result.returncode:
            raise AssertionError(result.stderr)
        if not succeeds and not result.returncode:
            raise AssertionError("Expected database constraint rejection")
        return result.stdout.strip()

    subprocess.run([
        "docker", "run", "--detach", "--pull=never", "--name", name,
        "--network=none", "--tmpfs", "/var/lib/mysql",
        "--env", "MYSQL_ROOT_PASSWORD", "mysql:8.0",
    ], check=True, capture_output=True, text=True, timeout=60,
        env={**os.environ, "MYSQL_ROOT_PASSWORD": password})
    try:
        for attempt in range(90):
            try:
                sql("SELECT 1")
                break
            except AssertionError:
                time.sleep(1)
        else:
            raise RuntimeError("Isolated MySQL did not become ready")
        verify(root, sql)
    finally:
        subprocess.run(["docker", "rm", "--force", name], check=True,
                       capture_output=True, text=True, timeout=60)


def verify(root, sql):
    base = subprocess.check_output(['git','show',BASELINE + ':database/schema.sql'],cwd=root,text=True)
    sql('CREATE DATABASE legacy_check; CREATE DATABASE fresh_check;')
    sql(base, 'legacy_check')
    sql("""
    INSERT INTO api_provider(id,provider_type,name) VALUES(9001,'heisha','isolated fixture');
    INSERT INTO service_product(id,provider_id,provider_type,project,remote_product_id,title,unit_price,enabled,fulfillment_mode,version,create_time,update_time)
    VALUES(9001,9001,'heisha','default','1','local fixture',0.25,1,'SELF_OPERATED',4,NOW(),NOW()),(9002,9001,'heisha','default','2','remote fixture',0.25,1,'UPSTREAM',7,NOW(),NOW());
    INSERT INTO service_order(id,user_id,product_id,provider_id,provider_version,provider_identity,provider_type,project,remote_product_id,title,account_label,status,fulfillment_mode,quantity,completed,distance,unit_charge,paid_amount,create_time,update_time)
    VALUES('remote-history',1,9001,9001,3,REPEAT('a',64),'heisha','default','1','fixture','masked','ACTIVE','UPSTREAM',10,0,2,0.5,5,NOW(),NOW()),('local-history',1,9002,9001,5,REPEAT('b',64),'heisha','default','2','fixture','masked','PENDING','SELF_OPERATED',10,0,2,0.5,5,NOW(),NOW());
    INSERT INTO service_order_operation(id,order_id,user_id,product_id,product_version,provider_version,action,state,quantity,distance,unit_charge,amount,account_label,expires_at,create_time,update_time)
    VALUES('remote-op','remote-history',1,9001,4,3,'CREATE','SUCCEEDED',10,2,0.5,5,'masked',NOW(),NOW(),NOW()),('local-op','local-history',1,9002,7,5,'CREATE','SUCCEEDED',10,2,0.5,5,'masked',NOW(),NOW(),NOW());
    INSERT INTO service_order_fulfillment(order_id,payload_encrypted,version,create_time,update_time) VALUES('local-history','encrypted-fixture',0,NOW(),NOW());
    """, 'legacy_check')
    sql((root/'database/migrations/035_providerless_self_operated_checkout.sql').read_text(),'legacy_check')
    assert sql("SELECT provider_id IS NULL,version FROM service_product WHERE id=9001",'legacy_check') == '1\t5'
    assert sql("SELECT provider_id,version FROM service_product WHERE id=9002",'legacy_check') == '9001\t7'
    assert sql("SELECT provider_id,provider_version,provider_identity=REPEAT('a',64) FROM service_order WHERE id='remote-history'",'legacy_check') == '9001\t3\t1'
    assert sql("SELECT provider_id IS NULL,provider_version IS NULL,provider_identity IS NULL FROM service_order WHERE id='local-history'",'legacy_check') == '1\t1\t1'
    assert sql("SELECT provider_version FROM service_order_operation WHERE id='remote-op'",'legacy_check') == '3'
    assert sql("SELECT provider_version IS NULL FROM service_order_operation WHERE id='local-op'",'legacy_check') == '1'
    for sku in range(2,5):
        sql(f"INSERT INTO service_product(provider_id,provider_type,project,remote_product_id,title,unit_price,fulfillment_mode,create_time,update_time) VALUES(NULL,'heisha','default','{sku}','fixture',0.25,'SELF_OPERATED',NOW(),NOW())",'legacy_check')
    for provider, kind, mode, sku in [('NULL','heisha','UPSTREAM','4'),('9001','heisha','SELF_OPERATED','4'),('NULL','flash','SELF_OPERATED','4'),('NULL','heisha','SELF_OPERATED','1')]:
        sql(f"INSERT INTO service_product(provider_id,provider_type,project,remote_product_id,title,unit_price,fulfillment_mode,create_time,update_time) VALUES({provider},'{kind}','default','{sku}','fixture',0.25,'{mode}',NOW(),NOW())",'legacy_check',False)
    for stmt in ["UPDATE service_order SET provider_id=NULL WHERE id='remote-history'", "UPDATE service_order SET provider_id=9001 WHERE id='local-history'", "UPDATE service_order_fulfillment SET verification_status='INVALID' WHERE order_id='local-history'", "INSERT INTO service_order_fulfillment_asset(id,order_id,asset_type,mime_type,width,height,byte_size,sha256,create_time) VALUES('bad-asset','missing','FACE_QUALIFICATION','image/png',1,1,1,REPEAT('c',64),NOW())"]:
        sql(stmt,'legacy_check',False)
    roles = sql("SELECT r.role_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.permission_code='service-order:biometric' ORDER BY r.role_code",'legacy_check')
    assert roles == 'OPERATOR\nSUPER_ADMIN', roles
    sql((root/'database/schema.sql').read_text(),'fresh_check')
    assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='fresh_check' AND table_name IN ('service_order_fulfillment','service_fulfillment_material_draft','service_order_fulfillment_asset')") == '3'
    print('PASS MySQL '+sql('SELECT VERSION()')+': legacy migration, historical snapshots, four local SKUs, uniqueness, provider constraints, image FK, verification constraints, restricted biometric grants, fresh schema')


if __name__ == "__main__":
    main()
