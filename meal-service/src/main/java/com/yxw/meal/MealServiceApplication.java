package com.yxw.meal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yxw.meal.mapper")
public class MealServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MealServiceApplication.class, args);
    }
}
