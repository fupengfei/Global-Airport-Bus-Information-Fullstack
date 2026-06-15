package com.airportbus.bus.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusSummaryDto;
import com.airportbus.bus.api.dto.TreeDto;
import com.airportbus.bus.service.BusQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "bus-query", description = "公开巴士查询(零登录)")
@RestController
@RequestMapping("/api/v1")
public class BusQueryController {

    private final BusQueryService service;

    public BusQueryController(BusQueryService service) {
        this.service = service;
    }

    @Operation(summary = "国家/城市/机场 导航树")
    @GetMapping("/tree")
    public TreeDto tree() {
        return service.tree();
    }

    @Operation(summary = "某机场下的巴士线路列表(空机场返回空数组)")
    @GetMapping("/airports/{code}/buses")
    public List<BusSummaryDto> busesByAirport(@PathVariable String code) {
        return service.busesByAirport(code);
    }

    @Operation(summary = "线路详情(按 source_id)")
    @GetMapping("/buses/{sourceId}")
    public BusDetailDto detail(@PathVariable String sourceId) {
        return service.detail(sourceId);
    }
}
