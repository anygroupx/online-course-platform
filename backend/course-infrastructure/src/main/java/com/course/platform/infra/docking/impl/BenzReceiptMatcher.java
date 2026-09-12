package com.course.platform.infra.docking.impl;

import cn.hutool.json.JSONObject;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.exception.ProviderRequestException;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import static com.course.platform.domain.orderreceipt.OrderReceiptTypes.MAX_CANDIDATES;

/** Full receipt identity checks; request filters are not evidence of returned ownership. */
public final class BenzReceiptMatcher {
    private BenzReceiptMatcher() {}
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12)
                    .maxStringLength(8192).maxNumberLength(64).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public static JSONObject select(String response, CourseOrder order, CoursePlatform platform,
                                    String receiptId, boolean requireFullIdentity) {
        if (receiptId == null || !receiptId.matches("[A-Za-z0-9_-]{1,50}")) throw invalid();
        try {
            JsonNode data = rows(response);
            JsonNode matched = null;
            for (JsonNode row : data) {
                if (!row.isObject()) throw invalid();
                String id = receipt(row);
                if (receiptId.equals(id)) {
                    if (matched != null) throw invalid();
                    matched = row;
                }
            }
            if (matched == null) throw invalid();
            check(matched, "user", order.getStudentAccount(), requireFullIdentity, false);
            check(matched, "pass", order.getStudentPassword(), requireFullIdentity, false);
            check(matched, "kcname", order.getCourseName(), requireFullIdentity, false);
            check(matched, "cid", platform.getDockParam(), requireFullIdentity, true);
            check(matched, "school", order.getSchoolName(), false, false);
            check(matched, "kcid", order.getCourseId(), false, true);
            return new JSONObject(matched.toString());
        } catch (ProviderRequestException e) {
            throw e;
        } catch (Exception e) {
            // Never attach raw response/credentials/parser failures to the public exception.
            throw invalid();
        }
    }

    /** Inspect one response only. Never infer pagination, choose the first row or return private fields. */
    public static List<String> candidates(String response, CourseOrder order, CoursePlatform platform) {
        try {
            Set<String> seen = new HashSet<>();
            List<String> matches = new ArrayList<>();
            for (JsonNode row : rows(response)) {
                if (!row.isObject()) throw invalid();
                String id = receipt(row);
                // Duplicate identifiers remain ambiguous even if one of their rows matches the order.
                if (!seen.add(id)) throw invalid();
                boolean matchesIdentity = matches(row, "user", order.getStudentAccount(), true, false);
                matchesIdentity &= matches(row, "pass", order.getStudentPassword(), true, false);
                matchesIdentity &= matches(row, "kcname", order.getCourseName(), true, false);
                matchesIdentity &= matches(row, "cid", platform.getDockParam(), true, true);
                matchesIdentity &= matches(row, "school", order.getSchoolName(), false, false);
                matchesIdentity &= matches(row, "kcid", order.getCourseId(), false, true);
                if (matchesIdentity) {
                    matches.add(id);
                    if (matches.size() > MAX_CANDIDATES) throw invalid();
                }
            }
            return List.copyOf(matches);
        } catch (ProviderRequestException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static JsonNode rows(String response) throws java.io.IOException {
        if (response == null || response.length() > 1024 * 1024) throw invalid();
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject()) throw invalid();
        if (!root.path("code").isIntegralNumber() || !root.path("code").canConvertToInt() || root.path("code").intValue() != 1)
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() > 1000) throw invalid();
        return data;
    }

    private static boolean matches(JsonNode row, String field, String expected, boolean required, boolean number) {
        if (!row.hasNonNull(field)) return !required;
        String actual = scalar(row.get(field), number);
        return expected != null && !expected.isBlank() && actual.equals(expected);
    }

    public static String createdId(String response) {
        try {
            if (response == null || response.length() > 1024 * 1024) return null;
            JsonNode root = JSON.readTree(response);
            JsonNode data = root.path("data");
            String nested = null;
            if (data.isObject() && (data.hasNonNull("id") || data.hasNonNull("yid"))) nested = receipt(data);
            if (data.isArray()) {
                if (data.size() != 1 || !data.get(0).isObject()) return null;
                nested = receipt(data.get(0));
            }
            String direct = root.hasNonNull("id") || root.hasNonNull("yid") ? receipt(root) : null;
            if (direct != null && nested != null && !direct.equals(nested)) return null;
            return nested == null ? direct : nested;
        } catch (Exception ignored) { return null; }
    }

    private static String receipt(JsonNode row) {
        String id = row.hasNonNull("id") ? scalar(row.get("id"), true) : null;
        String alternate = row.hasNonNull("yid") ? scalar(row.get("yid"), true) : null;
        if (id != null && alternate != null && !id.equals(alternate)) throw invalid();
        String chosen = id == null ? alternate : id;
        if (chosen == null || !chosen.matches("[A-Za-z0-9_-]{1,50}")) throw invalid();
        return chosen;
    }
    private static void check(JsonNode row, String field, String expected, boolean required, boolean number) {
        if (!row.has(field) || row.get(field).isNull()) {
            if (required) throw invalid();
            return;
        }
        String actual = scalar(row.get(field), number);
        if (expected == null || expected.isBlank() || !actual.equals(expected)) throw invalid();
    }
    private static String scalar(JsonNode value, boolean number) {
        if (!value.isTextual() && !(number && value.isIntegralNumber())) throw invalid();
        String text = value.asText();
        if (text.isBlank() || text.length() > 2048 || text.codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return text;
    }
    public static Map<String, Object> query(CourseOrder order, String uid, String key) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("uid", uid); body.put("key", key); body.put("user", order.getStudentAccount());
        body.put("pass", order.getStudentPassword()); body.put("school", order.getSchoolName());
        body.put("kcname", order.getCourseName());
        return body;
    }
    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
