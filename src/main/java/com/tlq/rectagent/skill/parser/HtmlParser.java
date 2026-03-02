package com.tlq.rectagent.skill.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class HtmlParser implements DocumentParser {
    @Override
    public String parse(byte[] content, String url) throws Exception {
        Document doc = Jsoup.parse(new String(content));
        // 提取文本内容，去除脚本和样式
        doc.select("script, style").remove();
        return doc.text();
    }

    @Override
    public boolean supports(String url) {
        return url.toLowerCase().endsWith(".html") || url.toLowerCase().endsWith(".htm") || url.contains("http");
    }
}