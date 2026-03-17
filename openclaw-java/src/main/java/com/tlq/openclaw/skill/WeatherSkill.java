package com.tlq.openclaw.skill;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class WeatherSkill extends AbstractSkill {
    public WeatherSkill(String id, String name, String description) {
        super(id, name, description);
    }
    
    @Override
    protected Object doExecute(Map<String, Object> parameters) {
        String city = (String) parameters.get("city");
        if (city == null) {
            return "Please provide a city name";
        }
        
        // 这里将实现天气查询逻辑
        // 目前返回模拟数据
        return "Weather in " + city + ": Sunny, 25°C";
    }
}