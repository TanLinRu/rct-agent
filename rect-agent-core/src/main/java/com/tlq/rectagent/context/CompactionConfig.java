package com.tlq.rectagent.context;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class CompactionConfig {

    @Value("${rectagent.compaction.preserve-recent:4}")
    private int preserveRecentMessages;

    @Value("${rectagent.compaction.max-tokens:10000}")
    private int maxEstimatedTokens;
}