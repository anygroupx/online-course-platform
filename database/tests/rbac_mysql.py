"""Opt-in isolated MySQL 8 test: python3 database/tests/rbac_mysql.py.

Reuses the private temporary-container runner; never connects to a live database.
"""
import subprocess
import providerless_mysql as runner


def verify(root, sql):
    sql('CREATE DATABASE rbac_check;')
    baseline = subprocess.check_output(
        ['git', 'show', 'e12e059:database/schema.sql'], cwd=root, text=True)
    sql(baseline, 'rbac_check')
    migration = (root / 'database/migrations/036_rbac_domain_permissions.sql').read_text()
    sql(migration, 'rbac_check')
    # Simulate stale dangerous role grants, then reapply the static policy.
    sql("""INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
        SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p
        WHERE r.role_code IN ('OPERATOR','AUDITOR','CUSTOMER_SERVICE','USER')
        AND p.permission_code IN ('service-order:refund','service-order:reconcile','service-order:biometric')""", 'rbac_check')
    sql(migration, 'rbac_check')
    def grants(role):
        return set(sql("SELECT p.permission_code FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id=p.id JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code='" + role + "' AND p.permission_code LIKE 'service-%'", 'rbac_check').splitlines())
    assert grants('FINANCE') == {'service-order:read', 'service-order:refund', 'service-order:reconcile'}
    assert grants('AUDITOR') == {'service-product:read', 'service-order:read', 'service-project:read'}
    assert grants('CUSTOMER_SERVICE') == set()
    assert grants('USER') == set()
    assert len(grants('SUPER_ADMIN')) == 9
    assert len(grants('OPERATOR')) == 7
    assert 'service-order:biometric' in grants('OPERATOR')
    assert not {'service-order:refund', 'service-order:reconcile'} & grants('OPERATOR')
    fk = (root / 'database/migrations/037_rbac_foreign_keys.sql').read_text()
    sql(fk, 'rbac_check')
    def constraints(db):
        return sql("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema='" + db + "' AND table_name IN ('sys_user_role','sys_role_permission')")
    assert constraints('rbac_check') == '4'
    sql('CREATE DATABASE fresh_check;')
    sql((root / 'database/schema.sql').read_text(), 'fresh_check')
    assert constraints('fresh_check') == '4'
    matrix_query = "SELECT r.role_code,p.permission_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id ORDER BY 1,2"
    assert sql(matrix_query, 'fresh_check') == sql(matrix_query, 'rbac_check')
    # All orphan types fail BEFORE any FK is added, even when the last relation is bad.
    for index, orphan in enumerate([
        "INSERT INTO sys_user_role VALUES(999,1)",
        "INSERT INTO sys_user_role VALUES(1,999)",
        "INSERT INTO sys_role_permission VALUES(999,1)",
        "INSERT INTO sys_role_permission VALUES(1,999)",
    ]):
        db = 'orphan_' + str(index)
        sql('CREATE DATABASE ' + db)
        sql("CREATE TABLE sys_user(id BIGINT PRIMARY KEY); CREATE TABLE sys_role(id BIGINT PRIMARY KEY); CREATE TABLE sys_permission(id BIGINT PRIMARY KEY); CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT); CREATE TABLE sys_role_permission(role_id BIGINT,permission_id BIGINT); INSERT INTO sys_user VALUES(1); INSERT INTO sys_role VALUES(1); INSERT INTO sys_permission VALUES(1); " + orphan, db)
        sql(fk, db, False)
        assert constraints(db) == '0', 'Preflight must not partially alter schema'
        sql('DELETE FROM sys_user_role; DELETE FROM sys_role_permission;', db)
        sql(fk, db)
        sql(orphan, db, False)
        sql('INSERT INTO sys_user_role VALUES(1,1); INSERT INTO sys_role_permission VALUES(1,1); DELETE FROM sys_permission WHERE id=1;', db)
        assert sql('SELECT COUNT(*) FROM sys_role_permission', db) == '0'
        assert sql('SELECT COUNT(*) FROM sys_user', db) == '1'
        sql('INSERT INTO sys_permission VALUES(1); INSERT INTO sys_role_permission VALUES(1,1); DELETE FROM sys_role WHERE id=1;', db)
        assert sql('SELECT COUNT(*) FROM sys_user_role', db) == '0'
        assert sql('SELECT COUNT(*) FROM sys_role_permission', db) == '0'
        assert sql('SELECT COUNT(*) FROM sys_user', db) == '1'
        sql('INSERT INTO sys_role VALUES(1); INSERT INTO sys_user_role VALUES(1,1); DELETE FROM sys_user WHERE id=1;', db)
        assert sql('SELECT COUNT(*) FROM sys_user_role', db) == '0'
        assert sql('SELECT COUNT(*) FROM sys_role', db) == '1'
    print('PASS MySQL ' + sql('SELECT VERSION()') + ': 036 repeatability/static matrix, fresh-schema parity, 037 four-way orphan preflight, invalid writes denied, mapping-only cascades')


if __name__ == '__main__':
    runner.verify = verify
    runner.main()
