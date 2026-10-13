package com.course.platform.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 协议变化的前后快照，保留操作人、原因及时间用于审计。 */
@Data
@TableName("course_private_contract_history")
public class CoursePrivateContractHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long contractId;

    private Long operatorId;

    private String reason;

    private String beforeJson;

    private String afterJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
