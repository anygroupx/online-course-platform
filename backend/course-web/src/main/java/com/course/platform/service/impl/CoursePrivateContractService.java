package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CourseContractRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.domain.vo.CoursePricePreview;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static com.course.platform.common.security.SecurityAuthorities.*;

/** 协议管理使用用户→协议→钱包锁序，查询仅返回本人或独立权限允许的白名单字段。 */
@Service
@RequiredArgsConstructor
public class CoursePrivateContractService {
    // 开关只阻止创建新协议；既有专户仍可读取、暂停、维护和按原来源退款。
    @Value("${course.private-wallet.enabled:false}")
    private boolean enabled = true;
    private final UserMapper users;
    private final CoursePlatformMapper platforms;
    private final PlatformCategoryMapper categories;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CourseReservedWalletLedgerMapper ledgers;
    private final CourseReservedTransferMapper transfers;
    private final CoursePrivateContractHistoryMapper histories;
    private final UserAuthorityMapper authorities;
    private final CourseReservedWalletService funds;
    private final CoursePricingService pricing;
    private final Clock courseWalletClock;

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> create(CourseContractRequest request,Long operatorId) {
        requireAdmin(operatorId,COURSE_PRIVATE_PRICE_MANAGE);
        if (!enabled) throw conflict("课程密价功能尚未开启");
        validate(request);
        if (!Set.of("ACTIVE","PENDING_ACTIVATION").contains(request.getStatus())) throw invalid("新协议只能创建为生效或待激活");
        User target = target(request.getUserUid(),request.getUserId());
        User locked = active(users.selectByIdForUpdate(target.getId()));
        // 与商品删除互斥，不能在存在性校验后并发创建悬空资金绑定。
        CoursePlatform lockedProduct = platforms.selectByIdForUpdate(request.getPlatformId());
        if (lockedProduct == null) throw missing("课程商品不存在");
        requirePlatform(request.getPlatformId());
        if (contracts.selectByBinding(locked.getId(),request.getPlatformId()) != null) throw conflict("此代理和课程已存在协议，请修改原协议");
        var value = new CoursePrivateContract();
        value.setUserId(locked.getId()); value.setPlatformId(request.getPlatformId());
        apply(value,request); value.setVersion(0); value.setCreatedBy(operatorId); value.setUpdatedBy(operatorId);
        boolean activate = request.getMinInitialDeposit().signum() == 0;
        value.setStatus(activate ? "ACTIVE" : "PENDING_ACTIVATION"); value.setActivatedAt(activate ? now() : null);
        value.setCreateTime(now()); value.setUpdateTime(now());
        if (contracts.insert(value) != 1) throw conflict("协议创建失败");
        var wallet = new CourseReservedWallet(); wallet.setContractId(value.getId()); wallet.setUserId(value.getUserId()); wallet.setPlatformId(value.getPlatformId());
        wallet.setBalance(new BigDecimal("0.00")); wallet.setVersion(0); wallet.setCreateTime(now()); wallet.setUpdateTime(now());
        if (wallets.insert(wallet) != 1) throw conflict("课程钱包创建失败");
        funds.history(value,operatorId,request.getReason().trim(),null);
        return contractView(value,wallet);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> update(Long id,CourseContractRequest request,Long operatorId) {
        requireAdmin(operatorId,COURSE_PRIVATE_PRICE_MANAGE); validate(request);
        if (request.getVersion() == null || request.getVersion() < 0) throw invalid("修改协议必须提供当前版本");
        CoursePrivateContract initial = contracts.selectById(id);
        if (initial == null) throw missing("课程协议不存在");
        active(users.selectByIdForUpdate(initial.getUserId()));
        CoursePrivateContract value = contracts.selectByIdForUpdate(id);
        if (value == null || !Objects.equals(value.getUserId(),initial.getUserId())) throw conflict("协议绑定已变化");
        // 现代权益由发布版本及周期审计维护，旧整表配置接口不能绕过成本、周期和资格规则。
        if(value.getOperationsMode()!=null&&!"LEGACY_PERMANENT".equals(value.getOperationsMode()))throw conflict("运营权益须通过周期审核调整，不能使用旧协议配置接口");
        if (!Objects.equals(request.getVersion(),value.getVersion())) throw conflict("协议版本已变化，请刷新后修改");
        if (!Objects.equals(request.getPlatformId(),value.getPlatformId())) throw invalid("课程资金绑定不能修改");
        if (request.getUserId() != null || request.getUserUid() != null) {
            if (!Objects.equals(target(request.getUserUid(),request.getUserId()).getId(),value.getUserId())) throw invalid("代理资金绑定不能修改");
        }
        CourseReservedWallet initialWallet = wallets.selectByBinding(value.getUserId(),value.getPlatformId());
        if (initialWallet == null) throw conflict("课程专户绑定异常");
        CourseReservedWallet wallet = wallets.selectByIdForUpdate(initialWallet.getId());
        CoursePricingService.requireBinding(value,wallet,value.getUserId(),value.getPlatformId());
        String status = request.getStatus();
        if ("TERMINATED".equals(value.getStatus()) && !"TERMINATED".equals(status)) throw conflict("已终止协议不能恢复");
        if (value.getActivatedAt() != null && "PENDING_ACTIVATION".equals(status)) throw conflict("已激活协议不能取消已获得的资格");
        // 已激活资格不受后续消费或门槛变化影响；首次激活必须有真实余额来源。
        LocalDateTime activated = value.getActivatedAt();
        if ("ACTIVE".equals(status) && activated == null) {
            if (wallet.getBalance().compareTo(request.getMinInitialDeposit()) < 0) throw conflict("预存金额未达到首次激活门槛");
            activated = now();
        }
        String before = funds.snapshot(value); Integer expected = value.getVersion();
        apply(value,request); value.setActivatedAt(activated); value.setUpdatedBy(operatorId);
        if (contracts.updateConfig(value,expected) != 1) throw conflict("协议版本已变化，请刷新后修改");
        value.setVersion(expected+1); value.setUpdateTime(now());
        funds.history(value,operatorId,request.getReason().trim(),before);
        return contractView(value,wallet);
    }

    public CoursePricePreview myPrice(Long userId,Long platformId) { requireSelf(userId); return pricing.preview(userId,platformId); }

    /** 管理员配置参考不受现有密价影响，仅返回价格字段，不泄漏代理余额或凭证。 */
    public Map<String,Object> adminPriceReference(Long operatorId,String userUid,Long platformId) {
        requireAdmin(operatorId,COURSE_PRIVATE_PRICE_MANAGE);
        if (platformId == null || platformId <= 0) throw invalid("课程商品ID无效");
        User user = target(userUid,null);
        CoursePlatform platform = pricing.requirePlatform(platformId);
        BigDecimal basePrice = CourseReservedWalletService.money(platform.getBasePrice(),true);
        BigDecimal ordinaryPrice = pricing.ordinaryPrice(user,platform);
        return Map.of("basePrice",basePrice,"ordinaryPrice",ordinaryPrice,
                "rateType","MULTIPLY".equals(platform.getRateType()) ? "MULTIPLY" : "ADD");
    }

    /** 逐商品返回本人决策，余额不足保留canPurchase=false，不会退回普通费率。 */
    public Map<Long,CoursePricePreview> myPrices(Long userId) {
        requireSelf(userId);
        Set<Long> enabledCategories = new HashSet<>();
        for (PlatformCategory category : categories.selectList(new QueryWrapper<PlatformCategory>().eq("status",1))) enabledCategories.add(category.getId());
        Map<Long,CoursePricePreview> result = new LinkedHashMap<>();
        for (CoursePlatform platform : platforms.selectList(new QueryWrapper<CoursePlatform>().eq("status",1).orderByAsc("sort_order","id"))) {
            if (platform.getCategoryId() == null || enabledCategories.contains(platform.getCategoryId())) result.put(platform.getId(),pricing.preview(userId,platform.getId()));
        }
        return result;
    }

    public IPage<Map<String,Object>> myWallets(Long userId,int page,int pageSize) {
        requireSelf(userId); page(page,pageSize);
        return wallets.selectPage(new Page<>(page,pageSize),new QueryWrapper<CourseReservedWallet>().eq("user_id",userId).orderByDesc("id")).convert(this::walletView);
    }
    public Map<String,Object> myWallet(Long userId,Long id) { requireSelf(userId); return walletView(owned(id,userId)); }
    public IPage<Map<String,Object>> myLedger(Long userId,Long id,int page,int pageSize) {
        requireSelf(userId); page(page,pageSize); owned(id,userId);
        return ledgers.selectPage(new Page<>(page,pageSize),new QueryWrapper<CourseReservedWalletLedger>().eq("wallet_id",id).eq("user_id",userId).orderByDesc("id")).convert(this::ledgerView);
    }
    public IPage<Map<String,Object>> myTransfers(Long userId,int page,int pageSize) {
        requireSelf(userId); page(page,pageSize);
        return transfers.selectPage(new Page<>(page,pageSize),new QueryWrapper<CourseReservedTransfer>().eq("user_id",userId).orderByDesc("id")).convert(this::transferView);
    }

    public IPage<Map<String,Object>> adminContracts(Long operatorId,int page,int pageSize,String userUid,Long platformId) {
        requireAdmin(operatorId,COURSE_PRIVATE_PRICE_READ,COURSE_PRIVATE_PRICE_MANAGE); page(page,pageSize);
        QueryWrapper<CoursePrivateContract> query = new QueryWrapper<>(); filter(query,userUid,platformId);
        return contracts.selectPage(new Page<>(page,pageSize),query.orderByDesc("id")).convert(value -> contractView(value,wallets.selectByBinding(value.getUserId(),value.getPlatformId())));
    }
    public IPage<Map<String,Object>> adminWallets(Long operatorId,int page,int pageSize,String userUid,Long platformId) {
        requireAdmin(operatorId,COURSE_WALLET_READ_ANY,COURSE_WALLET_AUDIT,COURSE_WALLET_RELEASE); page(page,pageSize);
        QueryWrapper<CourseReservedWallet> query = new QueryWrapper<>(); filter(query,userUid,platformId);
        return wallets.selectPage(new Page<>(page,pageSize),query.orderByDesc("id")).convert(this::walletView);
    }
    public IPage<Map<String,Object>> adminLedger(Long operatorId,Long id,int page,int pageSize) {
        requireAdmin(operatorId,COURSE_WALLET_READ_ANY,COURSE_WALLET_AUDIT,COURSE_WALLET_RELEASE); page(page,pageSize);
        if (wallets.selectById(id) == null) throw missing("课程专户不存在");
        return ledgers.selectPage(new Page<>(page,pageSize),new QueryWrapper<CourseReservedWalletLedger>().eq("wallet_id",id).orderByDesc("id")).convert(this::ledgerView);
    }
    public IPage<Map<String,Object>> adminTransfers(Long operatorId,int page,int pageSize,String userUid,Long platformId) {
        requireAdmin(operatorId,COURSE_WALLET_READ_ANY,COURSE_WALLET_AUDIT,COURSE_WALLET_RELEASE); page(page,pageSize);
        var query = new QueryWrapper<CourseReservedTransfer>();
        filter(query,userUid,null);
        if (platformId != null) {
            List<Long> ids = wallets.selectList(new QueryWrapper<CourseReservedWallet>().eq("platform_id",platformId)).stream().map(CourseReservedWallet::getId).toList();
            if (ids.isEmpty()) query.eq("wallet_id",-1); else query.in("wallet_id",ids);
        }
        return transfers.selectPage(new Page<>(page,pageSize),query.orderByDesc("id")).convert(this::transferView);
    }
    public IPage<CoursePrivateContractHistory> adminHistory(Long operatorId,Long id,int page,int pageSize) {
        requireAdmin(operatorId,COURSE_PRIVATE_PRICE_READ,COURSE_PRIVATE_PRICE_MANAGE,COURSE_WALLET_AUDIT); page(page,pageSize);
        if (contracts.selectById(id) == null) throw missing("课程协议不存在");
        return histories.selectPage(new Page<>(page,pageSize),new QueryWrapper<CoursePrivateContractHistory>().eq("contract_id",id).orderByDesc("id"));
    }

    private CourseReservedWallet owned(Long id,Long userId) {
        CourseReservedWallet value = wallets.selectById(id);
        if (value == null || !Objects.equals(value.getUserId(),userId)) throw missing("课程专户不存在");
        return value;
    }
    private void requireSelf(Long userId) {
        if (!Objects.equals(SecurityUtils.getCurrentUserId(),userId)) throw new BusinessException(ResultCode.FORBIDDEN);
        active(users.selectById(userId));
    }
    private void requireAdmin(Long operatorId,String... permissions) {
        if (!Objects.equals(SecurityUtils.getCurrentUserId(),operatorId)) throw new BusinessException(ResultCode.FORBIDDEN);
        SecurityUtils.requireAnyAuthority(permissions); active(users.selectById(operatorId));
        List<String> effective = authorities.findAuthoritiesByUserId(operatorId);
        if (effective == null || Arrays.stream(permissions).noneMatch(effective::contains)) throw new BusinessException(ResultCode.FORBIDDEN);
    }
    private User active(User value) {
        if (value == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        if (!Integer.valueOf(1).equals(value.getStatus())) throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        return value;
    }
    private User target(String uid,Long id) {
        User value;
        if (uid != null && !uid.isBlank()) {
            try { if (!UUID.fromString(uid).toString().equalsIgnoreCase(uid)) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException error) { throw invalid("用户UUID格式无效"); }
            value = users.selectOne(new QueryWrapper<User>().eq("uid",uid));
            if (value == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
            if (id != null && !Objects.equals(id,value.getId())) throw invalid("用户UUID与内部ID不匹配");
        } else {
            if (id == null || id <= 0) throw invalid("必须指定代理用户UUID");
            value = users.selectById(id);
        }
        return active(value);
    }
    private void requirePlatform(Long id) {
        CoursePlatform product = platforms.selectById(id);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        if (product.getCategoryId() != null) {
            PlatformCategory category = categories.selectById(product.getCategoryId());
            if (category == null || !Integer.valueOf(1).equals(category.getStatus())) throw new BusinessException(ResultCode.COURSE_NOT_FOUND);
        }
    }
    private void validate(CourseContractRequest request) {
        if (request == null || request.getPlatformId() == null || request.getPlatformId() <= 0) throw invalid("课程商品ID无效");
        CourseReservedWalletService.money(request.getFixedPrice(),false); CourseReservedWalletService.money(request.getMinInitialDeposit(),true);
        if (request.getStatus() == null || !Set.of("ACTIVE","PENDING_ACTIVATION","SUSPENDED","EXPIRED","TERMINATED").contains(request.getStatus())) throw invalid("协议状态无效");
        if (request.getReason() == null || request.getReason().isBlank() || request.getReason().length() > 255) throw invalid("必须填写不超过255字的操作原因");
        if (request.getValidFrom() != null && request.getValidUntil() != null && !request.getValidUntil().isAfter(request.getValidFrom())) throw invalid("到期时间必须晚于生效时间");
    }
    private void apply(CoursePrivateContract value,CourseContractRequest request) {
        value.setFixedPrice(CourseReservedWalletService.money(request.getFixedPrice(),false)); value.setMinInitialDeposit(CourseReservedWalletService.money(request.getMinInitialDeposit(),true));
        value.setStatus(request.getStatus()); value.setValidFrom(request.getValidFrom()); value.setValidUntil(request.getValidUntil());
    }
    private void filter(QueryWrapper<?> query,String uid,Long platformId) {
        if (uid != null && !uid.isBlank()) {
            User user = users.selectOne(new QueryWrapper<User>().eq("uid",uid)); query.eq("user_id",user == null ? -1 : user.getId());
        }
        if (platformId != null) query.eq("platform_id",platformId);
    }
    private void page(int page,int size) { if (page < 1 || size < 1 || size > 100) throw invalid("页码必须为正数，每页数量为1到100"); }

    private Map<String,Object> contractView(CoursePrivateContract value,CourseReservedWallet wallet) {
        var result = new LinkedHashMap<String,Object>();
        result.put("id",value.getId()); result.put("platformId",value.getPlatformId());
        CoursePlatform product = platforms.selectById(value.getPlatformId()); result.put("platformName",name(product));
        User user = users.selectById(value.getUserId()); result.put("userUid",user == null ? null : user.getUid()); result.put("username",user == null ? null : user.getUsername());
        result.put("fixedPrice",value.getFixedPrice()); result.put("minInitialDeposit",value.getMinInitialDeposit()); result.put("status",value.getStatus());
        result.put("activatedAt",value.getActivatedAt()); result.put("validFrom",value.getValidFrom()); result.put("validUntil",value.getValidUntil()); result.put("version",value.getVersion());
        result.put("walletId",wallet == null ? null : wallet.getId()); result.put("balance",wallet == null ? null : wallet.getBalance());
        Map<String,Object> totals = wallet == null ? null : ledgers.sumByWallet(wallet.getId());
        for (String key : List.of("totalTransferIn","totalTransferredOut","totalConsumed","totalRefunded")) {
            // 返回字符串保留累计金额精度，生命周期累计可高于单笔或余额上限。
            Object amount = totals == null ? null : totals.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(key)).map(Map.Entry::getValue).findFirst().orElse(null);
            result.put(key, amount == null ? "0.00" : new BigDecimal(amount.toString()).setScale(2).toPlainString());
        }
        result.put("createdBy",value.getCreatedBy()); result.put("updatedBy",value.getUpdatedBy()); result.put("createTime",value.getCreateTime()); result.put("updateTime",value.getUpdateTime());
        return result;
    }
    private Map<String,Object> walletView(CourseReservedWallet wallet) {
        CoursePrivateContract contract = contracts.selectById(wallet.getContractId());
        if (contract == null) throw conflict("课程专户绑定异常");
        CoursePricingService.requireBinding(contract,wallet,wallet.getUserId(),wallet.getPlatformId());
        Map<String,Object> result = contractView(contract,wallet);
        result.put("contractId",contract.getId()); result.put("id",wallet.getId()); result.put("contractStatus",contract.getStatus());
        result.put("contractVersion",contract.getVersion()); result.put("version",wallet.getVersion()); return result;
    }
    private Map<String,Object> ledgerView(CourseReservedWalletLedger value) {
        var result = new LinkedHashMap<String,Object>();
        result.put("id",value.getId()); result.put("walletId",value.getWalletId()); result.put("bizType",value.getBizType()); result.put("bizNo",value.getBizNo());
        result.put("direction",value.getDirection()); result.put("amount",value.getAmount()); result.put("balanceBefore",value.getBalanceBefore()); result.put("balanceAfter",value.getBalanceAfter());
        result.put("relatedOrderNo",value.getRelatedOrderNo()); result.put("operatorId",value.getOperatorId()); result.put("remark",value.getRemark()); result.put("createTime",value.getCreateTime()); return result;
    }
    private Map<String,Object> transferView(CourseReservedTransfer value) {
        var result = new LinkedHashMap<String,Object>();
        result.put("id",value.getId()); result.put("walletId",value.getWalletId()); result.put("amount",value.getAmount()); result.put("type",value.getType()); result.put("requestKey",value.getRequestKey());
        result.put("bizNo",value.getBizNo()); result.put("status",value.getStatus()); result.put("reason",value.getReason()); result.put("operatorId",value.getOperatorId()); result.put("approvedBy",value.getApprovedBy());
        result.put("createTime",value.getCreateTime()); result.put("completedAt",value.getCompletedAt()); return result;
    }
    private String name(CoursePlatform product) { return product == null ? "课程已删除" : product.getDisplayName() == null || product.getDisplayName().isBlank() ? product.getName() : product.getDisplayName(); }
    private LocalDateTime now() { return LocalDateTime.now(courseWalletClock); }
    private BusinessException invalid(String text) { return new BusinessException(ResultCode.PARAM_ERROR,text); }
    private BusinessException conflict(String text) { return new BusinessException(ResultCode.CONFLICT,text); }
    private BusinessException missing(String text) { return new BusinessException(ResultCode.NOT_FOUND,text); }
}
