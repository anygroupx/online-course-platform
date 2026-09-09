package com.course.platform.infra.integration;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.domain.vo.plugin.PluginIntegrationDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Curated static evidence. Runtime does not need access to archives or extracted source files. */
@Component
public class PluginResearchCatalog {
    private final List<PluginIntegrationDescriptor> integrations;

    public PluginResearchCatalog(PluginConnectorRegistry registry) {
        integrations = List.of(
            entry(registry, "P01", "0510闪电三件套.zip", "闪电三件套", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "flash", null,
                List.of("三项目报价", "账号预检与短信", "任务计划、暂停与延期", "日志与退款"),
                List.of("原生下单/售后及短信授权已实现；未部署和真实上游验收", "临时授权与用户/商品绑定；规则刷新会使旧报价失效，写请求不自动重放"),
                List.of("flash/api.php:13–29 / 34–54 / 116–117", "flash/api.php:245 / 343 / 469")),
            entry(registry, "P02", "benzs.zip", "Benz 同步桥", "课程同步", "PLAINTEXT", "EXISTING", "27", null,
                List.of("分类与商品同步", "仅更新现有价格与说明", "增量批量进度"),
                List.of("现有价格/说明独立预览与原子更新已实现；不新增课程，不动名称/分类/状态；未部署", "批量进度错误不推进水位；缺失单号恢复与上游分页含义仍需协议证据，不运行旧 cron"),
                List.of("benzcron.php:91–120", "benztb.php:18–36 / 38–74")),
            entry(registry, "P03", "heisha_sdxy_user.zip", "黑鲨闪动校园", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "heisha", null,
                List.of("四类商品", "账号预检、人脸状态与采集链接", "订单同步与剩余次数退款"),
                List.of("四类原生下单、状态同步及官方人脸授权已实现；未部署/真实联调", "照片仅由已批准的官方采集端处理；短期会话绑定用户与商品，退款采用人工核对入账"),
                List.of("heisha/heisha.api.php:35–47 / 112–117", "heisha/heisha.refund.php:47–76")),
            entry(registry, "P04", "jiguang_user.zip", "极光", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "jiguang", null,
                List.of("商品与学校目录", "次数 × 公里计价", "订单日志", "退款 / 加次数预览确认"),
                List.of("原生商品/学校/下单/日志/增次/退款已实现；未部署/真实联调", "预览绑定价格快照、订单版本与持久化单次派发；未确认回执不自动退款"),
                List.of("jiguang/jiguang.api.php:106–127 / 170 / 244–451", "index/jiguang.php:393–399")),
            entry(registry, "P05", "ssbenz.zip", "永夜 / ssbenz", "运动计划", "MIXED", "NEEDS_PROTOCOL", null, null,
                List.of("运动世界 / 校步点桥接", "学校、学生与订单", "退单与同步"),
                List.of("Xend 核心与混淆 JS，不执行或破解", "缺完整表定义及可验证协议，需授权源码或官方文档"),
                List.of("xbd/ydapi/add.php:172–240", "xbd/yongyeyd_api.php:1–10", "ydsj/ydapi/add.php:1–10")),
            entry(registry, "P06", "sxdk_tw.zip", "实习计划套娃", "实习计划", "PLAINTEXT", "NATIVE_PARTIAL", "sxdk_tw", null,
                List.of("周期计划与多平台表单", "学校选择与计划建议", "续期、退款与最近记录", "本人接收验证与状态通知"),
                List.of("原生下单、周期编辑、显式建议采用、日志与售后已实现；平台 ShowDoc 通知须本人验证，默认关闭且未部署", "通知不是校友帮微信授权；原绑定接口只改状态并收费，不照搬；合同单价与真实联调仍需核实"),
                List.of("api.php:118–690 / 350–391 / 507–543", "copilot.php:498–566", "qingka_wangke_sxdk.sql")),
            entry(registry, "P07", "syyv5 (1).tar.gz", "syyv5 多项目中心", "项目与账务", "PLAINTEXT", "NATIVE_PARTIAL", "syyv5", null,
                List.of("项目与客户密钥绑定", "余额兑换、流水与统计", "工单与补偿审核", "OpenAPI"),
                List.of("原生项目发布、零余额开户、冻结费率充值/转回与人工核对已实现；未部署/真实联调", "上游文字工单/审核、本平台多客户子账、主/客户密钥、原生 REST OpenAPI 及下游本地售后已实现（审核不入账）；未部署/真实联调。客户消费、附件、安全上游登录与未知新上游工单归属恢复仍待完成"),
                List.of("UniversalAPI.php:45–145 / 1179–1255 / 1669–1707", "api.php:796–799 / 875–879", "openapi.php:18–47")),
            entry(registry, "P08", "toc模板专属对接鲸鱼文件.zip", "鲸鱼 · TOC 版", "运动计划", "OPAQUE", "NEEDS_PROTOCOL", null, null,
                List.of("乐跑 / Keep / 运动 / 体育 UI", "区域、周期与任务计划", "延期与退款按钮"),
                List.of("API 与四个 cron 不透明，按钮不是协议证据", "不加载硬编码授权域名，需正式接口文档"),
                List.of("jingyu/api.php:1–3", "jingyu/cron_keep.php:1–3", "index/bdlp.php", "jingyu/jingyu_tables.sql")),
            entry(registry, "P09", "二开appui对接.zip", "appui 实习平台", "实习计划", "OPAQUE", "NEEDS_PROTOCOL", null, null,
                List.of("静态平台目录", "周期、报表、上下班时间与地址", "查询、续期与退款 UI"),
                List.of("API / cron 不透明；静态 course.json 不是实时价目表", "不猜测成功码或运行旧建表脚本"),
                List.of("appui/course.json", "index/appui.php:44–122", "appui/api.php:1–3")),
            entry(registry, "P10", "无心闪动0312.zip", "无心闪动", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "wuxin", null,
                List.of("学校与订单配置", "编辑、增次与重分配", "日志与同步"),
                List.of("原生授权码/计划/下单/编辑/增次/退款/重分配已实现；未部署/真实联调", "严格区分各动作成功码；没有服务端协议证据的动作不开放，退款次数缺失转人工核对"),
                List.of("wuxin/api.php", "wuxin/api01.php:15–61", "wuxin/wx_sdxytb.php:13–31")),
            entry(registry, "P11", "通用鲸鱼套娃对接.zip", "鲸鱼 · 通用版", "运动计划", "DUPLICATE", "DUPLICATE", null, "P08",
                List.of("与 TOC 版同一协议家族", "增加授权、设备绑定与推送说明"),
                List.of("25 个文件中 24 个与 P08 字节一致", "唯一差异为 bdlp 页面，不重复注册后端连接器"),
                List.of("index/bdlp.php:1–5 / 235–254", "与 P08 比对：24/25 成员相同")),
            entry(registry, "P12", "雷电_v1.2_二开对接.zip", "雷电 v1.2", "运动计划", "OPAQUE", "NEEDS_PROTOCOL", null, null,
                List.of("四类跑步项目 UI", "区域、周期、时间与里程", "取消订单按钮"),
                List.of("API / cron 不透明，取消不能直接当退款", "需合法源码或正式协议后再适配"),
                List.of("index/ldrun.php:140–230", "ldrun/api.php:1–3", "ldrun/cron.php:1–3"))
        );
    }

    public List<PluginIntegrationDescriptor> list() { return integrations; }

    public PluginIntegrationDescriptor find(String id) {
        return integrations.stream().filter(item -> item.id().equals(id)).findFirst().orElse(null);
    }

    private static PluginIntegrationDescriptor entry(PluginConnectorRegistry registry, String id, String archive,
            String name, String category, String evidenceLevel, String status, String type, String duplicateOf,
            List<String> observed, List<String> blockers, List<String> evidence) {
        PluginReadOnlyConnector connector = registry.getConnector(type);
        List<String> available = connector == null ? List.of()
                : connector.supportsSchools() ? List.of("CATALOG", "SCHOOLS") : List.of("CATALOG");
        if ("P02".equals(id)) available = List.of("COURSE_CATALOG", "BATCH_PROGRESS");
        if ("P07".equals(id)) available = List.of("PROJECT_CENTER");
        if ("READ_ONLY".equals(status) && connector == null) status = "PLANNED";
        return new PluginIntegrationDescriptor(id, archive, name, category, evidenceLevel, status, type, duplicateOf,
                observed, blockers, evidence, available, connector == null ? List.of() : connector.projects());
    }
}
