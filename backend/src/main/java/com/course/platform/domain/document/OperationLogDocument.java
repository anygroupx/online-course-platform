package com.course.platform.domain.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 操作日志 Elasticsearch 文档实体
 * 
 * 用于全文搜索，支持中文分词
 * 
 * @author AI Assistant
 * @since 2025-12-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "operation_log")
public class OperationLogDocument {

    /**
     * 日志ID（与数据库主键一致）
     */
    @Id
    private Long id;

    /**
     * 用户ID
     */
    @Field(type = FieldType.Long)
    private Long userId;

    /**
     * 操作类型
     * 使用 Keyword 类型，支持精确匹配和聚合
     */
    @Field(type = FieldType.Keyword)
    private String operationType;

    /**
     * 操作描述
     * 使用 Text 类型，支持全文搜索
     * 注：如需使用IK分词器，需在ES中安装IK插件，此处使用默认分词
     */
    @Field(type = FieldType.Text)
    private String operationDesc;

    /**
     * 金额变动
     */
    @Field(type = FieldType.Double)
    private BigDecimal amountChange;

    /**
     * 操作后余额
     */
    @Field(type = FieldType.Double)
    private BigDecimal balanceAfter;

    /**
     * IP地址
     * 使用 Keyword 类型，支持精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String ipAddress;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createTime;
}
