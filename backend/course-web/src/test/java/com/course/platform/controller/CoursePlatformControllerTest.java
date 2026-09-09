package com.course.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.course.platform.domain.entity.CoursePlatform;
import com.course.platform.domain.entity.PlatformCategory;
import com.course.platform.infra.persistence.mapper.CoursePlatformMapper;
import com.course.platform.infra.persistence.mapper.PlatformCategoryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoursePlatformControllerTest {

    private final CoursePlatformMapper coursePlatformMapper = mock(CoursePlatformMapper.class);
    private final PlatformCategoryMapper platformCategoryMapper = mock(PlatformCategoryMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                CoursePlatform.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new CoursePlatformController(coursePlatformMapper, platformCategoryMapper)).build();
    }

    private PlatformCategory category(Long id, Integer status) {
        PlatformCategory c = new PlatformCategory();
        c.setId(id);
        c.setStatus(status);
        return c;
    }

    private String capturedSql() throws Exception {
        when(coursePlatformMapper.selectList(any())).thenReturn(List.of());
        mvc.perform(get("/courses")).andExpect(status().isOk());
        ArgumentCaptor<LambdaQueryWrapper<CoursePlatform>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(coursePlatformMapper).selectList(captor.capture());
        return captor.getValue().getSqlSegment();
    }

    @Test
    void enabledCategoriesProduceCategoryInFilter() throws Exception {
        when(platformCategoryMapper.selectList(any()))
                .thenReturn(List.of(category(1L, 1), category(2L, 1)));
        String sql = capturedSql();
        assertTrue(sql.contains("category_id IS NULL"));
        assertTrue(sql.contains("category_id IN"));
    }

    @Test
    void disabledCategoriesAreExcludedFromInFilter() throws Exception {
        // 分类1启用、分类2禁用：DB 按 status=1 过滤后只返回启用分类1
        when(platformCategoryMapper.selectList(any())).thenReturn(List.of(category(1L, 1)));
        String sql = capturedSql();
        assertTrue(sql.contains("category_id IS NULL"));
        assertTrue(sql.contains("category_id IN"));
        assertFalse(sql.contains("category_id IN (2"));
    }

    @Test
    void noEnabledCategoriesKeepsOnlyUncategorizedCondition() throws Exception {
        // 没有任何启用分类：DB 按 status=1 过滤后返回空集
        when(platformCategoryMapper.selectList(any())).thenReturn(List.of());
        String sql = capturedSql();
        assertTrue(sql.contains("category_id IS NULL"));
        assertFalse(sql.contains("category_id IN"));
    }
}