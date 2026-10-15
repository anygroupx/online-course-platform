package com.course.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.dto.CourseWalletTransferRequest;
import com.course.platform.domain.entity.*;
import com.course.platform.infra.persistence.mapper.*;
import com.course.platform.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 资金释放申请不冻结余额，财务批准时双账本和执行身份在同一事务提交。 */
@Service
@RequiredArgsConstructor
public class CourseWalletReleaseRequestService {
    /** 财务展示原拥有者公开身份，避免把内部用户编号和幂等摘要作为界面数据。 */
    public java.util.Map<String,Object> administrativeView(CourseWalletReleaseRequest value){
        var result=new java.util.LinkedHashMap<String,Object>();var user=users.selectById(value.getUserId());result.put("id",value.getId());result.put("walletId",value.getWalletId());result.put("userUid",user==null?null:user.getUid());result.put("username",user==null?null:user.getUsername());result.put("amount",value.getAmount().toPlainString());result.put("reason",value.getReason());result.put("status",value.getStatus());result.put("reviewReason",value.getReviewReason());result.put("reviewedAt",value.getReviewedAt());result.put("executedTransferId",value.getExecutedTransferId());result.put("version",value.getVersion());result.put("createdAt",value.getCreatedAt());return result;
    }
    private final CourseWalletReleaseRequestMapper requests;
    private final UserMapper users;
    private final CoursePrivateContractMapper contracts;
    private final CourseReservedWalletMapper wallets;
    private final CourseReservedTransferMapper transfers;
    private final CourseReservedWalletService funds;
    private final CoursePrivateOperationsAccess access;
    private final CoursePrivateOperationsTime time;
    @Value("${course.private-operations.enabled:false}") private boolean enabled;

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseWalletReleaseRequest submit(Long userId,Long walletId,BigDecimal amount,String requestKey,String reason) {
        access.self(userId);amount=CourseReservedWalletService.money(amount,false);key(requestKey);CoursePrivatePlanService.reason(reason);
        if(users.selectByIdForUpdate(userId)==null) throw missing();
        var existing=requests.selectByRequestKey(userId,requestKey);
        String identity=fingerprint(walletId,amount,reason);
        // 同键重试读取真实历史结果，即使后续关闭灰度也不能丢失已提交的申请。
        if(existing!=null){if(!Objects.equals(existing.getOperationFingerprint(),identity)) throw conflict("转回申请幂等键冲突");return existing;}
        if(!enabled) throw conflict("课程资金转回申请功能尚未开启");
        var initial=wallets.selectById(walletId);if(initial==null||!Objects.equals(initial.getUserId(),userId)) throw missing();
        var contract=contracts.selectByIdForUpdate(initial.getContractId());var wallet=wallets.selectByIdForUpdate(walletId);
        CoursePricingService.requireBinding(contract,wallet,userId,initial.getPlatformId());
        if(wallet.getBalance().compareTo(amount)<0) throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT,"申请金额超过当前可用专户余额");
        var result=new CourseWalletReleaseRequest();result.setWalletId(walletId);result.setUserId(userId);result.setAmount(amount);result.setReason(reason.trim());result.setStatus("PENDING");
        result.setRequestKey(requestKey);result.setOperationFingerprint(identity);result.setVersion(0);result.setCreatedAt(time.now());result.setUpdatedAt(time.now());
        if(requests.insert(result)!=1) throw conflict("转回申请保存失败");return result;
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseWalletReleaseRequest cancel(Long userId,Long requestId) {
        access.self(userId);var initial=owned(requestId,userId);lockBinding(initial);
        var value=requests.selectByIdForUpdate(requestId);if(value==null) throw missing();
        if("CANCELLED".equals(value.getStatus()))return value;
        if(!"PENDING".equals(value.getStatus()))throw conflict("已经审核的申请不能撤销");
        if(requests.decideWithoutTransfer(requestId,value.getVersion(),"CANCELLED",userId,"代理撤销转回申请",time.now())!=1)throw conflict("转回申请已变化");
        return requests.selectById(requestId);
    }

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseWalletReleaseRequest review(Long requestId,Long operatorId,boolean approved,String reason) {return review(requestId,null,operatorId,approved,reason);}

    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CourseWalletReleaseRequest review(Long requestId,Integer expectedVersion,Long operatorId,boolean approved,String reason) {
        access.require(operatorId,"course-wallet-release:review");
        // 新审批权不隐含资金处置权，两项均检查当前数据库，避免权限撤销后的旧会话继续出账。
        if(approved)access.require(operatorId,"course-wallet:release");
        CoursePrivatePlanService.reason(reason);var initial=requests.selectById(requestId);if(initial==null) throw missing();
        var wallet=lockBinding(initial);var value=requests.selectByIdForUpdate(requestId);if(value==null)throw missing();
        if(approved&&"EXECUTED".equals(value.getStatus())||!approved&&"REJECTED".equals(value.getStatus()))return value;
        if(!"PENDING".equals(value.getStatus()))throw conflict("该转回申请已审核或已撤销");
        if(expectedVersion!=null&&!Objects.equals(value.getVersion(),expectedVersion))throw conflict("转回申请版本已变化");
        if(!approved){if(requests.decideWithoutTransfer(requestId,value.getVersion(),"REJECTED",operatorId,reason,time.now())!=1)throw conflict("转回申请审核冲突");return requests.selectById(requestId);}
        // 申请期间代理仍能消费；审批必须重新核验余额，失败整体回滚并保留PENDING。
        if(wallet.getBalance().compareTo(value.getAmount())<0)throw new BusinessException(ResultCode.BALANCE_INSUFFICIENT,"余额已变化，当前可用专户余额不足，申请仍待审核");
        var command=new CourseWalletTransferRequest();command.setAmount(value.getAmount());command.setReason(reason);command.setRequestKey("release_"+requestId);
        // 不能把旧财务接口同键生成的历史转回冒认为本申请执行，账户行锁保护此检查。
        if(transfers.selectByRequestKey(value.getUserId(),command.getRequestKey())!=null)throw conflict("财务执行幂等键已被其他操作占用，需人工核查");
        var transfer=funds.release(wallet.getId(),operatorId,command);
        if(requests.markExecuted(requestId,value.getVersion(),operatorId,reason,transfer.getId(),time.now())!=1)throw conflict("财务执行状态保存失败");
        return requests.selectById(requestId);
    }

    public List<CourseWalletReleaseRequest> list(Long userId) {readAccess(userId);return requests.selectList(query(userId,null).last("LIMIT 100"));}
    public IPage<CourseWalletReleaseRequest> page(Long userId,int page,int pageSize,String status) {readAccess(userId);CoursePrivatePlanService.page(page,pageSize);return requests.selectPage(new Page<>(page,pageSize),query(userId,status));}
    private QueryWrapper<CourseWalletReleaseRequest> query(Long userId,String status){return new QueryWrapper<CourseWalletReleaseRequest>().eq(userId!=null,"user_id",userId).eq(status!=null&&!status.isBlank(),"status",status).orderByDesc("id");}
    private void readAccess(Long userId){if(userId!=null)access.self(userId);else access.require(SecurityUtils.getCurrentUserId(),"course-wallet-release:review","course-wallet:audit");}
    private CourseWalletReleaseRequest owned(Long id,Long userId){var value=requests.selectById(id);if(value==null||!Objects.equals(value.getUserId(),userId))throw missing();return value;}
    private CourseReservedWallet lockBinding(CourseWalletReleaseRequest value) {
        if(users.selectByIdForUpdate(value.getUserId())==null)throw missing();
        var initial=wallets.selectById(value.getWalletId());if(initial==null)throw missing();
        var contract=contracts.selectByIdForUpdate(initial.getContractId());var wallet=wallets.selectByIdForUpdate(value.getWalletId());
        CoursePricingService.requireBinding(contract,wallet,value.getUserId(),initial.getPlatformId());return wallet;
    }
    private void key(String value){if(value==null||!value.matches("[A-Za-z0-9_-]{1,64}"))throw new BusinessException(ResultCode.PARAM_ERROR,"必须提供1到64位请求幂等键");}
    private String fingerprint(Long wallet,BigDecimal amount,String reason){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((wallet+"\n"+amount.toPlainString()+"\n"+reason.trim()).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("资金申请摘要算法不可用",e);}}
    private BusinessException conflict(String reason){return new BusinessException(ResultCode.CONFLICT,reason);}
    private BusinessException missing(){return new BusinessException(ResultCode.NOT_FOUND,"转回申请或课程专户不存在");}
}
