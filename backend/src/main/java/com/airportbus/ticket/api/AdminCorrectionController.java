package com.airportbus.ticket.api;

import com.airportbus.audit.Audited;
import com.airportbus.ticket.CorrectionReport;
import com.airportbus.ticket.CorrectionService;
import com.airportbus.ticket.api.dto.CorrectionDtos.UpdateCorrectionRequest;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "admin-correction", description = "纠错队列(管理员)")
@RestController
@RequestMapping("/api/v1/admin/corrections")
public class AdminCorrectionController {
    private final CorrectionService service;
    public AdminCorrectionController(CorrectionService service) { this.service = service; }

    @GetMapping
    public List<CorrectionReport> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "20") int limit,
                                       @RequestParam(defaultValue = "0") int offset) {
        CurrentUser.requireAdmin();
        return service.listForAdmin(status, limit, offset);
    }

    /** @Audited 放控制器方法(与 AdminBusController 一致;aspect 读 CurrentUser 取 actor,E10)。 */
    @Audited(action = "UPDATE_CORRECTION", target = "correction")
    @PatchMapping("/{id}")
    public CorrectionReport update(@PathVariable long id, @RequestBody UpdateCorrectionRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return service.updateStatus(id, req, actor(me));
    }

    /** 与 AdminBusController.actor 同款:JwtPrincipal 只有 userId()/role(),无 username()。 */
    private static String actor(JwtPrincipal me) { return "admin:" + me.userId(); }
}
