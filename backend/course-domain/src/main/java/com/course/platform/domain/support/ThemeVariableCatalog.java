package com.course.platform.domain.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 系统主题变量目录。
 * 颜色变量与前端语义 CSS Token 一一对应；液态玻璃主题额外保存受控的材质参数。
 */
public final class ThemeVariableCatalog {

    public static final String LIGHT_TYPE = "theme_color_light";
    public static final String DARK_TYPE = "theme_color_dark";
    public static final String LIQUID_GLASS_TYPE = "theme_color_liquid_glass";

    private static final Pattern CSS_COLOR_PATTERN = Pattern.compile(
            "(?i)^(#(?:[0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})|rgba?\\([0-9\\s.,%+\\-]+\\)|hsla?\\([0-9\\s.,%+\\-]+\\)|transparent)$"
    );

    public record Definition(
            String key,
            String name,
            String description,
            int sortOrder,
            String lightValue,
            String darkValue,
            String liquidGlassValue
    ) {
        public String valueForType(String variableType) {
            if (DARK_TYPE.equals(variableType)) {
                return darkValue;
            }
            if (LIQUID_GLASS_TYPE.equals(variableType)) {
                return liquidGlassValue;
            }
            return lightValue;
        }
    }

    /**
     * 液态玻璃材质参数定义。
     * 数值边界与前端编辑器一致，避免异常配置放大纹理成本或破坏可读性。
     */
    public record MaterialDefinition(
            String key,
            String name,
            String description,
            int sortOrder,
            String defaultValue,
            Double minValue,
            Double maxValue
    ) {
    }

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("brand_primary", "品牌主色", "主按钮、选中态和主要链接", 10, "#0f6cbd", "#479ef5", "#3463ce"),
            new Definition("brand_primary_hover", "主色悬停", "主要操作的悬停状态", 20, "#115ea3", "#62abf5", "#2952b3"),
            new Definition("brand_primary_pressed", "主色按下", "主要操作的按下状态", 30, "#0c3b5e", "#2886de", "#1f3f8c"),
            new Definition("brand_cyan", "品牌青色", "辅助品牌色和渐变终点", 40, "#00b7c3", "#38d5de", "#22b8cf"),
            new Definition("brand_violet", "品牌紫色", "强调装饰和数据视觉辅助色", 50, "#7160e8", "#9c89ff", "#6366f1"),
            new Definition("primary_gradient_start", "渐变起点", "主品牌渐变的起始颜色", 60, "#0f6cbd", "#479ef5", "#3463ce"),
            new Definition("primary_gradient_end", "渐变终点", "主品牌渐变的结束颜色", 70, "#00b7c3", "#38d5de", "#22b8cf"),
            new Definition("color_success", "成功色", "成功、完成和正常状态", 110, "#107c10", "#54b054", "#37a06f"),
            new Definition("color_warning", "警告色", "提醒、等待和风险状态", 120, "#f7630c", "#f9a825", "#d97706"),
            new Definition("color_danger", "危险色", "失败、删除和高风险状态", 130, "#c50f1f", "#f1707b", "#dc2626"),
            new Definition("color_info", "信息色", "一般信息和辅助提示", 140, "#0078d4", "#62abf5", "#3463ce"),
            new Definition("bg_body", "页面背景", "应用主内容区的底色", 210, "#eef4fb", "#07111f", "#f5f6f8"),
            new Definition("bg_card", "卡片背景", "常规卡片和容器背景", 220, "rgba(255, 255, 255, 0.78)", "rgba(14, 29, 48, 0.76)", "rgba(255, 255, 255, 0.65)"),
            new Definition("bg_card_hover", "卡片悬停", "可交互卡片的悬停背景", 230, "rgba(255, 255, 255, 0.94)", "rgba(20, 40, 64, 0.90)", "rgba(255, 255, 255, 0.82)"),
            new Definition("bg_overlay", "遮罩背景", "浮层后方的半透明遮罩", 240, "rgba(244, 248, 253, 0.78)", "rgba(7, 17, 31, 0.80)", "rgba(245, 246, 248, 0.75)"),
            new Definition("surface_solid", "实色表面", "输入框、弹层等不透明表面", 250, "#ffffff", "#101d2e", "#ffffff"),
            new Definition("surface_mica", "云母表面", "页面级柔和半透明材质", 260, "rgba(242, 247, 252, 0.82)", "rgba(11, 24, 41, 0.86)", "rgba(245, 247, 250, 0.80)"),
            new Definition("surface_acrylic", "亚克力表面", "浮动卡片和导航半透明材质", 270, "rgba(255, 255, 255, 0.68)", "rgba(17, 35, 57, 0.68)", "rgba(255, 255, 255, 0.52)"),
            new Definition("text_primary", "主要文字", "标题和高强调正文", 310, "#17202b", "#f5f8fc", "#252b35"),
            new Definition("text_regular", "常规文字", "正文和表单内容", 320, "#354052", "#d6e0ec", "#3b4453"),
            new Definition("text_secondary", "次要文字", "说明、辅助信息和元数据", 330, "#5c6675", "#a8b5c5", "#717a88"),
            new Definition("text_placeholder", "占位文字", "输入提示和弱化内容", 340, "#737d8c", "#8391a3", "#9aa2af"),
            new Definition("text_on_brand", "品牌色上文字", "主色按钮与品牌色背景上的文字", 350, "#ffffff", "#ffffff", "#ffffff"),
            new Definition("border_color", "主要边框", "控件和卡片的常规描边", 360, "rgba(74, 91, 113, 0.22)", "rgba(157, 192, 231, 0.24)", "rgba(205, 210, 219, 0.72)"),
            new Definition("border_color_light", "弱边框", "分隔线和低强调描边", 370, "rgba(74, 91, 113, 0.12)", "rgba(157, 192, 231, 0.13)", "rgba(223, 226, 231, 0.60)"),
            new Definition("stroke_highlight", "表面高光", "半透明表面的顶部高光", 380, "rgba(255, 255, 255, 0.92)", "rgba(209, 231, 255, 0.20)", "rgba(255, 255, 255, 0.95)"),
            new Definition("focus_ring", "焦点光环", "键盘操作时的可访问性焦点提示", 390, "rgba(15, 108, 189, 0.32)", "rgba(71, 158, 245, 0.40)", "rgba(52, 99, 206, 0.35)")
    );

    public static final List<MaterialDefinition> MATERIAL_DEFINITIONS = List.of(
            new MaterialDefinition("glass_renderer_mode", "渲染模式", "自动选择增强折射或兼容毛玻璃", 510, "auto", null, null),
            new MaterialDefinition("glass_refraction", "折射强度", "控制边缘背景位移强度", 520, "56", 0.0, 100.0),
            new MaterialDefinition("glass_bevel", "斜面宽度", "控制产生折射与高光的边缘范围", 530, "22", 2.0, 48.0),
            new MaterialDefinition("glass_blur", "光学模糊", "增强折射路径中的轻微模糊", 540, "0.35", 0.0, 8.0),
            new MaterialDefinition("glass_dispersion", "色散强度", "控制边缘 RGB 通道的微弱分离", 550, "1.2", 0.0, 5.0),
            new MaterialDefinition("glass_radius", "表面圆角", "控制主壳层和玻璃卡片的圆角", 560, "24", 8.0, 48.0),
            new MaterialDefinition("glass_tint", "材质着色", "叠加在实时背景上的低透明度颜色", 570, "rgba(255,255,255,0.018)", null, null),
            new MaterialDefinition("glass_surface_opacity", "表面透明度", "控制普通业务卡片的透明表面强度", 580, "0.65", 0.12, 0.92),
            new MaterialDefinition("glass_backdrop_blur", "兼容模糊", "CSS 降级路径与普通业务表面的背景模糊", 590, "16", 4.0, 32.0),
            new MaterialDefinition("glass_saturation", "背景饱和度", "控制玻璃后方内容的饱和度百分比", 600, "108", 80.0, 160.0)
    );

    private static final Map<String, Definition> DEFINITION_BY_KEY = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(Definition::key, Function.identity()));
    private static final Map<String, MaterialDefinition> MATERIAL_DEFINITION_BY_KEY = MATERIAL_DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(MaterialDefinition::key, Function.identity()));
    private static final Set<String> TYPES = Set.of(LIGHT_TYPE, DARK_TYPE, LIQUID_GLASS_TYPE);

    private ThemeVariableCatalog() {
    }

    public static boolean isThemeType(String variableType) {
        return TYPES.contains(variableType);
    }

    public static boolean isKnownKey(String variableType, String variableKey) {
        return DEFINITION_BY_KEY.containsKey(variableKey)
                || (LIQUID_GLASS_TYPE.equals(variableType) && MATERIAL_DEFINITION_BY_KEY.containsKey(variableKey));
    }

    public static Definition getDefinition(String variableKey) {
        return DEFINITION_BY_KEY.get(variableKey);
    }

    public static MaterialDefinition getMaterialDefinition(String variableKey) {
        return MATERIAL_DEFINITION_BY_KEY.get(variableKey);
    }

    public static boolean isSupportedColor(String value) {
        return value != null && CSS_COLOR_PATTERN.matcher(value.trim()).matches();
    }

    public static boolean isSupportedValue(String variableType, String variableKey, String value) {
        if (value == null) {
            return false;
        }
        if (DEFINITION_BY_KEY.containsKey(variableKey)) {
            return isSupportedColor(value);
        }
        if (!LIQUID_GLASS_TYPE.equals(variableType)) {
            return false;
        }
        MaterialDefinition definition = MATERIAL_DEFINITION_BY_KEY.get(variableKey);
        if (definition == null) {
            return false;
        }
        String normalized = value.trim();
        if ("glass_renderer_mode".equals(variableKey)) {
            return Set.of("auto", "svg", "css").contains(normalized);
        }
        if ("glass_tint".equals(variableKey)) {
            return isSupportedColor(normalized);
        }
        try {
            double numericValue = Double.parseDouble(normalized);
            return Double.isFinite(numericValue)
                    && numericValue >= definition.minValue()
                    && numericValue <= definition.maxValue();
        } catch (NumberFormatException error) {
            return false;
        }
    }

    public static boolean isMaterialKey(String variableKey) {
        return MATERIAL_DEFINITION_BY_KEY.containsKey(variableKey);
    }

    public static int maxDefinitionsPerType() {
        return DEFINITIONS.size() + MATERIAL_DEFINITIONS.size();
    }

    public static List<String> types() {
        return List.of(LIGHT_TYPE, DARK_TYPE, LIQUID_GLASS_TYPE);
    }
}
