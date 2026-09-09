package com.course.platform.infra.integration;

import com.course.platform.application.service.integration.PluginReadOnlyConnector;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.PluginPageQuery;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.vo.plugin.*;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independently implemented from observable P01/P03/P04 request/response contracts.
 * No legacy PHP/JS is loaded, decrypted, copied, executed or treated as an extension.
 */
public final class PhpTemplateReadOnlyConnector implements PluginReadOnlyConnector {
    public enum Protocol {
        FLASH("flash", "/flash/api.php", 0),
        HEISHA("heisha", "/heisha/heisha.api.php", 1),
        JIGUANG("jiguang", "/jiguang/jiguang.api.php", 1);

        private final String type;
        private final String path;
        private final int successCode;
        Protocol(String type, String path, int successCode) {
            this.type = type;
            this.path = path;
            this.successCode = successCode;
        }
    }

    private static final List<PluginProjectOption> FLASH_PROJECTS = List.of(
            new PluginProjectOption("sdxy", "闪动校园"),
            new PluginProjectOption("ydsjxy", "运动世界校园"),
            new PluginProjectOption("xbd", "校步点"));
    private static final int MAX_JSON_CHARS = 262_144;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(20).maxNumberLength(32).maxStringLength(4096).build())
            .build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final Protocol protocol;
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;

    public PhpTemplateReadOnlyConnector(Protocol protocol, ApiHttpClient http, ProviderUrlNormalizer normalizer) {
        this.protocol = protocol;
        this.http = http;
        this.normalizer = normalizer;
    }

    @Override public String getProviderType() { return protocol.type; }
    @Override public boolean supportsSchools() { return protocol == Protocol.JIGUANG; }
    @Override public List<PluginProjectOption> projects() {
        return protocol == Protocol.FLASH ? FLASH_PROJECTS : List.of();
    }

    @Override public void testConnection(ApiProvider provider) {
        // Exactly one read, including Flash's default project, not all projects or schools.
        fetchCatalog(provider, null);
    }

    @Override
    public List<PluginProduct> fetchCatalog(ApiProvider provider, String project) {
        if (protocol == Protocol.FLASH) {
            String projectId = project == null || project.isEmpty() ? "sdxy" : project;
            PluginProjectOption option = FLASH_PROJECTS.stream().filter(p -> p.id().equals(projectId))
                    .findFirst().orElseThrow(() -> new BusinessException(ResultCode.PARAM_ERROR, "请选择支持的闪电项目"));
            JsonNode price = read(provider, "get_price&appId=" + option.id(), Map.of());
            return List.of(new PluginProduct(option.id(), option.name(), price(price),
                    "sdxy".equals(option.id()) ? "元/次" : "元/公里（倍率另计）"));
        }
        if (project != null && !project.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "此接口不接受项目参数");
        }
        JsonNode data = read(provider, "products", Map.of());
        List<PluginProduct> products = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (protocol == Protocol.HEISHA) {
            if (!data.isObject() || data.size() > 100) throw invalid();
            data.fields().forEachRemaining(entry -> {
                String id = id(entry.getKey());
                JsonNode item = entry.getValue();
                if (!item.isObject() || !ids.add(id)) throw invalid();
                products.add(new PluginProduct(id, text(item.get("name")), price(item.get("price")), "元/公里"));
            });
        } else {
            if (!data.isArray() || data.size() > 100) throw invalid();
            for (JsonNode item : data) {
                if (!item.isObject()) throw invalid();
                String id = scalarId(item.get("product_id"));
                if (!ids.add(id)) throw invalid();
                products.add(new PluginProduct(id, text(item.get("name")), price(item.get("price")), "元/公里"));
            }
        }
        return List.copyOf(products);
    }

    @Override
    public PluginSchoolPage searchSchools(ApiProvider provider, PluginPageQuery query) {
        if (!supportsSchools()) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
        }
        if (query == null) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少学校查询参数");
        JsonNode data = read(provider, "schools", Map.of(
                "page", query.page(), "pageSize", query.pageSize(), "keyword", query.keyword()));
        JsonNode rows = data.get("list");
        if (!data.isObject() || rows == null || !rows.isArray() || rows.size() > query.pageSize()) throw invalid();
        List<PluginSchool> schools = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            if (!row.isObject()) throw invalid();
            String id = scalarId(row.get("id"));
            if (!ids.add(id)) throw invalid();
            schools.add(new PluginSchool(id, text(row.get("name"))));
        }
        return new PluginSchoolPage(schools, query.page(), query.pageSize(),
                schools.size() == query.pageSize() && query.page() < 10_000);
    }

    private JsonNode read(ApiProvider provider, String fixedAction, Map<String, Object> extraFields) {
        if (provider == null || !getProviderType().equals(provider.getProviderType())) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UNSUPPORTED_OPERATION);
        }
        if (!validCredential(provider.getUsername(), 100) || !validCredential(provider.getApiKey(), 2048)) {
            throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
        }
        String base = normalizer.normalize(provider.getApiUrl(), getProviderType()).toASCIIString();
        Map<String, Object> fields = new LinkedHashMap<>(extraFields);
        // Only the provider credentials are accepted; never forward arbitrary request parameters.
        fields.put("login_uid", provider.getUsername());
        fields.put("login_key", provider.getApiKey());
        String body = http.postForString(provider, base + protocol.path + "?act=" + fixedAction, fields);
        if (body == null) throw invalid();
        if (body.length() > MAX_JSON_CHARS) {
            throw new ProviderRequestException(ProviderRequestException.Reason.RESPONSE_TOO_LARGE);
        }
        try {
            JsonNode root = JSON.readTree(body);
            if (root == null || !root.isObject()) throw invalid();
            JsonNode code = root.get("code");
            if (code == null || !(code.isIntegralNumber() || code.isTextual())
                    || !code.asText().matches("-?[0-9]{1,4}")) throw invalid();
            if (Integer.parseInt(code.asText()) != protocol.successCode) {
                throw new ProviderRequestException(ProviderRequestException.Reason.UPSTREAM_REJECTED);
            }
            JsonNode data = root.get("data");
            if (data == null || data.isNull()) throw invalid();
            return data;
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            // Never retain the original exception or body: either can contain remote secrets.
            throw invalid();
        }
    }

    private static boolean validCredential(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static BigDecimal price(JsonNode value) {
        if (value == null || !(value.isNumber() || value.isTextual())) throw invalid();
        String decimal;
        if (value.isNumber()) {
            // Some JSON parsers represent an overflowing exponent as a non-finite DoubleNode.
            if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) throw invalid();
            BigDecimal amount = value.decimalValue();
            // Jackson may normalize an ordinary JSON 10.00 to 1E+1. Bound precision/scale
            // before toPlainString so normal values work but hostile exponents cannot allocate.
            if (amount.precision() > 15 || amount.scale() < -8 || amount.scale() > 6) throw invalid();
            decimal = amount.toPlainString();
        } else {
            decimal = value.textValue();
        }
        if (!decimal.matches("[0-9]{1,9}(?:\\.[0-9]{1,6})?")) throw invalid();
        return new BigDecimal(decimal);
    }

    private static String text(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 160
                || value.asText().codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return value.asText().trim();
    }

    private static String scalarId(JsonNode value) {
        if (value == null || !(value.isIntegralNumber() || value.isTextual())) throw invalid();
        return id(value.asText());
    }

    private static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) throw invalid();
        return value;
    }

    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
