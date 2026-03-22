package com.tlq.rectagent.skill.validator;

public class DefaultDocumentValidator implements DocumentValidator {
    @Override
    public boolean validate(String content, String url) {
        // 检查文档内容是否为空
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // 检查文档长度是否合理
        if (content.length() > 1000000) { // 1MB
            return false;
        }
        
        // 检查URL是否有效
        if (url == null || !url.startsWith("http")) {
            return false;
        }
        
        // 可以添加更多验证逻辑，如内容相关性检查等
        return true;
    }
}