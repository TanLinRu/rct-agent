package com.tlq.rectagent.skill.fetcher;

import org.springframework.web.reactive.function.client.WebClient;

public class HtmlFetcher implements DocumentFetcher {
    private final WebClient webClient;

    public HtmlFetcher() {
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
        return url.toLowerCase().endsWith(".html") || url.toLowerCase().endsWith(".htm") || url.contains("http");
    }
}