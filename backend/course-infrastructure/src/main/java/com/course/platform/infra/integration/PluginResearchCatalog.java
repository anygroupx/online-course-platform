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
                List.of("分类与商品同步", "仅更新现有价格与说明", "增量批量进度", "核对并恢复执行编号"),
                List.of("已支持现有价格与说明的预览更新；执行编号恢复需双权限核对与确认，尚未发布", "完整身份一致后只恢复指定编号，不重新下单或扣费；自动查找缺失编号及完整分页仍待验证"),
                List.of("benzcron.php:91–120", "benztb.php:18–36 / 38–74")),
            entry(registry, "P03", "heisha_sdxy_user.zip", "黑鲨闪动校园", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "heisha", null,
                List.of("四类商品", "账号预检、人脸状态与采集链接", "订单同步与剩余次数退款"),
                List.of("四类原生下单、状态同步及官方人脸授权已实现；未部署/真实联调", "照片仅由已批准的官方采集端处理；短期会话绑定用户与商品，退款采用人工核对入账"),
                List.of("heisha/heisha.api.php:35–47 / 112–117", "heisha/heisha.refund.php:47–76")),
            entry(registry, "P04", "jiguang_user.zip", "极光", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "jiguang", null,
                List.of("商品与学校目录", "次数 × 公里计价", "订单日志", "退款 / 加次数预览确认"),
                List.of("原生商品/学校/下单/日志/增次/退款已实现；未部署/真实联调", "预览绑定价格快照、订单版本与持久化单次派发；未确认回执不自动退款"),
                List.of("jiguang/jiguang.api.php:106–127 / 170 / 244–451", "index/jiguang.php:393–399")),
            entry(registry, "P05", "ssbenz.zip", "永夜 / ssbenz", "运动计划", "MIXED", "NATIVE_PARTIAL", "ssbenz_xbd", null,
                List.of("两种方案报价", "总公里计划下单", "提交状态核对"),
                List.of("明文报价、总公里下单和提交状态核对已实现；尚未发布和业务验收", "加密部分尚待核实；不提供账号预检、完成进度或自动退款，提交记录不代表执行完成"),
                List.of("xbd/ydapi/school.php:49–80", "xbd/ydapi/add.php:48–240", "xbd/ydapi/order.php:45–83", "ydsj/ydapi/add.php:1–10")),
            entry(registry, "P06", "sxdk_tw.zip", "实习计划套娃", "实习计划", "PLAINTEXT", "NATIVE_PARTIAL", "sxdk_tw", null,
                List.of("周期计划与多平台表单", "学校选择与计划建议", "续期、退款与最近记录", "本人接收验证与状态通知"),
                List.of("原生下单、周期编辑、显式建议采用、日志与售后已实现；平台 ShowDoc 通知须本人验证，默认关闭且未部署", "通知不是校友帮微信授权；原绑定接口只改状态并收费，不照搬；合同单价与真实联调仍需核实"),
                List.of("api.php:118–690 / 350–391 / 507–543", "copilot.php:498–566", "qingka_wangke_sxdk.sql")),
            entry(registry, "P07", "syyv5 (1).tar.gz", "syyv5 多项目中心", "项目与账务", "PLAINTEXT", "NATIVE_PARTIAL", "syyv5", null,
                List.of("项目与客户密钥绑定", "余额兑换、流水与统计", "工单与补偿审核", "OpenAPI"),
                List.of("项目发布、零额度或带初始额度开户、冻结单价充值/转回与人工核对已实现；带初始额度开户尚未发布，功能默认关闭，尚未完成业务验收", "项目工单、客户售后、客户额度账户、主/客户密钥与 OpenAPI 已实现；支持私有 PNG/JPEG 图文、本人用量、双权限概览、经营者明细和资金记录。审核不自动付款，项目账户与客户额度分别记录；客户额度尚不能用于购买服务。项目安全登录、项目账户密钥管理、其他附件及待核实新工单的归属确认仍待补充，尚未全部开放"),
                List.of("UniversalAPI.php:45–145 / 573–747 / 1179–1255 / 1669–1707", "api.php:796–799 / 875–879 / 900–932", "openapi.php:18–47")),
            entry(registry, "P08", "toc模板专属对接鲸鱼文件.zip", "鲸鱼 · TOC 版", "运动计划", "MIXED", "NATIVE_PARTIAL", "jingyu", null,
                List.of("Keep 与步道乐跑报价、账号查询与下单", "逐次任务安排、延期与暂停恢复", "双编号核对与人工退款入账"),
                List.of("两个项目的 Java 业务流程已实现；页面与验收仍在完善，尚未部署和真实联调", "运动与体育项目的学校归属、图形验证、规则更新、补跑计费仍待补齐；设备授权和推送没有独立可核实接口", "退款没有原子数量回执，必须核对实际数量；不执行 PHP 或定时脚本，不重发结果不确定的订单"),
                List.of("jingyu/api.php（静态核对的固定表单协议）", "index/keep.php", "index/bdlp.php", "jingyu/jingyu_tables.sql")),
            entry(registry, "P09", "二开appui对接.zip", "AppUI 实习打卡", "实习计划", "MIXED", "NATIVE_PARTIAL", "appui", null,
                List.of("九类实习项目实时报价", "学校、账号查询与天数下单", "执行安排编辑、续期与退款", "天数核对与签到日志"),
                List.of("Java/Vue 天数下单和售后已实现；尚未部署和真实联调", "天数不代表签到成功；退款需明确回执，结果不确定时保留账务并核对，不自动重试"),
                List.of("appui/api.php（静态还原的固定表单协议）", "index/appui.php", "appui/course.json（项目编号，不使用默认价）")),
            entry(registry, "P10", "无心闪动0312.zip", "无心闪动", "运动计划", "PLAINTEXT", "NATIVE_PARTIAL", "wuxin", null,
                List.of("学校与订单配置", "编辑、增次与重分配", "日志与同步"),
                List.of("原生授权码/计划/下单/编辑/增次/退款/重分配已实现；未部署/真实联调", "严格区分各动作成功码；没有服务端协议证据的动作不开放，退款次数缺失转人工核对"),
                List.of("wuxin/api.php", "wuxin/api01.php:15–61", "wuxin/wx_sdxytb.php:13–31")),
            entry(registry, "P11", "通用鲸鱼套娃对接.zip", "鲸鱼 · 通用版", "运动计划", "DUPLICATE", "DUPLICATE", null, "P08",
                List.of("与 TOC 版同一协议家族", "增加授权、设备绑定与推送说明"),
                List.of("25 个文件中 24 个与 P08 字节一致", "唯一差异为 bdlp 页面，不重复注册后端连接器"),
                List.of("index/bdlp.php:1–5 / 235–254", "与 P08 比对：24/25 成员相同")),
            entry(registry, "P12", "雷电_v1.2_二开对接.zip", "雷电 v1.2", "运动计划", "MIXED", "NATIVE_PARTIAL", "leidian", null,
                List.of("四种运动项目报价与下单", "跑区规则、执行安排与任务时间", "次数核对、执行记录与成绩查询文字", "取消订单与独立退款核对"),
                List.of("Java/Vue 固定协议功能已实现；尚未部署和真实联调", "使用次数不代表成绩完成；取消仅进入退款核对，不自动退回余额", "不执行 PHP 或 cron；不确定结果保留原编号，人工核对需要两个独立订单编号"),
                List.of("index/ldrun.php:140–230", "ldrun/api.php（离线核对的固定表单协议）", "ldrun/cron.php（不作为运行依赖）"))
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
