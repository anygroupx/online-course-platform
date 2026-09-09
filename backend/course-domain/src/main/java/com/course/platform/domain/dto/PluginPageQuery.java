package com.course.platform.domain.dto;

import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;

/** Validated again at the service/connector boundary, not only by an HTTP binder. */
public record PluginPageQuery(int page, int pageSize, String keyword) {
    public PluginPageQuery {
        if (page < 1 || page > 10_000 || pageSize < 1 || pageSize > 100
                || (keyword != null && (keyword.length() > 80
                || keyword.codePoints().anyMatch(Character::isISOControl)))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "查询参数超出范围，请检查页码、每页数量和关键词");
        }
        keyword = keyword == null ? "" : keyword.trim();
    }
}
