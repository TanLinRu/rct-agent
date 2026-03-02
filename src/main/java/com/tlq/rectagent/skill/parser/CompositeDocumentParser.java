package com.tlq.rectagent.skill.parser;

import java.util.List;

public class CompositeDocumentParser implements DocumentParser {
    private final List<DocumentParser> parsers;

    public CompositeDocumentParser(DocumentParser... parsers) {
        this.parsers = List.of(parsers);
    }

    @Override
    public String parse(byte[] content, String url) throws Exception {
        for (DocumentParser parser : parsers) {
            if (parser.supports(url)) {
                return parser.parse(content, url);
            }
        }
        throw new UnsupportedOperationException("No parser supports URL: " + url);
    }

    @Override
    public boolean supports(String url) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(url)) {
                return true;
            }
        }
        return false;
    }
}