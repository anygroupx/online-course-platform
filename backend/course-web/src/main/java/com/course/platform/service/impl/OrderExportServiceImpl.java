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
        // 兼容旧 format 编号，但彻底移除密码列
        switch (format == null ? 1 : format) {
            case 1:
                return new String[]{"学校名称", "学生账号", "课程名称"};
            case 2:
                return new String[]{"学生账号", "课程名称"};
            case 3:
                return new String[]{"学校名称", "学生账号"};
            case 4:
                return new String[]{"学生账号"};
            case 5:
                return new String[]{"订单号","学校名称","学生账号","课程名称","成交金额","价格类型","支付来源","退款目标","退款状态"};
            default:
                return new String[]{"学校名称", "学生账号", "课程名称", "订单号"};
        }
    }

    private void fillRowByFormat(Row row, CourseOrder order, Integer format, CellStyle style) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";
        String orderNo = order.getOrderNo() != null ? order.getOrderNo() : "";

        String[] values;
        switch (format == null ? 1 : format) {
            case 1:
                values = new String[]{school, account, courseName};
                break;
            case 2:
                values = new String[]{account, courseName};
                break;
            case 3:
                values = new String[]{school, account};
                break;
            case 4:
                values = new String[]{account};
                break;
            case 5:
                values = financialValues(order);
                break;
            default:
                values = new String[]{school, account, courseName, orderNo};
                break;
        }
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    /** 财务白名单不含学生口令、钱包ID、请求指纹等无关敏感数据。 */
    private String[] financialValues(CourseOrder order) {
        String source = "COURSE_WALLET".equals(order.getPaymentSource()) ? "课程专户" : "普通余额";
        return new String[]{order.getOrderNo()==null?"":order.getOrderNo(),order.getSchoolName()==null?"":order.getSchoolName(),
                order.getStudentAccount()==null?"":order.getStudentAccount(),order.getCourseName()==null?"":order.getCourseName(),
                order.getAmount()==null?"":order.getAmount().toPlainString(),"PRIVATE".equals(order.getPriceSource())?"专属密价":"普通费率价",
                source,source,order.getRefundedAt()==null?"未记录退款":"已退款"};
    }

    private String formatOrderForExport(CourseOrder order, Integer format) {
        String school = order.getSchoolName() != null ? order.getSchoolName() : "";
        String account = order.getStudentAccount() != null ? order.getStudentAccount() : "";
        String courseName = order.getCourseName() != null ? order.getCourseName() : "";
        String orderNo = order.getOrderNo() != null ? order.getOrderNo() : "";

        switch (format == null ? 1 : format) {
            case 1:
                return String.format("%s %s %s", school, account, courseName);
            case 2:
                return String.format("%s %s", account, courseName);
            case 3:
                return String.format("%s %s", school, account);
            case 4:
                return account;
            case 5:
                return String.join("\t",financialValues(order));
            default:
                return String.format("%s %s %s %s", school, account, courseName, orderNo);
        }
    }
}
