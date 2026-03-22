package com.tlq.rectagent.skill.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfParser implements DocumentParser {
    @Override
    public String parse(byte[] content, String url) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    @Override
    public boolean supports(String url) {
        return url.toLowerCase().endsWith(".pdf");
    }
}