package com.tlq.rectagent.skill.parser;

public interface DocumentParser {
    String parse(byte[] content, String url) throws Exception;
    boolean supports(String url);
}