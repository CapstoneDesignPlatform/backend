package com.capdi.backend.global.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ExcelFileGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Section(String title, List<String> headers, List<List<Object>> rows) {
    }

    // 목록 화면처럼 하나의 표만 필요한 Excel 파일을 생성한다.
    public byte[] generate(String sheetName, List<String> headers, List<List<Object>> rows) {
        return generateSections(sheetName, List.of(new Section(null, headers, rows)));
    }

    // 상세 화면처럼 여러 정보 영역이 필요한 Excel 파일을 생성한다.
    public byte[] generateSections(String sheetName, List<Section> sections) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);

            int rowIndex = 0;
            int maxColumnCount = 0;

            for (Section section : sections) {
                if (section.title() != null && !section.title().isBlank()) {
                    Row titleRow = sheet.createRow(rowIndex++);
                    Cell titleCell = titleRow.createCell(0);
                    titleCell.setCellValue(section.title());
                    titleCell.setCellStyle(titleStyle);
                }

                writeHeader(sheet.createRow(rowIndex++), section.headers(), headerStyle);
                rowIndex = writeRows(sheet, rowIndex, section.rows());
                rowIndex++;

                maxColumnCount = Math.max(maxColumnCount, section.headers().size());
            }

            resizeColumns(sheet, maxColumnCount);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Excel file generation failed.", e);
        }
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 13);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private void writeHeader(Row headerRow, List<String> headers, CellStyle headerStyle) {
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    private int writeRows(Sheet sheet, int startRowIndex, List<List<Object>> rows) {
        for (int rowOffset = 0; rowOffset < rows.size(); rowOffset++) {
            Row row = sheet.createRow(startRowIndex + rowOffset);
            List<Object> values = rows.get(rowOffset);

            for (int colIndex = 0; colIndex < values.size(); colIndex++) {
                writeCell(row.createCell(colIndex), values.get(colIndex));
            }
        }

        return startRowIndex + rows.size();
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }

        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }

        if (value instanceof LocalDate date) {
            cell.setCellValue(date.format(DATE_FORMATTER));
            return;
        }

        if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime.format(DATE_TIME_FORMATTER));
            return;
        }

        cell.setCellValue(value.toString());
    }

    private void resizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(currentWidth + 1024, 12000));
        }
    }
}
