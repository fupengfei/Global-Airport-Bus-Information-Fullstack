package com.airportbus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync       // 机场搜索热度:计数走 @Async,不阻塞查询路径
@EnableScheduling  // 机场搜索热度:@Scheduled 周期把 Redis 计数刷入 airport_search_stat
@MapperScan({"com.airportbus.bus.mapper", "com.airportbus.user.mapper"})
public class AirportbusApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirportbusApplication.class, args);
    }
}
