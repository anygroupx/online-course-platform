package com.course.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.course.platform.shared.result.Result;
import com.course.platform.domain.dto.CardQueryRequest;
import com.course.platform.domain.dto.CardRechargeRequest;
import com.course.platform.domain.dto.GenerateCardRequest;
import com.course.platform.domain.entity.RechargeCard;
import com.course.platform.service.RechargeCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 充值卡密管理控制器
 * 
 * @author AI Assistant
 * @since 2025-01-17
 * Source: 基于现有系统架构设计
 */
@Tag(name = "充值卡密管理", description = "充值卡密生成、查询、禁用等接口")
@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class RechargeCardController {

    private final RechargeCardService rechargeCardService;

    /**
     * 生成充值卡密（管理员）
     */
    @Operation(summary = "生成充值卡密", description = "管理员生成充值卡密")
    @PostMapping("/generate")
    public Result<List<RechargeCard>> generateCards(@Valid @RequestBody GenerateCardRequest request,
                                                    Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        List<RechargeCard> cards = rechargeCardService.generateCards(request, operatorId);
        return Result.success("卡密生成成功", cards);
    }

    /**
     * 查询充值卡密列表（管理员）
     */
    @Operation(summary = "查询充值卡密列表", description = "分页查询充值卡密列表")
    @GetMapping
    public Result<IPage<RechargeCard>> queryCards(@RequestParam(required = false) String cardNo,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) Long usedBy,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        CardQueryRequest request = new CardQueryRequest();
        request.setCardNo(cardNo);
        request.setStatus(status);
        request.setUsedBy(usedBy);
        
        IPage<RechargeCard> result = rechargeCardService.queryCards(request, page, pageSize);
        return Result.success(result);
    }

    /**
     * 获取卡密详情（管理员）
     */
    @Operation(summary = "获取卡密详情", description = "根据ID获取卡密详细信息")
    @GetMapping("/{id}")
    public Result<RechargeCard> getCard(@PathVariable Long id) {
        RechargeCard card = rechargeCardService.getCardById(id);
        return Result.success(card);
    }

    /**
     * 禁用卡密（管理员）
     */
    @Operation(summary = "禁用卡密", description = "禁用指定的充值卡密")
    @PostMapping("/{id}/disable")
    public Result<Void> disableCard(@PathVariable Long id,
                                    Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        rechargeCardService.disableCard(id, operatorId);
        return Result.success("卡密禁用成功");
    }

    /**
     * 用户自助充值
     */
    @Operation(summary = "用户自助充值", description = "用户使用卡密进行自助充值")
    @PostMapping("/recharge")
    public Result<BigDecimal> rechargeByCard(@Valid @RequestBody CardRechargeRequest request,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        BigDecimal amount = rechargeCardService.rechargeByCard(request, userId);
        return Result.success("充值成功", amount);
    }
}
