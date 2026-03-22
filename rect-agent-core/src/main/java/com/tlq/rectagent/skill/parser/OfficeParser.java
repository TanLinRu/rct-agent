package com.tlq.rectagent.skill.parser;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class OfficeParser implements DocumentParser {
    @Override
    public String parse(byte[] content, String url) throws Exception {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.endsWith(".doc") || lowerUrl.endsWith(".docx")) {
            return parseWordDocument(content);
        } else if (lowerUrl.endsWith(".xls") || lowerUrl.endsWith(".xlsx")) {
            return parseExcelDocument(content, lowerUrl);
        } else if (lowerUrl.endsWith(".ppt") || lowerUrl.endsWith(".pptx")) {
            return parsePowerPointDocument(content);
        }
        throw new UnsupportedOperationException("Unsupported Office document format: " + url);
    }

    private String parseWordDocument(byte[] content) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    text.append(run.getText(0));
                }
                text.append("\n");
            }
            return text.toString();
        }
    }

    private String parseExcelDocument(byte[] content, String url) throws Exception {
        try (Workbook workbook = url.toLowerCase().endsWith(".xlsx") ? 
             new XSSFWorkbook(new ByteArrayInputStream(content)) : 
             new HSSFWorkbook(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                text.append("Sheet: " + sheet.getSheetName() + "\n");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING:
                                text.append(cell.getStringCellValue()).append("\t");
                                break;
                            case NUMERIC:
                                text.append(cell.getNumericCellValue()).append("\t");
                                break;
                            case BOOLEAN:
                                text.append(cell.getBooleanCellValue()).append("\t");
                                break;
                            default:
                                text.append("\t");
                                break;
                        }
                    }
                    text.append("\n");
                }
                text.append("\n");
            }
            return text.toString();
        }
    }

    private String parsePowerPointDocument(byte[] content) throws Exception {
        // 简化实现，实际项目中可以使用Apache POI的PPT处理API
        return "PowerPoint document content (parsing not fully implemented)";
    }

    @Override
    public boolean supports(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".doc") || lowerUrl.endsWith(".docx") || 
               lowerUrl.endsWith(".xls") || lowerUrl.endsWith(".xlsx") || 
               lowerUrl.endsWith(".ppt") || lowerUrl.endsWith(".pptx");
    }
}