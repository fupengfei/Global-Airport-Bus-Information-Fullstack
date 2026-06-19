package com.airportbus.user.api;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.user.api.dto.FavoriteStatusDto;
import com.airportbus.user.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "favorite", description = "收藏 = 订阅(需登录)")
@RestController
@RequestMapping("/api/v1")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @Operation(summary = "收藏一条线路(幂等)")
    @PutMapping("/buses/{sourceId}/favorite")
    public FavoriteStatusDto favorite(@PathVariable String sourceId) {
        return service.favorite(sourceId);
    }

    @Operation(summary = "取消收藏(幂等)")
    @DeleteMapping("/buses/{sourceId}/favorite")
    public FavoriteStatusDto unfavorite(@PathVariable String sourceId) {
        return service.unfavorite(sourceId);
    }

    @Operation(summary = "我的收藏(完整卡片,按收藏时间倒序)")
    @GetMapping("/favorites")
    public List<BusDetailDto> myFavorites() {
        return service.myFavorites();
    }

    @Operation(summary = "我收藏的 source_id 列表(供前端打标记)")
    @GetMapping("/favorites/ids")
    public List<String> myIds() {
        return service.myIds();
    }
}
