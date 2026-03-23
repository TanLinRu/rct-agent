package com.tlq.rectagent.model.config;

import lombok.Data;

@Data
public class TrafficShiftingRule {
    private String agent;
    private String model;
    private int percentage;
}
