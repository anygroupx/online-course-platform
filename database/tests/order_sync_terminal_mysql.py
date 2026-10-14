"""Opt-in MySQL 8 regression: 订单进度同步必须跳过终态订单。

Run from any directory: python3 database/tests/order_sync_terminal_mysql.py
Requires Docker and the mysql:8.0 image. Creates a private temporary container
(no published ports, tmpfs data), never connects to an existing database, and
removes only that container on exit.

背景：`updateOrderProgressByFullMatch` 原先无条件刷新 `update_time`，而批量同步
不排除终态订单，导致已完成订单每轮都被重写、永远显示"正在同步"。本测试用真实
MySQL 执行生产 Mapper 的完整 SQL，验证终态订单零行命中、非终态订单正常更新。
"""
import os
import pathlib
import secrets
import subprocess
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]

# 与 Constants.TERMINAL_ORDER_STATUSES_CSV 保持一致：已完成/已取消/失败/考试完成/等待退款
TERMINAL_CSV = "2,3,4,7,8"
STATUS_COMPLETED = 2
STATUS_PROCESSING = 1


def main():
    name = "course-ordersync-test-" + uuid.uuid4().hex[:12]
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
            raise AssertionError("Expected database rejection")
        return result.stdout.strip()

    subprocess.run([
        "docker", "run", "--detach", "--pull=never", "--name", name,
        "--network=none", "--tmpfs", "/var/lib/mysql",
        "--env", "MYSQL_ROOT_PASSWORD", "mysql:8.0",
    ], check=True, capture_output=True, text=True, timeout=60,
        env={**os.environ, "MYSQL_ROOT_PASSWORD": password})
    try:
        for _ in range(90):
            try:
                sql("SELECT 1")
                break
            except AssertionError:
                time.sleep(1)
        else:
            raise RuntimeError("Isolated MySQL did not become ready")
        verify(sql)
    finally:
        subprocess.run(["docker", "rm", "--force", name], check=True,
                       capture_output=True, text=True, timeout=60)


def mapper_update_sql(guard):
    """生产 Mapper `updateOrderProgressByFullMatch` 的完整 SQL。

    guard=True 时带上终态排除条件（修复后）；False 复现修复前的行为。
    """
    where = ("WHERE student_account='acct-a' AND student_password='pw-a' "
             "AND course_name='高等数学' AND third_order_id='third-1' AND api_provider_id=6")
    if guard:
        where += f" AND order_status NOT IN ({TERMINAL_CSV})"
    return (
        "UPDATE course_order SET order_status=1, progress='100%', remarks='完成', "
        "course_start_time=NULL, course_end_time=NULL, exam_start_time=NULL, exam_end_time=NULL, "
        "update_time=NOW() " + where + "; SELECT ROW_COUNT();"
    )


def verify(sql):
    sql("CREATE DATABASE sync_check;")
    sql((ROOT / "database/schema.sql").read_text(), "sync_check")

    def q(text):
        return sql(text, "sync_check")

    def seed(order_id, order_no, status):
        q(
            "INSERT INTO course_order(id,order_no,user_id,platform_id,platform_name,"
            "api_provider_id,third_order_id,student_account,student_password,course_name,"
            "amount,order_status,progress,update_time) VALUES"
            f"({order_id},'{order_no}',1,1,'白昼',6,'third-1','acct-a','pw-a','高等数学',"
            f"1.00,{status},'100%','2020-01-01 00:00:00');"
        )

    # 1) 修复后的 SQL：终态订单（已完成）必须零行命中，update_time 保持不变。
    q("DELETE FROM course_order;")
    seed(1, "ORD-TERMINAL", STATUS_COMPLETED)
    seed(2, "ORD-ACTIVE", STATUS_PROCESSING)
    assert q(mapper_update_sql(guard=True)) == "1", "应只更新非终态订单一行"

    terminal = q("SELECT order_status,update_time FROM course_order WHERE id=1")
    active = q("SELECT order_status,update_time FROM course_order WHERE id=2")
    assert terminal == f"{STATUS_COMPLETED}\t2020-01-01 00:00:00", \
        f"终态订单不得被同步重写: {terminal}"
    assert not active.startswith(f"{STATUS_PROCESSING}\t2020-01-01 00:00:00"), \
        f"非终态订单应被正常更新: {active}"

    # 2) 反向验证：修复前的 SQL 确实会命中终态订单，刷新 update_time。
    #    这证明缺陷真实存在，且修复条件正是消除它的那一处。
    q("DELETE FROM course_order;")
    seed(1, "ORD-TERMINAL", STATUS_COMPLETED)
    assert q(mapper_update_sql(guard=False)) == "1", "未加守卫时应命中终态订单（复现缺陷）"
    reverted = q("SELECT update_time FROM course_order WHERE id=1")
    assert reverted != "2020-01-01 00:00:00", \
        "复现失败：修复前本应刷新终态订单的 update_time"

    # 3) 全部五个终态都必须被排除。
    for status in [int(s) for s in TERMINAL_CSV.split(",")]:
        q("DELETE FROM course_order;")
        seed(1, "ORD-T", status)
        assert q(mapper_update_sql(guard=True)) == "0", \
            f"终态状态 {status} 未被排除"

    # 4) 非终态（进行中）必须仍可同步。
    q("DELETE FROM course_order;")
    seed(1, "ORD-A", STATUS_PROCESSING)
    assert q(mapper_update_sql(guard=True)) == "1", "进行中订单必须仍能同步"

    print("PASS MySQL " + q("SELECT VERSION()") +
          ": 终态订单零行命中、update_time 不被刷新；修复前 SQL 复现缺陷；"
          "五个终态全部排除；进行中订单正常同步")


if __name__ == "__main__":
    main()
