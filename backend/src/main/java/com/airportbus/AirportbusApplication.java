package com.airportbus;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync       // 机场搜索热度:计数走 @Async,不阻塞查询路径
@EnableScheduling  // 机场搜索热度:@Scheduled 周期把 Redis 计数刷入 airport_search_stat
// audit 包扫 annotationClass=Mapper.class,避免把 @interface Audited 误当 mapper 注册
@MapperScans({
    @MapperScan({"com.airportbus.bus.mapper", "com.airportbus.user.mapper", "com.airportbus.message.mapper", "com.airportbus.ticket.mapper"}),
    @MapperScan(value = "com.airportbus.audit", annotationClass = Mapper.class)
})
public class AirportbusApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirportbusApplication.class, args);
    }
}
