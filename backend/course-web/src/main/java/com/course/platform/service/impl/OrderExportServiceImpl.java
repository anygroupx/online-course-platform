package com.course.platform.service.impl;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.application.service.order.OrderExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单导出服务实现。
 * 安全策略：禁止导出学生明文密码。
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
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = getHeadersByFormat(format);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (CourseOrder order : orders) {
                Row row = sheet.createRow(rowNum++);
                fillRowByFormat(row, order, format, dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 3000) {
                    sheet.setColumnWidth(i, 3000);
                }
                if (sheet.getColumnWidth(i) > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }

            workbook.write(outputStream);
            return new ByteArrayResource(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("导出XLSX失败", e);
            throw new RuntimeException("导出XLSX失败: " + e.getMessage());
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private String[] getHeadersByFormat(Integer format) {
        // 兼容旧 format 编号，但密码列改为“密码状态”，永不导出明文
        switch (format == null ? 1 : format) {
            case 1:
                return new String[]{"学校名称", "学生账号", "密码状态", "课程名称"};
            case 2:
                return new String[]{"学生账号", "密码状态", "课程名称"};
            case 3:
                return new String[]{"学校名称", "学生账号", "密码状态"};
            case 4:
                return new String[]{"学生账号", "密码状态"};
            default:
                return new String[]{"学校名称", "学生账号", "密码状态", "课程名称", "订单号"};
        }
    }

    private void fillRowByFormat(Row row, CourseOrder order, Integer format, CellStyle style) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";
        String passwordStatus = (order.getStudentPassword() != null && !order.getStudentPassword().isBlank())
                ? "已设置" : "未设置";
        String orderNo = order.getOrderNo() != null ? order.getOrderNo() : "";

        String[] values;
        switch (format == null ? 1 : format) {
            case 1:
                values = new String[]{school, account, passwordStatus, courseName};
                break;
            case 2:
                values = new String[]{account, passwordStatus, courseName};
                break;
            case 3:
                values = new String[]{school, account, passwordStatus};
                break;
            case 4:
                values = new String[]{account, passwordStatus};
                break;
            default:
                values = new String[]{school, account, passwordStatus, courseName, orderNo};
                break;
        }
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private String formatOrderForExport(CourseOrder order, Integer format) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";
        String passwordStatus = (order.getStudentPassword() != null && !order.getStudentPassword().isBlank())
                ? "已设置" : "未设置";
        String orderNo = order.getOrderNo() != null ? order.getOrderNo() : "";

        switch (format == null ? 1 : format) {
            case 1:
                return String.format("%s %s %s %s", school, account, passwordStatus, courseName);
            case 2:
                return String.format("%s %s %s", account, passwordStatus, courseName);
            case 3:
                return String.format("%s %s %s", school, account, passwordStatus);
            case 4:
                return String.format("%s %s", account, passwordStatus);
            default:
                return String.format("%s %s %s %s %s", school, account, passwordStatus, courseName, orderNo);
        }
    }
}
