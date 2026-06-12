package com.airportbus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@MapperScan("com.airportbus.bus.mapper")
public class AirportbusApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirportbusApplication.class, args);
    }
}
