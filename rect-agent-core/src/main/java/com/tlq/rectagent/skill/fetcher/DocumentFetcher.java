package com.tlq.rectagent.skill.fetcher;

public interface DocumentFetcher {
    byte[] fetch(String url) throws Exception;
    boolean supports(String url);
}