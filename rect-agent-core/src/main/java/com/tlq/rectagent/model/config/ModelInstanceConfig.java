package com.tlq.rectagent.model.config;

import lombok.Data;
import java.util.Set;

@Data
public class ModelInstanceConfig {
    private String name;
    private String provider;
    private String model;
    private double costPerToken;
    private int priority;
    private Set<String> capabilities;
}
