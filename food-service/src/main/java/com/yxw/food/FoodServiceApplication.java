package com.yxw.food;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yxw.food.mapper")
public class FoodServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodServiceApplication.class, args);
    }
}
