package com.course.platform.domain.support;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 系统主题颜色变量目录。
 * 变量键与前端语义 CSS Token 一一对应，数据库只保存可编辑色值，复合渐变由前端派生。
 */
public final class ThemeVariableCatalog {

    public static final String LIGHT_TYPE = "theme_color_light";
    public static final String DARK_TYPE = "theme_color_dark";

    private static final Pattern CSS_COLOR_PATTERN = Pattern.compile(
            "(?i)^(#(?:[0-9a-f]{3}|[0-9a-f]{4}|[0-9a-f]{6}|[0-9a-f]{8})|rgba?\\([0-9\\s.,%+\\-]+\\)|hsla?\\([0-9\\s.,%+\\-]+\\)|transparent)$"
    );

    public record Definition(
            String key,
            String name,
            String description,
            int sortOrder,
            String lightValue,
            String darkValue
    ) {
        public String valueForType(String variableType) {
            return DARK_TYPE.equals(variableType) ? darkValue : lightValue;
        }
    }

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("brand_primary", "品牌主色", "主按钮、选中态和主要链接", 10, "#0f6cbd", "#479ef5"),
            new Definition("brand_primary_hover", "主色悬停", "主要操作的悬停状态", 20, "#115ea3", "#62abf5"),
            new Definition("brand_primary_pressed", "主色按下", "主要操作的按下状态", 30, "#0c3b5e", "#2886de"),
            new Definition("brand_cyan", "品牌青色", "辅助品牌色和渐变终点", 40, "#00b7c3", "#38d5de"),
            new Definition("brand_violet", "品牌紫色", "强调装饰和数据视觉辅助色", 50, "#7160e8", "#9c89ff"),
            new Definition("primary_gradient_start", "渐变起点", "主品牌渐变的起始颜色", 60, "#0f6cbd", "#479ef5"),
            new Definition("primary_gradient_end", "渐变终点", "主品牌渐变的结束颜色", 70, "#00b7c3", "#38d5de"),
            new Definition("color_success", "成功色", "成功、完成和正常状态", 110, "#107c10", "#54b054"),
            new Definition("color_warning", "警告色", "提醒、等待和风险状态", 120, "#f7630c", "#f9a825"),
            new Definition("color_danger", "危险色", "失败、删除和高风险状态", 130, "#c50f1f", "#f1707b"),
            new Definition("color_info", "信息色", "一般信息和辅助提示", 140, "#0078d4", "#62abf5"),
            new Definition("bg_body", "页面背景", "应用主内容区的底色", 210, "#eef4fb", "#07111f"),
            new Definition("bg_card", "卡片背景", "常规卡片和容器背景", 220, "rgba(255, 255, 255, 0.78)", "rgba(14, 29, 48, 0.76)"),
            new Definition("bg_card_hover", "卡片悬停", "可交互卡片的悬停背景", 230, "rgba(255, 255, 255, 0.94)", "rgba(20, 40, 64, 0.90)"),
            new Definition("bg_overlay", "遮罩背景", "浮层后方的半透明遮罩", 240, "rgba(244, 248, 253, 0.78)", "rgba(7, 17, 31, 0.80)"),
            new Definition("surface_solid", "实色表面", "输入框、弹层等不透明表面", 250, "#ffffff", "#101d2e"),
            new Definition("surface_mica", "云母表面", "页面级柔和半透明材质", 260, "rgba(242, 247, 252, 0.82)", "rgba(11, 24, 41, 0.86)"),
            new Definition("surface_acrylic", "亚克力表面", "浮动卡片和导航半透明材质", 270, "rgba(255, 255, 255, 0.68)", "rgba(17, 35, 57, 0.68)"),
            new Definition("text_primary", "主要文字", "标题和高强调正文", 310, "#17202b", "#f5f8fc"),
            new Definition("text_regular", "常规文字", "正文和表单内容", 320, "#354052", "#d6e0ec"),
            new Definition("text_secondary", "次要文字", "说明、辅助信息和元数据", 330, "#5c6675", "#a8b5c5"),
            new Definition("text_placeholder", "占位文字", "输入提示和弱化内容", 340, "#737d8c", "#8391a3"),
            new Definition("text_on_brand", "品牌色上文字", "主色按钮与品牌色背景上的文字", 350, "#ffffff", "#ffffff"),
            new Definition("border_color", "主要边框", "控件和卡片的常规描边", 360, "rgba(74, 91, 113, 0.22)", "rgba(157, 192, 231, 0.24)"),
            new Definition("border_color_light", "弱边框", "分隔线和低强调描边", 370, "rgba(74, 91, 113, 0.12)", "rgba(157, 192, 231, 0.13)"),
            new Definition("stroke_highlight", "表面高光", "半透明表面的顶部高光", 380, "rgba(255, 255, 255, 0.92)", "rgba(209, 231, 255, 0.20)"),
            new Definition("focus_ring", "焦点光环", "键盘操作时的可访问性焦点提示", 390, "rgba(15, 108, 189, 0.32)", "rgba(71, 158, 245, 0.40)")
    );

    private static final Map<String, Definition> DEFINITION_BY_KEY = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(Definition::key, Function.identity()));
    private static final Set<String> TYPES = Set.of(LIGHT_TYPE, DARK_TYPE);

    private ThemeVariableCatalog() {
    }

    public static boolean isThemeType(String variableType) {
        return TYPES.contains(variableType);
    }

    public static boolean isKnownKey(String variableKey) {
        return DEFINITION_BY_KEY.containsKey(variableKey);
    }

    public static Definition getDefinition(String variableKey) {
        return DEFINITION_BY_KEY.get(variableKey);
    }

    public static boolean isSupportedColor(String value) {
        return value != null && CSS_COLOR_PATTERN.matcher(value.trim()).matches();
    }

    public static List<String> types() {
        return List.of(LIGHT_TYPE, DARK_TYPE);
    }
}
