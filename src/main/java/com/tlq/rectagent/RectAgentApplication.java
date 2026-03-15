package com.tlq.rectagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.tlq.rectagent.data.mapper")
@EnableAsync
public class RectAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RectAgentApplication.class, args);
    }

}
