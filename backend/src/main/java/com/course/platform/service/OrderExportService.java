package com.course.platform.service;

import com.course.platform.domain.entity.CourseOrder;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;

/**
 * 订单导出服务
 */
public interface OrderExportService {
    
    /**
     * 导出订单为TXT格式
     * @param orders 订单列表
     * @param format 内容格式(1-4)
     * @return 导出内容
     */
    String exportAsTxt(List<CourseOrder> orders, Integer format);
    
    /**
     * 导出订单为XLSX格式
     * @param orders 订单列表
     * @param format 内容格式(1-4)
     * @return 字节数组资源
     */
    ByteArrayResource exportAsXlsx(List<CourseOrder> orders, Integer format);
}
