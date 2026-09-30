package com.course.platform.service.impl;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.OrderForm;
import com.course.platform.domain.servicecommerce.ServiceCommerceTypes.PreparedOrder;
import com.course.platform.domain.servicecommerce.ServiceProduct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure local validation for providerless Black Shark checkout. */
@Component
public class SelfOperatedCheckoutPreparer {
    private static final Set<String> INPUT_FIELDS = Set.of("phone", "password", "run_time", "school_name", "note");
    private static final Pattern SENSITIVE_NOTE = Pattern.compile(
            "(?i)data\\s*:\\s*image/|(?:face[_-]?token|run[_-]?preflight[_-]?token|access[_-]?token|refresh[_-]?token|api[_-]?key)|\\b(?:bearer|token)\\b|(?<!\\d)1[3-9]\\d{9}(?!\\d)|[A-Za-z0-9+/]{80,}={0,2}");
    public PreparedOrder prepare(ServiceProduct product, OrderForm form) {
        if (product == null || form == null || !"heisha".equals(product.getProviderType())
                || !"SELF_OPERATED".equals(product.getFulfillmentMode()) || product.getProviderId() != null
                || !"default".equals(product.getProject())
                || !Set.of("1", "2", "3", "4").contains(product.getRemoteProductId()))
            throw bad("商品配置不支持本地处理");
        if (!form.authorizedAccount()) throw bad("请单独确认账号使用授权");
        if (form.accountSessionId() != null || form.schedule() != null
                || form.taskTimes() != null && !form.taskTimes().isEmpty()) throw bad("自营订单参数不正确");
        if (form.quantity() < 1 || form.quantity() > 365 || form.distance() == null
                || form.distance().compareTo(new BigDecimal("0.1")) < 0
                || form.distance().compareTo(new BigDecimal("50")) > 0
                || form.distance().stripTrailingZeros().scale() > 2) throw bad("请选择有效次数和每次公里数");
        Map<String, String> input = form.fields();
        if (input == null || !INPUT_FIELDS.containsAll(input.keySet())) throw bad("自营订单包含不支持的资料字段");
        String phone = field(input, "phone", 100, false);
        String password = field(input, "password", 200, false);
        String runTime = field(input, "run_time", 40, false);
        if (!phone.matches("[A-Za-z0-9@._+\\-]{3,100}")) throw bad("账号或手机号格式不正确");
        if (password.length() < 4) throw bad("密码格式不正确");
        if (!runTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) throw bad("执行时间格式不正确");
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("phone", phone);
        material.put("password", password);
        material.put("run_time", runTime);
        copyOptional(input, material, "school_name", 120);
        copyOptional(input, material, "note", 1000);
        requireSafeNote((String) material.get("note"), material);
        material.put("times", form.quantity());
        material.put("km_per_day", form.distance().stripTrailingZeros().toPlainString());
        if (form.materialDraftId() != null) material.put("material_draft_id", form.materialDraftId());
        String label = phone.length() < 5 ? "已提交账号"
                : phone.substring(0, 2) + "***" + phone.substring(phone.length() - 2);
        return new PreparedOrder(material, form.quantity(), form.distance(), form.distance(), label);
    }

    public static boolean requiresFaceMaterial(ServiceProduct product) {
        return product != null && "heisha".equals(product.getProviderType())
                && "default".equals(product.getProject())
                && ("3".equals(product.getRemoteProductId()) || "4".equals(product.getRemoteProductId()));
    }

    private static String field(Map<String, String> input, String key, int max, boolean blank) {
        String value = input == null ? null : input.get(key);
        if (value == null || value.length() > max || value.codePoints().anyMatch(Character::isISOControl)
                || !blank && value.isBlank()) throw bad("订单资料格式不正确");
        return "password".equals(key) ? value : value.trim();
    }

    private static void copyOptional(Map<String, String> input, Map<String, Object> output, String key, int max) {
        if (input == null || input.get(key) == null || input.get(key).isBlank()) return;
        output.put(key, field(input, key, max, true));
    }

    static void requireSafeNote(String note, Map<String, ?> fields) {
        if (note == null) return;
        if (SENSITIVE_NOTE.matcher(note).find()) throw bad("处理备注不能包含账号、密码、图片或授权凭据");
        for (String key : Set.of("phone", "password", "face_token", "run_preflight_token")) {
            Object value = fields.get(key);
            if (value instanceof String text && !text.isBlank() && note.contains(text))
                throw bad("处理备注不能包含账号、密码、图片或授权凭据");
        }
    }

    private static BusinessException bad(String message) { return new BusinessException(message); }
}
