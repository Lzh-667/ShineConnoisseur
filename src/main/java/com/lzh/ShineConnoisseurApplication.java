package com.lzh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lzh.mapper")
@SpringBootApplication
public class ShineConnoisseurApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShineConnoisseurApplication.class, args);
    }

}
