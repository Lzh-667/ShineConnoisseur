package com.lzh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.lzh.mapper")
@EnableScheduling
@SpringBootApplication
public class ShineConnoisseurApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShineConnoisseurApplication.class, args);
    }

}
