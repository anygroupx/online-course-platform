package com.course.platform.infra.projectcenter;

import com.course.platform.application.service.platform.docking.ProviderConnectionProbe;
import com.course.platform.application.service.projectcenter.ProjectCenterGateway;
import com.course.platform.application.service.projectcenter.ProjectTicketGateway;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.exception.ProviderRequestException;
import com.course.platform.domain.projectcenter.ProjectCenterTypes.*;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.Receipt;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.Reply;
import com.course.platform.domain.projectcenter.ProjectTicketTypes.SubmitForm;
import com.course.platform.infra.external.ApiHttpClient;
import com.course.platform.infra.http.ProviderUrlNormalizer;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** Evidence-backed syyv5 protocol. generateCustomer is a legacy mutating GET: NEVER retry it. */
@Component
@RequiredArgsConstructor
public class Syyv5ProjectGateway
        implements ProjectCenterGateway, ProjectTicketGateway, ProviderConnectionProbe {
    private final ApiHttpClient http;
    private final ProviderUrlNormalizer normalizer;
    private final ProjectTicketImagePolicy images;
    private static final ObjectMapper JSON =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(16)
                                                    .maxStringLength(8192)
                                                    .maxNumberLength(32)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private static final ObjectMapper TICKET_JSON = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                            .maxStringLength(ProjectTicketImagePolicy.MAX_INLINE_CHARS).maxNumberLength(32).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Set<String> TICKET_ACTIONS = Set.of("submitCustomerTicket", "getCustomerTicketDetail", "replyCustomerTicket", "reviewCompensationTicket");

    @Override
    public String getProviderType() {
        return "syyv5";
    }

    @Override
    public void testConnection(ApiProvider provider) {
        projects(provider);
    }

    @Override
    public List<CatalogItem> projects(ApiProvider p) {
        JsonNode rows = call(p, "getUserApiKeys", Map.of(), false).path("apiKeys");
        if (!rows.isArray() || rows.size() > 200) throw invalid();
        var result = new ArrayList<CatalogItem>();
        var seen = new HashSet<String>();
        for (JsonNode row : rows) {
            String id = id(row.path("project_id"));
            if (!seen.add(id)) throw invalid();
            BigDecimal price =
                    decimal(row.path("price")); // Missing price must NOT become the PHP default of
            // 1.
            if (price.signum() <= 0 || price.compareTo(new BigDecimal("9999")) > 0) throw invalid();
            result.add(new CatalogItem(id, text(row.path("project_name"), 100), price));
        }
        return List.copyOf(result);
    }

    @Override
    public CustomerReceipt provision(ApiProvider p, String projectId) {
        requireId(projectId);
        var customer =
                parseCustomer(
                        call(
                                        p,
                                        "generateCustomer",
                                        Map.of("project_id", projectId, "balance", "0"),
                                        false)
                                .path("customer"),
                        projectId,
                        null,
                        false);
        if (customer.balance().signum() != 0) throw invalid();
        return customer;
    }

    @Override
    public CustomerReceipt customer(ApiProvider p, String projectId, String customerId) {
        requireId(projectId);
        requireId(customerId);
        var root = call(p, "getCustomerInfo", Map.of("customer_ids", customerId), false);
        JsonNode row = root.path("customer");
        if (root.has("customers")) {
            var rows = root.get("customers");
            if (!rows.isArray() || rows.size() != 1 || root.has("customer")) throw invalid();
            row = rows.get(0);
        }
        return parseCustomer(row, projectId, customerId, true);
    }

    @Override
    public AdjustmentReceipt adjust(
            ApiProvider p,
            String projectId,
            String customerId,
            BigDecimal units,
            String operationId) {
        requireId(projectId);
        requireId(customerId);
        if (units == null
                || units.signum() == 0
                || units.abs().compareTo(new BigDecimal("100000")) > 0
                || units.stripTrailingZeros().scale() > 6
                || operationId == null
                || !operationId.matches("[0-9a-f-]{36}")) throw invalid();
        var root =
                call(
                        p,
                        "adjustCustomerBalance",
                        Map.of(
                                "customer_id",
                                customerId,
                                "project_id",
                                projectId,
                                "amount",
                                units.toPlainString(),
                                "remark",
                                "Platform operation " + operationId),
                        true);
        BigDecimal after = decimal(root.path("balance_after"));
        if (root.has("balance_before")) {
            var before = decimal(root.get("balance_before"));
            if (before.add(units).compareTo(after) != 0) throw invalid();
        }
        if (root.has("project_id") && !projectId.equals(id(root.get("project_id"))))
            throw invalid();
        if (root.has("customer_id") && !customerId.equals(id(root.get("customer_id"))))
            throw invalid();
        BigDecimal price = root.has("actual_price") ? decimal(root.get("actual_price")) : null;
        return new AdjustmentReceipt(after, price);
    }

    @Override
    public Receipt submitTicket(
            ApiProvider p, String projectId, String customerKey, SubmitForm form) {
        requireId(projectId);
        var fields = new LinkedHashMap<String, Object>();
        fields.put("project_id", projectId);
        fields.put("type", form.type());
        fields.put("title", form.title());
        fields.put("description", form.description());
        String image = images.outgoing(form.imageData());
        if (image != null) fields.put("image_data", image);
        fields.put(
                "compensation_amount",
                form.compensationAmount() == null
                        ? "0"
                        : form.compensationAmount().toPlainString());
        var receipt =
                parseTicket(
                        call(p, "submitCustomerTicket", fields, true, customerKey),
                        projectId,
                        null,
                        p,
                        customerKey);
        if (!receipt.type().equals(form.type())
                || !receipt.title().equals(form.title())
                || !receipt.description().equals(form.description())
                || receipt.compensationAmount()
                                .compareTo(
                                        form.compensationAmount() == null
                                                ? BigDecimal.ZERO
                                                : form.compensationAmount())
                        != 0 || (image != null && !image.equals(receipt.imageData()))) throw invalid();
        return receipt;
    }

    @Override
    public Receipt ticket(ApiProvider p, String projectId, String customerKey, String ticketId) {
        requireId(projectId);
        requireId(ticketId);
        return parseTicket(
                call(
                        p,
                        "getCustomerTicketDetail",
                        Map.of("project_id", projectId, "ticket_id", ticketId),
                        false,
                        customerKey),
                projectId,
                ticketId,
                p,
                customerKey);
    }

    @Override
    public Receipt replyTicket(ApiProvider p, String projectId, String customerKey, String ticketId, String content) {
        return replyTicketWithImage(p, projectId, customerKey, ticketId, content, null);
    }

    @Override
    public Receipt replyTicketWithImage(ApiProvider p, String projectId, String customerKey,
            String ticketId, String content, String imageData) {
        requireId(projectId);
        requireId(ticketId);
        String image = images.outgoing(imageData);
        if (content == null || content.length() > 4000 || (content.isBlank() && image == null)) throw invalid();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("project_id", projectId); fields.put("ticket_id", ticketId); fields.put("content", content);
        if (image != null) fields.put("image_data", image);
        var receipt = parseTicket(call(p, "replyCustomerTicket", fields, true, customerKey), projectId, ticketId, p, customerKey);
        if (receipt.replies().stream().noneMatch(r -> "customer".equals(r.sender())
                && content.equals(r.content()) && Objects.equals(image, r.imageData()))) throw invalid();
        return receipt;
    }

    @Override
    public Receipt reviewTicket(
            ApiProvider p, String projectId, String ticketId, String result, String note) {
        requireId(projectId);
        requireId(ticketId);
        if (!Set.of("approved", "rejected").contains(result)) throw invalid();
        var receipt =
                parseTicket(
                        call(
                                p,
                                "reviewCompensationTicket",
                                Map.of(
                                        "ticket_id",
                                        ticketId,
                                        "review_result",
                                        result,
                                        "review_note",
                                        note),
                                true),
                        projectId,
                        ticketId,
                        p,
                        null);
        if (!"compensation".equals(receipt.type())
                || !result.equals(receipt.reviewResult())
                || !note.equals(receipt.reviewNote())
                || !("approved".equals(result) ? "resolved" : "closed").equals(receipt.status()))
            throw invalid();
        return receipt;
    }

    private Receipt parseTicket(
            JsonNode root,
            String projectId,
            String expectedId,
            ApiProvider provider,
            String customerKey) {
        var data = root.path("data");
        var row = data.path("ticket");
        if (!row.isObject()) throw invalid();
        String ticketId = id(row.path("id"));
        if ((expectedId != null && !expectedId.equals(ticketId))
                || !projectId.equals(id(row.path("project_id")))
                || (row.has("ticket_id") && !ticketId.equals(id(row.get("ticket_id")))))
            throw invalid();
        String type = text(row.path("type"), 20), status = text(row.path("status"), 20);
        if (!Set.of("suggestion", "bug", "compensation").contains(type)
                || !Set.of("pending", "processing", "resolved", "closed").contains(status))
            throw invalid();
        String review = ticketText(row.path("review_result"), 20, provider, customerKey);
        if (!Set.of("", "approved", "rejected").contains(review)) throw invalid();
        var replies = row.path("replies");
        if (data.has("replies")) {
            if (!replies.isMissingNode() && !replies.equals(data.get("replies"))) throw invalid();
            replies = data.get("replies");
        }
        if (!replies.isArray() || replies.size() > 100) throw invalid();
        var imageBudget = images.receipt();
        String image = imageData(row, imageBudget);
        List<Reply> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (var r : replies) {
            String replyId = id(r.path("id"));
            if (!seen.add(replyId) || !ticketId.equals(id(r.path("ticket_id")))) throw invalid();
            String sender = text(r.path("sender_type"), 20);
            if (!Set.of("customer", "admin", "owner").contains(sender)) throw invalid();
            result.add(
                    new Reply(
                            replyId,
                            sender,
                            ticketText(r.path("content"), 4000, provider, customerKey),
                            ticketText(r.path("created_at"), 40, provider, customerKey),
                            attachment(r), imageData(r, imageBudget)));
        }
        return new Receipt(
                ticketId,
                projectId,
                type,
                ticketText(row.path("title"), 120, provider, customerKey),
                ticketText(row.path("description"), 4000, provider, customerKey),
                decimal(row.path("compensation_amount")),
                status,
                review,
                ticketText(row.path("review_note"), 1000, provider, customerKey),
                ticketText(row.path("created_at"), 40, provider, customerKey),
                ticketText(row.path("updated_at"), 40, provider, customerKey),
                attachment(row),
                List.copyOf(result), image);
    }

    private String imageData(JsonNode row, ProjectTicketImagePolicy.Budget budget) {
        if (!attachment(row)) return null;
        return budget.image(row.get("image_data").textValue());
    }

    private static boolean attachment(JsonNode row) {
        if (!row.has("image_data")) return false;
        if (!row.get("image_data").isTextual()) throw invalid();
        return !row.get("image_data").textValue().isEmpty();
    }

    private static String ticketText(
            JsonNode node, int max, ApiProvider provider, String customerKey) {
        if (!node.isTextual()
                || node.textValue().length() > max
                || node.textValue()
                        .codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                && c != '\n'
                                                && c != '\r'
                                                && c != '\t')) throw invalid();
        String value = node.textValue();
        for (String key : new String[] {provider.getApiKey(), customerKey})
            if (key != null && !key.isBlank()) value = value.replace(key, "[REDACTED]");
        return value;
    }

    private CustomerReceipt parseCustomer(
            JsonNode row, String projectId, String expectedId, boolean requireStatus) {
        if (!row.isObject()) throw invalid();
        String id = id(row.path("id"));
        if (!projectId.equals(id(row.path("project_id")))
                || (expectedId != null && !expectedId.equals(id))) throw invalid();
        boolean enabled = true;
        if (requireStatus || row.has("status")) {
            String status = row.path("status").asText();
            if (!Set.of("0", "1").contains(status)) throw invalid();
            enabled = "1".equals(status);
        }
        return new CustomerReceipt(
                id,
                projectId,
                text(row.path("api_key"), 2048),
                decimal(row.path("balance")),
                enabled);
    }

    private JsonNode call(
            ApiProvider p, String action, Map<String, Object> supplied, boolean post) {
        return call(p, action, supplied, post, p == null ? null : p.getApiKey());
    }

    private JsonNode call(
            ApiProvider p, String action, Map<String, Object> supplied, boolean post, String key) {
        if (key == null || key.isBlank() || key.length() > 2048) throw invalid();
        if (p == null
                || !"syyv5".equals(p.getProviderType())
                || p.getApiKey() == null
                || p.getApiKey().isBlank()) throw invalid();
        var uri = normalizer.normalize(p.getApiUrl());
        if (!"https".equals(uri.getScheme()))
            throw new ProviderRequestException(ProviderRequestException.Reason.BLOCKED_DESTINATION);
        var args = new LinkedHashMap<String, Object>(supplied);
        args.put("action", action);
        args.put("api_key", key);
        String response =
                post
                        ? http.postForString(p, uri.toASCIIString(), args)
                        : http.getForString(p, uri.toASCIIString(), args);
        boolean ticket = TICKET_ACTIONS.contains(action);
        // Existing transport cap remains authoritative (default 8MiB). All non-ticket paths
        // retain their stricter 256KiB parser limit; no global outbound policy is weakened.
        if (response == null || response.length() > (ticket ? 8 * 1024 * 1024 : 262144)) throw invalid();
        try {
            JsonNode root = (ticket ? TICKET_JSON : JSON).readTree(response);
            if (root == null || !root.isObject() || !root.path("status").isTextual())
                throw invalid();
            if (!"success".equals(root.get("status").textValue()))
                throw new ProviderRequestException(
                        ProviderRequestException.Reason.UPSTREAM_REJECTED);
            return root;
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid();
        }
    }

    private static String id(JsonNode node) {
        String value = text(node, 19);
        requireId(value);
        return value;
    }

    private static void requireId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try {
            Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid();
        }
    }

    private static String text(JsonNode n, int max) {
        if (!(n.isTextual() || n.isIntegralNumber())
                || n.asText().isBlank()
                || n.asText().length() > max
                || n.asText().codePoints().anyMatch(Character::isISOControl)) throw invalid();
        return n.asText();
    }

    private static BigDecimal decimal(JsonNode n) {
        if (n.isNumber()) {
            // Jackson may use a DoubleNode for an exponent overflowing to Infinity even
            // with USE_BIG_DECIMAL_FOR_FLOATS. Never coerce that lossy fallback to money.
            if (n.isFloatingPointNumber() && !n.isBigDecimal()) throw invalid();
            BigDecimal value = n.decimalValue();
            // Bound magnitude BEFORE stripping/rendering: a short JSON exponent can expand
            // to gigabytes, or overflow scale normalization, despite the token length limit.
            if (value.signum() < 0 || (long) value.precision() - value.scale() > 12)
                throw invalid();
            value = value.stripTrailingZeros();
            if (value.scale() > 6) throw invalid();
            return value;
        }
        String value = n.asText();
        if (!value.matches("[0-9]{1,12}(?:\\.[0-9]{1,6})?")) throw invalid();
        return new BigDecimal(value);
    }

    private static ProviderRequestException invalid() {
        return new ProviderRequestException(ProviderRequestException.Reason.INVALID_RESPONSE);
    }
}
