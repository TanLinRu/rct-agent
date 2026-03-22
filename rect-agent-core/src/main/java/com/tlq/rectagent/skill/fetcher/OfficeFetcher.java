package com.tlq.rectagent.skill.fetcher;

import org.springframework.web.reactive.function.client.WebClient;

public class OfficeFetcher implements DocumentFetcher {
    private final WebClient webClient;

    public OfficeFetcher() {
        this.webClient = WebClient.builder().build();
    }

    @Override
    public byte[] fetch(String url) throws Exception {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    @Override
    public boolean supports(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".doc") || lowerUrl.endsWith(".docx") || 
               lowerUrl.endsWith(".xls") || lowerUrl.endsWith(".xlsx") || 
               lowerUrl.endsWith(".ppt") || lowerUrl.endsWith(".pptx");
    }
}