package com.airportbus.admin.api;

import com.airportbus.admin.api.dto.OverviewDto;
import com.airportbus.admin.api.dto.SubscriptionStatsDto;
import com.airportbus.admin.service.AdminStatsService;
import com.airportbus.bus.mapper.SearchHotnessMapper;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.service.UserStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "admin-stats", description = "管理后台统计(仅管理员)")
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService service;

    public AdminStatsController(AdminStatsService service) { this.service = service; }

    @Operation(summary = "概览:用户/收藏总数 + 本周新增")
    @GetMapping("/overview")
    public OverviewDto overview() {
        CurrentUser.requireAdmin();
        return service.overview();
    }

    @Operation(summary = "注册趋势:近 days 天每天注册数(空天补 0)")
    @GetMapping("/registrations")
    public List<UserStatsService.DailyRegistration> registrations(
            @RequestParam(defaultValue = "7") int days) {
        CurrentUser.requireAdmin();
        return service.registrations(days);
    }

    @Operation(summary = "订阅统计:按线路/机场/城市聚合收藏数")
    @GetMapping("/subscriptions")
    public SubscriptionStatsDto subscriptions() {
        CurrentUser.requireAdmin();
        return service.subscriptions();
    }

    @Operation(summary = "机场搜索热度榜单(window=7d/30d/all)")
    @GetMapping("/hotness")
    public List<SearchHotnessMapper.HotnessRow> hotness(
            @RequestParam(defaultValue = "7d") String window) {
        CurrentUser.requireAdmin();
        return service.hotnessRanking(window);
    }
}
