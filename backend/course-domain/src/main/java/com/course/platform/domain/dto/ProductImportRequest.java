package com.course.platform.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 从第三方商品列表选择导入的请求。
 */
@Data
public class ProductImportRequest {

    @NotNull(message = "API接口不能为空")
    private Long apiProviderId;

    /** 查询商品时使用的远程分类；导入时重新向上游查询同一分类，不信任前端商品快照。 */
    @Size(max = 50, message = "分类ID长度不能超过50")
    private String categoryId;

    /** 远程商品ID列表。价格与商品信息由后端重新查询，禁止信任前端快照。 */
    @NotEmpty(message = "请至少选择一个商品")
    @Size(max = 500, message = "单次最多导入500个商品")
    private List<@NotBlank(message = "商品ID不能为空") @Size(max = 50, message = "商品ID长度不能超过50") String> productIds;

    @DecimalMin(value = "0.01", message = "价格倍率必须大于0")
    private BigDecimal priceMultiplier = BigDecimal.ONE;

    private Boolean syncCategories = true;
}
