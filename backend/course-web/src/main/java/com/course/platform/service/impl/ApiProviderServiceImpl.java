package com.course.platform.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.course.platform.common.exception.BusinessException;
import com.course.platform.common.result.ResultCode;
import com.course.platform.domain.entity.ApiProvider;
import com.course.platform.infra.persistence.mapper.ApiProviderMapper;
import com.course.platform.application.service.platform.ApiProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方API接口服务实现类
 * 
 * @author AI Assistant
 * @since 2025-01-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiProviderServiceImpl implements ApiProviderService {

    private final ApiProviderMapper apiProviderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApiProvider(ApiProvider apiProvider) {
        // 检查名称是否重复
        ApiProvider existing = apiProviderMapper.selectOne(new LambdaQueryWrapper<ApiProvider>()
                .eq(ApiProvider::getName, apiProvider.getName()));

        if (existing != null) {
            throw new BusinessException("接口名称已存在");
        }

        apiProviderMapper.insert(apiProvider);

        log.info("API接口创建成功：id={}, name={}", apiProvider.getId(), apiProvider.getName());

        return apiProvider.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApiProvider(ApiProvider apiProvider) {
        ApiProvider existing = apiProviderMapper.selectById(apiProvider.getId());
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API接口不存在");
        }

        apiProviderMapper.updateById(apiProvider);

        log.info("API接口更新成功：id={}", apiProvider.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiProvider(Long id) {
        ApiProvider apiProvider = apiProviderMapper.selectById(id);
        if (apiProvider == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "API接口不存在");
        }

        apiProviderMapper.deleteById(id);

        log.info("API接口删除成功：id={}", id);
    }

    @Override
    public IPage<ApiProvider> queryApiProviders(String keyword, Integer status, Integer page, Integer pageSize) {
        Page<ApiProvider> pageObj = new Page<>(page, pageSize);

        LambdaQueryWrapper<ApiProvider> queryWrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.like(ApiProvider::getName, keyword)
                    .or()
                    .like(ApiProvider::getProviderType, keyword);
        }

        if (status != null) {
            queryWrapper.eq(ApiProvider::getStatus, status);
        }

        queryWrapper.orderByDesc(ApiProvider::getCreateTime);

        return apiProviderMapper.selectPage(pageObj, queryWrapper);
    }
}

