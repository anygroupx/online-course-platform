package com.course.platform.service.impl;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.service.OrderExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单导出服务实现
 */
@Slf4j
@Service
public class OrderExportServiceImpl implements OrderExportService {

    @Override
    public String exportAsTxt(List<CourseOrder> orders, Integer format) {
        List<String> exportLines = new ArrayList<>();
        
        for (CourseOrder order : orders) {
            String line = formatOrderForExport(order, format);
            if (line != null) {
                exportLines.add(line);
            }
        }
        
        return String.join("\n", exportLines);
    }

    @Override
    public ByteArrayResource exportAsXlsx(List<CourseOrder> orders, Integer format) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("订单数据");
            
            // 创建标题样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // 创建标题行
            Row headerRow = sheet.createRow(0);
            String[] headers = getHeadersByFormat(format);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // 填充数据
            int rowNum = 1;
            for (CourseOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                fillRowByFormat(row, order, format, dataStyle);
            }
            
            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // 设置最小列宽
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
                // 设置最大列宽
                if (sheet.getColumnWidth(i) > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }
            
            workbook.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            
            return new ByteArrayResource(bytes);
            
        } catch (Exception e) {
            log.error("导出XLSX失败", e);
            throw new RuntimeException("导出XLSX失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建标题样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // 设置背景色
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // 设置边框
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // 设置对齐
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        // 设置字体
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
        return style;
    }
    
    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        // 设置边框
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        // 设置对齐
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    /**
     * 根据格式获取表头
     */
    private String[] getHeadersByFormat(Integer format) {
        switch (format) {
            case 1: // 学校+账号+密码+课程名字
                return new String[]{"学校名称", "学生账号", "学生密码", "课程名称"};
            case 2: // 账号+密码+课程名字
                return new String[]{"学生账号", "学生密码", "课程名称"};
            case 3: // 学校+账号+密码
                return new String[]{"学校名称", "学生账号", "学生密码"};
            case 4: // 账号+密码
                return new String[]{"学生账号", "学生密码"};
            default:
                return new String[]{"学校名称", "学生账号", "学生密码", "课程名称"};
        }
    }
    
    /**
     * 根据格式填充行数据
     */
    private void fillRowByFormat(Row row, CourseOrder order, Integer format, CellStyle style) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String password = order.getStudentPassword() != null ? order.getStudentPassword() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";
        
        int cellNum = 0;
        Cell cell;
        
        switch (format) {
            case 1: // 学校+账号+密码+课程名字
                cell = row.createCell(cellNum++);
                cell.setCellValue(school);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(account);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(password);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(courseName);
                cell.setCellStyle(style);
                break;
                
            case 2: // 账号+密码+课程名字
                cell = row.createCell(cellNum++);
                cell.setCellValue(account);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(password);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(courseName);
                cell.setCellStyle(style);
                break;
                
            case 3: // 学校+账号+密码
                cell = row.createCell(cellNum++);
                cell.setCellValue(school);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(account);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(password);
                cell.setCellStyle(style);
                break;
                
            case 4: // 账号+密码
                cell = row.createCell(cellNum++);
                cell.setCellValue(account);
                cell.setCellStyle(style);
                
                cell = row.createCell(cellNum++);
                cell.setCellValue(password);
                cell.setCellStyle(style);
                break;
        }
    }
    
    /**
     * 根据格式格式化订单信息（用于TXT导出）
     */
    private String formatOrderForExport(CourseOrder order, Integer format) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String password = order.getStudentPassword() != null ? order.getStudentPassword() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";

        switch (format) {
            case 1: // 学校+账号+密码+课程名字
                return String.format("%s %s %s %s", school, account, password, courseName);
            case 2: // 账号+密码+课程名字
                return String.format("%s %s %s", account, password, courseName);
            case 3: // 学校+账号+密码
                return String.format("%s %s %s", school, account, password);
            case 4: // 账号+密码
                return String.format("%s %s", account, password);
            default:
                return null;
        }
    }
}
