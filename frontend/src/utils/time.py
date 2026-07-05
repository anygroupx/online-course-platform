import os, time, json
import requests
from urllib.parse import urlencode

BASE_URL = os.getenv("AQKS_BASE", "https://aqks.csuft.edu.cn")
USER_ID  = os.getenv("AQKS_USER_ID", "100173779")
DELTA    = int(os.getenv("AQKS_DELTA", "10"))  # 本次要累加的值

# 两条 Cookie 的“等号右边完整值”（百分号编码原样，不要再解码）
CK_SERVER = r'%7b%22Token%22%3a%22100173779-1-4983-2025-297-641-0-638979012006679094%22%2c%22TimeStamp%22%3a638979012006679094%2c%22UserName%22%3a%2220255249%22%2c%22Password%22%3anull%2c%22Name%22%3a%22%e6%96%bd%e8%9e%8d%e5%b2%9a%22%2c%22Type%22%3a1%2c%22Code%22%3a%2220255249%22%2c%22Sex%22%3a1%2c%22AdminLevel%22%3a0%2c%22DepartmentID%22%3a641%2c%22DepartmentName%22%3a%22%e5%9b%bd%e5%ae%b6%e5%85%ac%e5%9b%ad%e4%b8%8e%e6%97%85%e6%b8%b8%e5%ad%a6%e9%99%a2%22%2c%22SpecialtyID%22%3a297%2c%22SpecialtyName%22%3a%22%e5%9b%bd%e5%ae%b6%e5%85%ac%e5%9b%ad%e5%bb%ba%e8%ae%be%e4%b8%8e%e7%ae%a1%e7%90%86+%22%2c%22Grade%22%3a2025%2c%22ClassID%22%3a4983%2c%22ClassName%22%3anull%2c%22StudyTimes%22%3a%221110%22%2c%22MinTimeMinute%22%3a%22450%22%2c%22ID%22%3a100173779%2c%22IsDeleted%22%3afalse%2c%22Mender%22%3a0%2c%22UpdateTime%22%3anull%7d'

CK_DOUBLE = r'%257B%2522Token%2522%253A%2522100173779-1-4983-2025-297-641-0-638979012006679094%2522%252C%2522TimeStamp%2522%253A638979012006679000%252C%2522UserName%2522%253A%252220255249%2522%252C%2522Password%2522%253Anull%252C%2522Name%2522%253A%2522%25E6%2596%25BD%25E8%259E%258D%25E5%25B2%259A%2522%252C%2522Type%2522%253A1%252C%2522Code%2522%253A%252220255249%2522%252C%2522Sex%2522%253A1%252C%2522AdminLevel%2522%253A0%252C%2522DepartmentID%2522%253A641%252C%2522DepartmentName%2522%253A%2522%25E5%259B%25BD%25E5%25AE%25B6%25E5%2585%25AC%25E5%259B%25AD%25E4%25B8%258E%25E6%2597%2585%25E6%25B8%25B8%25E5%25AD%25A6%25E9%2599%25A2%2522%252C%2522SpecialtyID%2522%253A297%252C%2522SpecialtyName%2522%253A%2522%25E5%259B%25BD%25E5%25AE%25B6%25E5%2585%25AC%25E5%259B%25AD%25E5%25BB%25BA%25E8%25AE%25BE%25E4%25B8%258E%25E7%25AE%25A1%25E7%2590%2586%2520%2522%252C%2522Grade%2522%253A2025%252C%2522ClassID%2522%253A4983%252C%2522ClassName%2522%253Anull%252C%2522StudyTimes%2522%253A%25221110%2522%252C%2522MinTimeMinute%2522%253A%2522450%2522%252C%2522ID%2522%253A100173779%252C%2522IsDeleted%2522%253Afalse%252C%2522Mender%2522%253A0%252C%2522UpdateTime%2522%253Anull%257D'
# 可选：读取当前累计值的接口（请替换为你们实际的读接口）
READ_ENDPOINT = os.getenv("AQKS_READ_URL", f"{BASE_URL}/api/LoginTimesGet")
READ_PARAM_NAME = os.getenv("AQKS_READ_PARAM", "UserID")  # 如果读端的参数名不同，可通过环境变量改

def make_session():
    s = requests.Session()
    s.headers.update({
        "Accept": "application/json, text/javascript, */*; q=0.01",
        "Accept-Language": "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:144.0) Gecko/20100101 Firefox/144.0",
        "Referer": f"{BASE_URL}/client_pc/exone.html",
        "X-Requested-With": "XMLHttpRequest",
    })
    # 先访问根路径，拿站点级 Cookie（如 ASP.NET_SessionId）
    s.get(BASE_URL + "/", timeout=10)

    # 注入两条业务 Cookie（域与 path 按站点惯例设置）
    if not CK_SERVER or not CK_DOUBLE:
        raise RuntimeError("缺少 CK_LOGININFO_SERVER / CK_LOGININFO_DOUBLE 环境变量")
    s.cookies.set("LoginUserInfo_SYSAQ_Server", CK_SERVER, domain="aqks.csuft.edu.cn", path="/")
    s.cookies.set("LoginUserInfo_SYSAQ",        CK_DOUBLE, domain="aqks.csuft.edu.cn", path="/")
    return s

def read_current(s: requests.Session) -> int | None:
    """读当前累计值；若无读端接口，返回 None（你可以改为 DB 校验）"""
    if not READ_ENDPOINT:
        return None
    # 读端可能是 GET，也可能是 POST，这里先试 GET，再回退 POST
    params = {READ_PARAM_NAME: USER_ID}
    r = s.get(READ_ENDPOINT, params=params, timeout=10)
    if r.status_code == 404:
        # 尝试 POST 读
        r = s.post(READ_ENDPOINT, data=params, timeout=10)
    r.raise_for_status()
    ctype = (r.headers.get("content-type") or "").lower()
    if "json" in ctype:
        data = r.json()
        # 下面三种常见形态，按你们实际返回改字段
        if isinstance(data, dict):
            for key in ("StudyTimes", "studyTimes", "TotalMinutes", "LoginCount"):
                if key in data:
                    return int(data[key])
        if isinstance(data, list) and data and isinstance(data[0], dict):
            for key in ("StudyTimes", "studyTimes", "TotalMinutes", "LoginCount"):
                if key in data[0]:
                    return int(data[0][key])
        raise AssertionError(f"读端JSON无法找到累计字段: {json.dumps(data, ensure_ascii=False)[:400]}")
    # 如果不是 JSON，比如直接返回纯数字：
    text = r.text.strip()
    if text.isdigit():
        return int(text)
    raise AssertionError(f"读端返回非预期: {text[:400]}")

def write_delta(s: requests.Session, delta: int, method_hint: str = "GET"):
    """对 LoginTimesSet 写入 delta；默认 GET，失败再试 POST"""
    endpoint = f"{BASE_URL}/api/LoginTimesSet"
    params = {"UserID": USER_ID, "StudyTimes": str(delta)}
    if method_hint.upper() == "POST":
        r = s.post(endpoint, data=params, timeout=10)
    else:
        r = s.get(endpoint, params=params, timeout=10)
        if r.status_code in (405, 500, 404):  # 若 GET 不行，试试 POST
            r = s.post(endpoint, data=params, timeout=10)
    # 常见成功：200/204，且返回 JSON {success:true} 或空
    if r.status_code not in (200, 204):
        raise AssertionError(f"写入失败：HTTP {r.status_code} {r.text[:400]}")
    # 如果是 JSON，校验 success 字段
    ctype = (r.headers.get("content-type") or "").lower()
    if "json" in ctype and r.text.strip():
        try:
            data = r.json()
            if isinstance(data, dict) and ("success" in data or "Success" in data):
                ok = data.get("success", data.get("Success"))
                if ok is False:
                    raise AssertionError(f"接口返回失败：{data}")
        except Exception:
            pass
    return r

def main():
    s = make_session()
    # 读初始值（可选）
    before = None
    try:
        before = read_current(s)
        if before is not None:
            print("初始累计：", before)
    except Exception as e:
        print("读初始值失败（继续写入验证返回码）：", e)

    # 写入 +DELTA
    write_delta(s, DELTA)
    time.sleep(0.8)  # 给服务端一点处理/缓存时间

    after = None
    try:
        after = read_current(s)
        if after is not None:
            print("写入后累计：", after)
            if before is not None:
                assert after - before == DELTA, f"累计不一致：期望 +{DELTA}，实际 {after-before}"
                print("✅ 数值校验通过：+%d" % DELTA)
    except Exception as e:
        print("读回校验失败：", e)

    # 幂等性/重复提交测试：同一 Δ 再打一次，观察是否累加或被拒
    try:
        write_delta(s, DELTA)
        time.sleep(0.5)
        again = read_current(s)
        if after is not None and again is not None:
            print("再次写入后累计：", again)
            # 如果系统要求幂等，这里可能不变；如果允许重复累加，这里应再 +DELTA
            print("⚠️ 幂等性结果：", "不变(幂等)" if again == after else "再次累加(允许重复)")
    except Exception as e:
        print("幂等性测试失败：", e)

if __name__ == "__main__":
    main()
