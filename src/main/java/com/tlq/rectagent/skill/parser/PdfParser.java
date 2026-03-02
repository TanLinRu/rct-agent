package com.tlq.rectagent.skill.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;

public class PdfParser implements DocumentParser {
    @Override
    public String parse(byte[] content, String url) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(content))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    @Override
    public boolean supports(String url) {
        return url.toLowerCase().endsWith(".pdf");
    }
}