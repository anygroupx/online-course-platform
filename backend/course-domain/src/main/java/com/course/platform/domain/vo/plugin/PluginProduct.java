package com.course.platform.domain.vo.plugin;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** Read-only upstream quotation, not a local price or a purchasable CoursePlatform. */
public record PluginProduct(String id, String name,
                            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
                            String priceUnit) {}
