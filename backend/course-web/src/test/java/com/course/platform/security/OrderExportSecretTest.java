package com.course.platform.security;

import com.course.platform.domain.entity.CourseOrder;
import com.course.platform.service.impl.OrderExportServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OrderExportSecretTest {
    @Test
    void textAndSpreadsheetExportsNeverContainStudentPassword() throws Exception {
        CourseOrder order = new CourseOrder();
        order.setSchoolName("Example University");
        order.setStudentAccount("student-account");
        order.setStudentPassword("never-export-this-password");
        order.setCourseName("Course");
        OrderExportServiceImpl service = new OrderExportServiceImpl();

        String text = service.exportAsTxt(List.of(order), 1);
        assertFalse(text.contains("never-export-this-password"));
        assertFalse(text.contains("密码"));

        byte[] bytes = service.exportAsXlsx(List.of(order), 1).getByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder cells = new StringBuilder();
            workbook.getSheetAt(0).forEach(row -> row.forEach(cell -> cells.append(cell).append('\n')));
            assertFalse(cells.toString().contains("never-export-this-password"));
            assertFalse(cells.toString().contains("密码"));
        }
    }
}
