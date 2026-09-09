package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.course.platform.application.service.catalogrefresh.CatalogRefreshService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.common.security.SecretCrypto;
import com.course.platform.domain.catalogrefresh.*;
import com.course.platform.domain.catalogrefresh.CatalogRefreshTypes.*;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.servicecommerce.ServiceTime;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/**
 * P02 dockpricee parity: existing price/content only, with exact preview and atomic local apply.
 */
@Service
@RequiredArgsConstructor
public class CatalogRefreshServiceImpl implements CatalogRefreshService {
    private final CoursePriceRefreshMapper batches;
    private final CoursePlatformMapper platforms;
    private final ApiProviderMapper providers;
    private final PlatformDockingService docking;
    private final PlatformTransactionManager transactions;
    private final Validator validator;

    @Value("${app.native-services.enabled:false}")
    private boolean enabled;

    @Value("${app.crypto.secret}")
    private String cryptoSecret;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    @Override
    public View preview(PreviewForm form) {
        Long uid = user();
        if (!enabled) throw bad("原生插件功能尚未启用");
        if (form == null || !validator.validate(form).isEmpty()) throw bad("请核对接口、范围、分类及价格倍率");
        var selected =
                new LinkedHashSet<>(
                        form.productIds() == null ? List.<String>of() : form.productIds());
        if ("SELECTED".equals(form.scope()) && selected.isEmpty()) throw bad("请选择需要更新的商品");
        if ("ALL_EXISTING".equals(form.scope()) && !selected.isEmpty())
            throw bad("全部已导入模式不接收单独商品编号");
        var skip =
                new LinkedHashSet<>(
                        form.skipCategoryIds() == null
                                ? List.<String>of()
                                : form.skipCategoryIds());
        String category =
                form.categoryId() == null || form.categoryId().isBlank()
                        ? null
                        : form.categoryId().trim();
        var provider = providers.selectById(form.providerId());
        requireProvider(provider);
        // Deliberately outside SQL transactions/locks; uses existing credential and outbound
        // controls.
        var remote = docking.fetchProviderProducts(provider.getId(), category);
        if (remote == null || remote.size() > 10000) throw bad("上游目录过大或无效，请缩小查询分类");
        var indexed = new LinkedHashMap<String, PlatformItem>();
        for (var item : remote) {
            if (item == null
                    || item.getId() == null
                    || item.getId().isBlank()
                    || item.getId().length() > 50
                    || indexed.putIfAbsent(item.getId(), item) != null)
                throw bad("上游商品编号缺失或重复，未生成可执行预览");
        }
        var eligibleIds =
                remote.stream()
                        .filter(
                                item ->
                                        !"SELECTED".equals(form.scope())
                                                || selected.contains(item.getId()))
                        .filter(item -> category == null || category.equals(item.getCategoryId()))
                        .filter(item -> !skip.contains(item.getCategoryId()))
                        .map(PlatformItem::getId)
                        .toList();
        List<CoursePlatform> locals =
                eligibleIds.isEmpty()
                        ? List.of()
                        : platforms.selectList(
                                new LambdaQueryWrapper<CoursePlatform>()
                                        .eq(CoursePlatform::getDockApiId, provider.getId())
                                        .in(CoursePlatform::getDockParam, eligibleIds)
                                        .orderByAsc(CoursePlatform::getId)
                                        .last("LIMIT 501"));
        if (locals.size() > 500) throw bad("单次最多更新500个本地课程，请缩小分类或勾选范围");
        var byRemote = new HashMap<String, List<CoursePlatform>>();
        for (var local : locals)
            byRemote.computeIfAbsent(local.getDockParam(), k -> new ArrayList<>()).add(local);
        var rows = new ArrayList<Row>();
        int missing = 0, unimported = 0, excluded = 0;
        for (var item : remote) {
            if ("SELECTED".equals(form.scope()) && !selected.contains(item.getId())) continue;
            if ((category != null && !category.equals(item.getCategoryId()))
                    || skip.contains(item.getCategoryId())) {
                excluded++;
                continue;
            }
            var matches = byRemote.getOrDefault(item.getId(), List.of());
            if (matches.isEmpty()) {
                unimported++;
                continue;
            }
            BigDecimal price = roundedPrice(item.getPrice(), form.multiplier());
            if (item.getContent() != null && item.getContent().length() > 500)
                throw bad("上游说明超过本平台500字限制，未生成预览");
            for (var local : matches) {
                if (local.getBasePrice() == null) throw bad("本地课程价格缺失，请先修复课程配置");
                String description =
                        item.getContent() == null ? local.getDescription() : item.getContent();
                boolean changed =
                        price.compareTo(local.getBasePrice()) != 0
                                || !Objects.equals(description, local.getDescription());
                rows.add(
                        new Row(
                                local.getId(),
                                item.getId(),
                                local.getName(),
                                local.getBasePrice().toPlainString(),
                                price.toPlainString(),
                                local.getDescription(),
                                description,
                                item.getContent() != null,
                                changed));
                if (rows.size() > 500) throw bad("单次最多更新500个本地课程，请缩小分类或勾选范围");
            }
        }
        if ("SELECTED".equals(form.scope()))
            for (String id : selected) if (!indexed.containsKey(id)) missing++;
        rows.sort(Comparator.comparing(Row::localId));
        Plan plan =
                new Plan(
                        form.multiplier().toPlainString(),
                        form.scope(),
                        category,
                        List.copyOf(skip),
                        List.copyOf(rows),
                        missing,
                        unimported,
                        excluded);
        String ciphertext = encrypt(plan);
        return tx(
                () -> {
                    var current = batches.lockProvider(provider.getId());
                    requireProvider(current);
                    if (!Objects.equals(provider.getConfigVersion(), current.getConfigVersion()))
                        throw bad("供应商配置已变化，请重新预览");
                    var batch = new CoursePriceRefresh();
                    batch.setId(UUID.randomUUID().toString());
                    batch.setUserId(uid);
                    batch.setProviderId(provider.getId());
                    batch.setProviderVersion(provider.getConfigVersion());
                    batch.setState("READY");
                    batch.setPlanEncrypted(ciphertext);
                    batch.setExpiresAt(now().plusMinutes(5));
                    batch.setCreateTime(now());
                    batch.setUpdateTime(now());
                    write(batches.insert(batch));
                    return view(batch, plan);
                });
    }

    @Override
    public View get(String id) {
        var batch = owned(id);
        return view(batch, decode(batch));
    }

    @Override
    public View confirm(String id, ConfirmForm form) {
        var seed = owned(id);
        if (!enabled) throw bad("原生插件功能尚未启用；仍可查询已有结果");
        if (form == null || !form.consent()) throw bad("请确认仅更新预览中的现有价格和说明");
        return tx(
                () -> {
                    var provider = batches.lockProvider(seed.getProviderId());
                    var batch = batches.lock(id);
                    if (batch == null || !Objects.equals(batch.getUserId(), seed.getUserId()))
                        throw missing();
                    Plan plan = decode(batch);
                    if (!"READY".equals(batch.getState())) return view(batch, plan);
                    if (!batch.getExpiresAt().isAfter(now()))
                        return terminal(batch, plan, "EXPIRED");
                    if (!validProvider(provider)
                            || !Objects.equals(
                                    provider.getConfigVersion(), batch.getProviderVersion()))
                        return terminal(batch, plan, "STALE");
                    // Lock and validate every row before writing any row. Unrelated fields may
                    // change safely.
                    for (var row : plan.rows()) {
                        var local = batches.lockPlatform(row.localId());
                        if (local == null
                                || !Objects.equals(local.getDockApiId(), batch.getProviderId())
                                || !Objects.equals(local.getDockParam(), row.remoteId())
                                || local.getBasePrice() == null
                                || local.getBasePrice().compareTo(new BigDecimal(row.oldPrice()))
                                        != 0
                                || !Objects.equals(local.getDescription(), row.oldDescription()))
                            return terminal(batch, plan, "STALE");
                    }
                    if (!batch.getExpiresAt().isAfter(now()))
                        return terminal(batch, plan, "EXPIRED");
                    for (var row : plan.rows())
                        if (row.changed()) {
                            write(
                                    platforms.update(
                                            null,
                                            new LambdaUpdateWrapper<CoursePlatform>()
                                                    .eq(CoursePlatform::getId, row.localId())
                                                    .set(
                                                            CoursePlatform::getBasePrice,
                                                            new BigDecimal(row.newPrice()))
                                                    .set(
                                                            CoursePlatform::getDescription,
                                                            row.newDescription())
                                                    .set(CoursePlatform::getUpdateTime, now())));
                        }
                    batch.setAppliedAt(now());
                    return terminal(batch, plan, "APPLIED");
                });
    }

    private View terminal(CoursePriceRefresh batch, Plan plan, String state) {
        batch.setState(state);
        batch.setUpdateTime(now());
        write(batches.updateById(batch));
        return view(batch, plan);
    }

    private View view(CoursePriceRefresh batch, Plan plan) {
        String state =
                "READY".equals(batch.getState()) && !batch.getExpiresAt().isAfter(now())
                        ? "EXPIRED"
                        : batch.getState();
        String notice =
                switch (state) {
                    case "APPLIED" -> "价格与已提供的说明已原子更新；未创建课程、未调整分类/名称/上架状态，未影响原订单或账本。";
                    case "STALE" -> "供应商或本地价格/说明/绑定已变化，本批次未更新任何课程，请重新预览。";
                    case "EXPIRED" -> "预览已过期，本批次未更新任何课程，请重新预览。";
                    default -> "只读上游目录快照，五分钟有效；四舍五入保留两位。仅本地明确确认后更新价格/说明，不改名称、分类、上下架或接口绑定。";
                };
        return new View(
                batch.getId(),
                batch.getProviderId(),
                state,
                batch.getExpiresAt(),
                batch.getAppliedAt(),
                plan,
                notice);
    }

    private CoursePriceRefresh owned(String id) {
        Long uid = user();
        if (id == null
                || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw missing();
        var batch = batches.selectById(id);
        if (batch == null || !uid.equals(batch.getUserId())) throw missing();
        return batch;
    }

    private Long user() {
        SecurityUtils.requireAuthority("platform:update");
        return SecurityUtils.getCurrentUserId();
    }

    private static boolean validProvider(ApiProvider p) {
        return p != null
                && Integer.valueOf(1).equals(p.getStatus())
                && "27".equals(p.getProviderType())
                && p.getConfigVersion() != null
                && p.getConfigVersion() >= 1;
    }

    private static void requireProvider(ApiProvider p) {
        if (!validProvider(p)) throw bad("请选择已启用且已验证配置的 Benz 课程接口");
    }

    private static BigDecimal roundedPrice(BigDecimal raw, BigDecimal multiplier) {
        if (raw == null
                || raw.signum() < 0
                || raw.precision() > 24
                || Math.abs((long) raw.scale()) > 12) throw bad("上游价格缺失或格式无效");
        var exact = raw.multiply(multiplier);
        var rounded = exact.setScale(2, RoundingMode.HALF_UP);
        if (rounded.compareTo(MAX_PRICE) > 0 || (exact.signum() > 0 && rounded.signum() == 0))
            throw bad("换算后的价格超出范围或不足一分");
        return rounded;
    }

    private String encrypt(Plan plan) {
        try {
            return SecretCrypto.encrypt(JSON.writeValueAsString(plan), cryptoSecret);
        } catch (Exception e) {
            throw bad("价格预览无法安全保存");
        }
    }

    private Plan decode(CoursePriceRefresh batch) {
        try {
            if (!SecretCrypto.isEncrypted(batch.getPlanEncrypted())) throw bad("无效快照");
            return JSON.readValue(
                    SecretCrypto.decrypt(batch.getPlanEncrypted(), cryptoSecret), Plan.class);
        } catch (Exception e) {
            throw bad("价格预览无法读取，请联系管理员核对原批次");
        }
    }

    private static void write(int n) {
        if (n != 1) throw bad("更新未能完整保存，本批次已回滚，请查询原结果");
    }

    private static LocalDateTime now() {
        return ServiceTime.now();
    }

    private <T> T tx(Supplier<T> body) {
        return new TransactionTemplate(transactions).execute(s -> body.get());
    }

    private static BusinessException bad(String m) {
        return new BusinessException(m);
    }

    private static BusinessException missing() {
        return new BusinessException(ResultCode.NOT_FOUND);
    }
}
