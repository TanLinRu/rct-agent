package com.tlq.rectagent.skill.fetcher;

import java.util.List;

public class CompositeDocumentFetcher implements DocumentFetcher {
    private final List<DocumentFetcher> fetchers;

    public CompositeDocumentFetcher(DocumentFetcher... fetchers) {
        this.fetchers = List.of(fetchers);
    }

    @Override
    public byte[] fetch(String url) throws Exception {
        for (DocumentFetcher fetcher : fetchers) {
            if (fetcher.supports(url)) {
                return fetcher.fetch(url);
            }
        }
        throw new UnsupportedOperationException("No fetcher supports URL: " + url);
    }

    @Override
    public boolean supports(String url) {
        for (DocumentFetcher fetcher : fetchers) {
            if (fetcher.supports(url)) {
                return true;
            }
        }
        return false;
    }
}