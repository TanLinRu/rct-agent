package com.tlq.openclaw.tool;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
public class FileTool extends AbstractTool {
    public FileTool(String id, String name, String description) {
        super(id, name, description);
    }
    
    @Override
    protected Object doExecute(Map<String, Object> parameters) {
        String action = (String) parameters.get("action");
        String path = (String) parameters.get("path");
        
        switch (action) {
            case "read":
                return readFile(path);
            case "write":
                String content = (String) parameters.get("content");
                return writeFile(path, content);
            case "list":
                return listFiles(path);
            default:
                return "Unknown action: " + action;
        }
    }
    
    private Object readFile(String path) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "File not found: " + path;
            }
            return Files.readString(filePath);
        } catch (IOException e) {
            log.error("Error reading file: {}", path, e);
            return "Error reading file: " + e.getMessage();
        }
    }
    
    private Object writeFile(String path, String content) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            return "File written successfully: " + path;
        } catch (IOException e) {
            log.error("Error writing file: {}", path, e);
            return "Error writing file: " + e.getMessage();
        }
    }
    
    private Object listFiles(String path) {
        try {
            Path dirPath = Paths.get(path);
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return "Directory not found: " + path;
            }
            StringBuilder result = new StringBuilder();
            Files.list(dirPath).forEach(file -> result.append(file.getFileName()).append("\n"));
            return result.toString();
        } catch (IOException e) {
            log.error("Error listing files: {}", path, e);
            return "Error listing files: " + e.getMessage();
        }
    }
}