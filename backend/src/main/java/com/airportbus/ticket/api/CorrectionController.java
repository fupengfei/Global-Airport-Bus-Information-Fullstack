package com.airportbus.ticket.api;

import com.airportbus.ticket.CorrectionReport;
import com.airportbus.ticket.CorrectionService;
import com.airportbus.ticket.api.dto.CorrectionDtos.SubmitCorrectionRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@Tag(name = "correction", description = "匿名数据纠错上报(零登录)")
@RestController
@RequestMapping("/api/v1/corrections")
public class CorrectionController {
    private final CorrectionService service;
    public CorrectionController(CorrectionService service) { this.service = service; }

    /** 公开:无需登录。JwtAuthFilter 无 token 不拦截,这里不调 CurrentUser.require()。 */
    @PostMapping
    public CorrectionReport submit(@RequestBody SubmitCorrectionRequest req, HttpServletRequest http) {
        return service.submit(req, clientIp(http));
    }

    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
