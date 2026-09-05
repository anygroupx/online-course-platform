package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.domain.vo.CourseInfoResponse;
import com.course.platform.domain.dto.DockResult;
import com.course.platform.domain.dto.OrderProgressResult;
import com.course.platform.domain.dto.PlatformItem;
import com.course.platform.domain.dto.QueryCourseRequest;
import com.course.platform.domain.dto.ProductImportRequest;
import com.course.platform.domain.dto.ProviderOrderLog;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.infra.persistence.mapper.CourseOrderMapper;
import com.course.platform.infra.persistence.mapper.PlatformCategoryMapper;
import com.course.platform.domain.entity.PlatformCategory;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.application.service.platform.ApiProviderService;
import com.course.platform.application.service.platform.PlatformDockingService;
import com.course.platform.application.service.platform.docking.PlatformDockingStrategy;
import com.course.platform.infra.docking.PlatformDockingStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import cn.hutool.core.util.StrUtil;

/**
 * 平台对接服务实现
 */
@Slf4j
@Service
public class PlatformDockingServiceImpl implements PlatformDockingService {

    private final PlatformDockingStrategyFactory strategyFactory;
    private final ApiProviderMapper apiProviderMapper;
    private final CoursePlatformMapper coursePlatformMapper;
    private final CourseOrderMapper courseOrderMapper;
    private final PlatformCategoryMapper platformCategoryMapper;
    private final ApiProviderService apiProviderService;

    public PlatformDockingServiceImpl(PlatformDockingStrategyFactory strategyFactory,
                                      ApiProviderMapper apiProviderMapper,
                                      CoursePlatformMapper coursePlatformMapper,
                                      CourseOrderMapper courseOrderMapper,
                                      PlatformCategoryMapper platformCategoryMapper,
                                      ApiProviderService apiProviderService) {
        this.strategyFactory = strategyFactory;
        this.apiProviderMapper = apiProviderMapper;
        this.coursePlatformMapper = coursePlatformMapper;
        this.courseOrderMapper = courseOrderMapper;
        this.platformCategoryMapper = platformCategoryMapper;
        this.apiProviderService = apiProviderService;
    }

    @Override
    public List<CourseInfoResponse.CourseItem> queryCourses(CoursePlatform platform, QueryCourseRequest request) {
        if (platform.getQueryApiId() == null) {
            throw new BusinessException("未配置查课接口");
        }
        
        ApiProvider apiProvider = apiProviderService.loadDecrypted(platform.getQueryApiId());
        if (apiProvider == null || !Integer.valueOf(1).equals(apiProvider.getStatus())) {
            throw new BusinessException("查课接口配置不存在或已禁用");
        }

        PlatformDockingStrategy strategy = strategyFactory.getStrategy(apiProvider.getProviderType());
        if (strategy == null) {
            throw new BusinessException("不支持的接口类型: " + apiProvider.getProviderType());
        }

        return strategy.queryCourses(platform, request, apiProvider);
    }

    @Override
    public DockResult dockOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        apiProvider = resolveDecryptedProvider(apiProvider);
        PlatformDockingStrategy strategy = strategyFactory.getStrategy(apiProvider.getProviderType());
        if (strategy == null) {
            return DockResult.fail("不支持的接口类型: " + apiProvider.getProviderType());
        }
        return strategy.dockOrder(order, platform, apiProvider);
    }

    @Override
    public OrderProgressResult queryOrderProgress(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        apiProvider = resolveDecryptedProvider(apiProvider);
        PlatformDockingStrategy strategy = strategyFactory.getStrategy(apiProvider.getProviderType());
        if (strategy == null) {
            throw new BusinessException("不支持的接口类型: " + apiProvider.getProviderType());
        }
        return strategy.queryOrderProgress(order, platform, apiProvider);
    }

    @Override
    public DockResult retryOrder(CourseOrder order, CoursePlatform platform, ApiProvider apiProvider) {
        apiProvider = resolveDecryptedProvider(apiProvider);
        PlatformDockingStrategy strategy = strategyFactory.getStrategy(apiProvider.getProviderType());
        if (strategy == null) {
            return DockResult.fail("不支持的接口类型: " + apiProvider.getProviderType());
        }
        return strategy.retryOrder(order, platform, apiProvider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importPlatforms(Long apiProviderId, BigDecimal priceMultiplier, String targetCategoryId) {
        return importPlatforms(apiProviderId, priceMultiplier, targetCategoryId, true, null);
    }

    /**
     * 一键导入平台/课程（增强版，参考 benzcron.php）
     *
     * @param apiProviderId    API配置ID
     * @param priceMultiplier  价格倍率
     * @param targetCategoryId 目标分类ID (可选)
     * @param syncCategories   是否同步分类（对应 benzcron.php 的 $dockcro）
     * @param skipCategoryIds  跳过的分类ID列表（对应 benzcron.php 的 $skipCategories）
     * @return 导入结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importPlatforms(Long apiProviderId, BigDecimal priceMultiplier,
                                               String targetCategoryId, Boolean syncCategories,
                                               List<String> skipCategoryIds) {
        ApiProvider apiProvider = requireEnabledProvider(apiProviderId);
        PlatformDockingStrategy strategy = requireStrategy(apiProvider);
        BigDecimal multiplier = normalizeMultiplier(priceMultiplier);

        List<PlatformItem> items = strategy.fetchPlatformList(apiProvider, targetCategoryId).stream()
                // Never trust an upstream category filter to be enforced correctly.
                .filter(item -> StrUtil.isBlank(targetCategoryId)
                        || targetCategoryId.equals(item.getCategoryId()))
                .filter(item -> skipCategoryIds == null || !skipCategoryIds.contains(item.getCategoryId()))
                .toList();
        return importPlatformItems(apiProviderId, items, multiplier, syncCategories, skipCategoryIds);
    }

    @Override
    public List<PlatformItem> fetchProviderProducts(Long apiProviderId, String categoryId) {
        ApiProvider apiProvider = requireEnabledProvider(apiProviderId);
        PlatformDockingStrategy strategy = requireStrategy(apiProvider);
        List<PlatformItem> items = strategy.fetchPlatformList(apiProvider, categoryId).stream()
                // Daytime accepts a remote filter parameter, but still enforce it locally.
                .filter(item -> StrUtil.isBlank(categoryId) || categoryId.equals(item.getCategoryId()))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<String, CoursePlatform> importedByRemoteId = coursePlatformMapper.selectList(
                        new QueryWrapper<CoursePlatform>().eq("dock_api_id", apiProviderId))
                .stream()
                .filter(platform -> StrUtil.isNotBlank(platform.getDockParam()))
                .collect(Collectors.toMap(CoursePlatform::getDockParam, Function.identity(), (left, right) -> left));

        for (PlatformItem item : items) {
            CoursePlatform imported = importedByRemoteId.get(item.getId());
            item.setImported(imported != null);
            item.setLocalPlatformId(imported == null ? null : imported.getId());
        }
        return items;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importSelectedProducts(ProductImportRequest request) {
        ApiProvider apiProvider = requireEnabledProvider(request.getApiProviderId());
        PlatformDockingStrategy strategy = requireStrategy(apiProvider);
        Set<String> selectedIds = request.getProductIds().stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (selectedIds.isEmpty()) {
            throw new BusinessException("请至少选择一个有效商品");
        }

        // 重新从第三方拉取，商品名称、价格和分类均以后端实时数据为准。
        List<PlatformItem> selectedItems = new ArrayList<>(strategy.fetchPlatformList(apiProvider).stream()
                .filter(item -> selectedIds.contains(item.getId()))
                .filter(item -> StrUtil.isNotBlank(item.getId()))
                .collect(Collectors.toMap(PlatformItem::getId, Function.identity(),
                        (first, ignored) -> first, LinkedHashMap::new))
                .values());
        if (selectedItems.isEmpty()) {
            throw new BusinessException("所选商品已不存在，请重新查询商品列表");
        }

        Map<String, Object> result = importPlatformItems(
                request.getApiProviderId(), selectedItems, normalizeMultiplier(request.getPriceMultiplier()),
                request.getSyncCategories(), null);
        result.put("requested", selectedIds.size());
        result.put("missing", Math.max(0, selectedIds.size() - selectedItems.size()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal refreshProviderBalance(Long apiProviderId) {
        ApiProvider apiProvider = requireEnabledProvider(apiProviderId);
        PlatformDockingStrategy strategy = requireStrategy(apiProvider);
        BigDecimal balance = strategy.queryBalance(apiProvider);
        if (balance == null) {
            throw new BusinessException("该接口类型暂不支持余额查询");
        }

        ApiProvider update = new ApiProvider();
        update.setId(apiProviderId);
        update.setBalance(balance);
        apiProviderMapper.updateById(update);
        return balance;
    }

    @Override
    public List<ProviderOrderLog> fetchOrderLogs(Long orderId) {
        CourseOrder order = courseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (Integer.valueOf(1).equals(order.getIsSelfOperated())) {
            throw new BusinessException("自营订单没有第三方订单日志");
        }

        Long apiProviderId = order.getApiProviderId();
        if (apiProviderId == null && order.getPlatformId() != null) {
            CoursePlatform platform = coursePlatformMapper.selectById(order.getPlatformId());
            apiProviderId = platform == null ? null : platform.getDockApiId();
        }
        if (apiProviderId == null) {
            throw new BusinessException("订单未配置第三方接口");
        }

        ApiProvider apiProvider = requireEnabledProvider(apiProviderId);
        List<ProviderOrderLog> logs = requireStrategy(apiProvider).fetchOrderLogs(order, apiProvider);
        if (logs == null) {
            throw new BusinessException("该接口类型暂不支持订单日志查询");
        }
        return logs;
    }

    private Map<String, Object> importPlatformItems(Long apiProviderId, List<PlatformItem> items,
                                                     BigDecimal priceMultiplier, Boolean syncCategories,
                                                     List<String> skipCategoryIds) {
        int successCount = 0;
        int failCount = 0;
        int updateCount = 0;
        int createCount = 0;
        int categoryCreated = 0;

        if (Boolean.TRUE.equals(syncCategories)) {
            categoryCreated = syncCategories(items, skipCategoryIds);
        }

        Map<String, Long> categoryCache = new HashMap<>();
        for (PlatformItem item : items) {
            try {
                if (StrUtil.isBlank(item.getId()) || StrUtil.isBlank(item.getName())) {
                    failCount++;
                    continue;
                }
                if (skipCategoryIds != null && skipCategoryIds.contains(item.getCategoryId())) {
                    continue;
                }

                boolean shouldSyncCategory = Boolean.TRUE.equals(syncCategories);
                Long categoryId = shouldSyncCategory
                        ? getCategoryIdFromCache(item.getCategoryId(), item.getCategoryName(),
                                apiProviderId, categoryCache)
                        : null;
                CoursePlatform existing = coursePlatformMapper.selectOne(new QueryWrapper<CoursePlatform>()
                        .eq("dock_param", item.getId())
                        .eq("dock_api_id", apiProviderId));
                BigDecimal remotePrice = item.getPrice();
                if (remotePrice == null || remotePrice.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("第三方商品价格缺失或无效");
                }
                BigDecimal localPrice = remotePrice.multiply(priceMultiplier);

                if (existing != null) {
                    existing.setBasePrice(localPrice);
                    existing.setName(item.getName());
                    if (shouldSyncCategory) {
                        existing.setCategoryId(categoryId);
                    }
                    existing.setUpdateTime(LocalDateTime.now());
                    if (item.getContent() != null) {
                        existing.setDescription(item.getContent());
                    }
                    coursePlatformMapper.updateById(existing);
                    updateCount++;
                } else {
                    CoursePlatform newPlatform = new CoursePlatform();
                    newPlatform.setName(item.getName());
                    newPlatform.setCategoryId(categoryId);
                    newPlatform.setQueryParam(item.getId());
                    newPlatform.setDockParam(item.getId());
                    newPlatform.setBasePrice(localPrice);
                    newPlatform.setQueryApiId(apiProviderId);
                    newPlatform.setDockApiId(apiProviderId);
                    newPlatform.setRateType("MULTIPLY");
                    newPlatform.setStatus(1);
                    newPlatform.setIsSelfOperated(0);
                    newPlatform.setPasswordEnabled(0);
                    newPlatform.setSortOrder(0);
                    newPlatform.setDescription(item.getContent());
                    newPlatform.setCreateTime(LocalDateTime.now());
                    newPlatform.setUpdateTime(LocalDateTime.now());
                    coursePlatformMapper.insert(newPlatform);
                    createCount++;
                }
                successCount++;
            } catch (Exception e) {
                log.error("导入平台失败: remoteId={}, name={}", item.getId(), item.getName(), e);
                failCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items.size());
        result.put("success", successCount);
        result.put("fail", failCount);
        result.put("created", createCount);
        result.put("updated", updateCount);
        if (Boolean.TRUE.equals(syncCategories)) {
            result.put("categoryCreated", categoryCreated);
        }
        return result;
    }

    /**
     * 从缓存中获取分类 ID，避免同一批商品重复查询或创建分类。
     */
    private Long getCategoryIdFromCache(String remoteCategoryId, String categoryName,
                                        Long apiProviderId, Map<String, Long> cache) {
        if (StrUtil.isBlank(remoteCategoryId)) {
            return null;
        }
        String cacheKey = apiProviderId + ":" + remoteCategoryId;
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        Long categoryId = findOrCreateCategory(remoteCategoryId, categoryName, apiProviderId);
        if (categoryId != null) {
            cache.put(cacheKey, categoryId);
        }
        return categoryId;
    }

    private ApiProvider requireEnabledProvider(Long apiProviderId) {
        ApiProvider apiProvider = apiProviderService.loadDecrypted(apiProviderId);
        if (apiProvider == null) {
            throw new BusinessException("API配置不存在");
        }
        if (!Integer.valueOf(1).equals(apiProvider.getStatus())) {
            throw new BusinessException("API接口已禁用");
        }
        return apiProvider;
    }

    private PlatformDockingStrategy requireStrategy(ApiProvider apiProvider) {
        PlatformDockingStrategy strategy = strategyFactory.getStrategy(apiProvider.getProviderType());
        if (strategy == null) {
            throw new BusinessException("不支持的接口类型: " + apiProvider.getProviderType());
        }
        return strategy;
    }

    private BigDecimal normalizeMultiplier(BigDecimal priceMultiplier) {
        if (priceMultiplier == null) {
            return BigDecimal.ONE;
        }
        if (priceMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("价格倍率必须大于0");
        }
        return priceMultiplier;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSyncOrderProgress(Long apiProviderId, Long timestampSeconds, Integer offset) {
        ApiProvider apiProvider = apiProviderService.loadDecrypted(apiProviderId);
        if (apiProvider == null) {
            throw new BusinessException("API配置不存在");
        }

        PlatformDockingStrategy strategy = strategyFactory.getStrategy(apiProvider.getProviderType());
        if (strategy == null) {
            throw new BusinessException("不支持的接口类型: " + apiProvider.getProviderType());
        }

        // 如果未指定时间戳，使用上次同步时间戳（参考 benztb.php）
        Long effectiveTimestamp = timestampSeconds;
        if (effectiveTimestamp == null && apiProvider.getLastSyncTime() != null) {
            effectiveTimestamp = apiProvider.getLastSyncTime();
        }
        
        // 如果还是 null，使用当前时间减去5分钟作为默认值
        if (effectiveTimestamp == null) {
            effectiveTimestamp = java.time.Instant.now().getEpochSecond() - 300;
        }

        // 添加600秒缓冲（benztb.php 第24行）
        Long bufferedTimestamp = effectiveTimestamp - 600;
        log.info("批量同步订单进度: apiProviderId={}, 原始时间戳={}, 缓冲后时间戳={}", 
                apiProviderId, effectiveTimestamp, bufferedTimestamp);

        int totalUpdated = 0;
        int currentOffset = (offset != null) ? offset : 0;
        
        // 模拟 benztb.php 的 while(true) 循环逻辑（第29-78行）
        while (true) {
            List<OrderProgressResult> results = strategy.batchQueryOrderProgress(
                    apiProvider, bufferedTimestamp, currentOffset);

            int batchSize = results.size();
            log.info("批量查询返回 {} 条订单，offset={}", batchSize, currentOffset);

            if (batchSize > 0) {
                // 批量更新订单
                for (OrderProgressResult result : results) {
                    try {
                        // 使用精确匹配：user + pass + kcname + noun + hid (benztb.php 第56行)
                        int updated = courseOrderMapper.updateOrderProgressByFullMatch(
                                result.getStudentAccount(),
                                result.getStudentPassword(),
                                result.getCourseName(),
                                result.getThirdOrderId(),  // noun/cid
                                apiProviderId,
                                result.getOrderStatus(),
                                result.getProgress(),
                                result.getRemarks(),
                                result.getCourseStartTime(),
                                result.getCourseEndTime(),
                                result.getExamStartTime(),
                                result.getExamEndTime()
                        );
                        if (updated > 0) {
                            totalUpdated += updated;
                        }
                    } catch (Exception e) {
                        log.error("更新订单进度失败: apiProviderId={}", apiProviderId, e);
                    }
                }
            }

            // benztb.php 第74行：if($num<500){break;}
            if (batchSize < 500) {
                log.info("本批次返回 {} 条 < 500，结束循环", batchSize);
                break;
            }

            // benztb.php 第36行：$offset+=10000;
            currentOffset += 10000;
            log.info("继续下一批次，新 offset={}", currentOffset);
        }

        // 解密后的 provider 只用于调用第三方，禁止整体回写，避免明文凭据落库。
        ApiProvider syncTimeUpdate = new ApiProvider();
        syncTimeUpdate.setId(apiProviderId);
        syncTimeUpdate.setLastSyncTime(java.time.Instant.now().getEpochSecond());
        apiProviderMapper.updateById(syncTimeUpdate);

        Map<String, Object> result = new HashMap<>();
        result.put("totalUpdated", totalUpdated);
        result.put("message", "已同步" + totalUpdated + "条订单");
        return result;
    }

    /**
     * 将调用方传入的配置统一替换为数据库中的解密副本。
     * 临时/测试配置没有 ID 时保持原对象，避免破坏现有扩展调用。
     */
    private ApiProvider resolveDecryptedProvider(ApiProvider apiProvider) {
        if (apiProvider == null) {
            throw new BusinessException("API配置不存在");
        }
        if (apiProvider.getId() == null) {
            return apiProvider;
        }
        ApiProvider decrypted = apiProviderService.loadDecrypted(apiProvider.getId());
        if (decrypted == null) {
            throw new BusinessException("API配置不存在");
        }
        return decrypted;
    }

    /** 同步分类（参考 benzcron.php 的分类同步逻辑）
     *
     * @param items            平台项列表
     * @param skipCategoryIds  跳过的分类ID列表
     * @return 新创建的分类数量
     */
    private int syncCategories(List<PlatformItem> items, List<String> skipCategoryIds) {
        int createdCount = 0;
        Set<String> processedCategoryIds = new HashSet<>();

        for (PlatformItem item : items) {
            String categoryId = item.getCategoryId();
            String categoryName = item.getCategoryName();

            // 跳过无分类信息或已处理的分类
            if (StrUtil.isBlank(categoryId) || processedCategoryIds.contains(categoryId)) {
                continue;
            }

            // 跳过指定的分类
            if (skipCategoryIds != null && skipCategoryIds.contains(categoryId)) {
                continue;
            }

            processedCategoryIds.add(categoryId);

            try {
                // 检查分类是否已存在
                // 注意：这里暂时不知道apiProviderId，因为syncCategories是批量处理
                // 实际使用中，应该在importPlatforms的主循环中处理，而不是在syncCategories中
                // 为了保持兼容，这里仅解析ID
                Long longCategoryId = parseCategoryId(categoryId);
                if (longCategoryId != null) {
                    PlatformCategory existing = platformCategoryMapper.selectById(longCategoryId);
                    if (existing == null && StrUtil.isNotBlank(categoryName)) {
                        // 创建新分类（注意：这里不填充remoteCategoryId，因为缺少apiProviderId上下文）
                        PlatformCategory newCategory = new PlatformCategory();
                        newCategory.setId(longCategoryId);
                        newCategory.setName(categoryName);
                        newCategory.setSortOrder(0);
                        newCategory.setStatus(1);
                        newCategory.setCreateTime(LocalDateTime.now());
                        newCategory.setUpdateTime(LocalDateTime.now());
                        platformCategoryMapper.insert(newCategory);
                        createdCount++;
                        log.info("批量同步创建分类: id={}, name={}", longCategoryId, categoryName);
                    }
                }
            } catch (Exception e) {
                log.error("同步分类失败: categoryId={}, categoryName={}", categoryId, categoryName, e);
            }
        }

        return createdCount;
    }

    /**
     * 查找或创建分类（支持远程分类ID去重）
     *
     * @param remoteCategoryId 远程API的分类ID
     * @param categoryName     分类名称
     * @param apiProviderId    API提供商ID
     * @return 本地分类ID（Long）
     */
    private Long findOrCreateCategory(String remoteCategoryId, String categoryName, Long apiProviderId) {
        if (StrUtil.isBlank(remoteCategoryId)) {
            return null;
        }

        // 第一步：优先通过 (remoteCategoryId, apiProviderId) 查询是否已存在
        PlatformCategory existingByRemote = platformCategoryMapper.selectOne(
            new QueryWrapper<PlatformCategory>()
                .eq("remote_category_id", remoteCategoryId)
                .eq("remote_api_provider_id", apiProviderId)
        );
        
        if (existingByRemote != null) {
            log.debug("找到已存在的远程分类映射: remoteCategoryId={}, apiProviderId={}, localId={}", 
                    remoteCategoryId, apiProviderId, existingByRemote.getId());
            return existingByRemote.getId();
        }

        // 第二步：尝试解析为本地ID并查询（兼容旧数据）
        Long parsedCategoryId = parseCategoryId(remoteCategoryId);
        if (parsedCategoryId != null) {
            PlatformCategory existingById = platformCategoryMapper.selectById(parsedCategoryId);
            if (existingById != null) {
                // 找到旧分类，补充远程ID信息
                if (StrUtil.isBlank(existingById.getRemoteCategoryId())) {
                    existingById.setRemoteCategoryId(remoteCategoryId);
                    existingById.setRemoteApiProviderId(apiProviderId);
                    existingById.setUpdateTime(LocalDateTime.now());
                    platformCategoryMapper.updateById(existingById);
                    log.info("为已存在的分类补充远程ID: localId={}, remoteCategoryId={}, apiProviderId={}", 
                            parsedCategoryId, remoteCategoryId, apiProviderId);
                }
                return existingById.getId();
            }
        }

        // 第三步：创建新分类
        if (StrUtil.isNotBlank(categoryName)) {
            try {
                PlatformCategory newCategory = new PlatformCategory();
                // 如果能解析为数字ID，使用解析的ID；否则使用自增ID
                if (parsedCategoryId != null) {
                    newCategory.setId(parsedCategoryId);
                }
                newCategory.setName(categoryName);
                newCategory.setRemoteCategoryId(remoteCategoryId);
                newCategory.setRemoteApiProviderId(apiProviderId);
                newCategory.setSortOrder(0);
                newCategory.setStatus(1);
                newCategory.setCreateTime(LocalDateTime.now());
                newCategory.setUpdateTime(LocalDateTime.now());
                platformCategoryMapper.insert(newCategory);
                log.info("创建新分类: localId={}, remoteCategoryId={}, apiProviderId={}, name={}", 
                        newCategory.getId(), remoteCategoryId, apiProviderId, categoryName);
                return newCategory.getId();
            } catch (Exception e) {
                log.error("创建分类失败: remoteCategoryId={}, apiProviderId={}, name={}", 
                        remoteCategoryId, apiProviderId, categoryName, e);
                return null;
            }
        }

        return parsedCategoryId;
    }

    /**
     * 解析分类ID（支持字符串转Long）
     */
    private Long parseCategoryId(String categoryId) {
        if (StrUtil.isBlank(categoryId)) {
            return null;
        }
        try {
            return Long.parseLong(categoryId);
        } catch (NumberFormatException e) {
            log.warn("无法解析分类ID: {}", categoryId);
            return null;
        }
    }
}
