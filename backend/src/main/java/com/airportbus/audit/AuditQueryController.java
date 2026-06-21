package com.airportbus.audit;

import com.airportbus.user.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "admin-audit", description = "操作记录(管理员)")
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditQueryController {
    private final AuditService audit;
    public AuditQueryController(AuditService audit) { this.audit = audit; }

    @GetMapping
    public List<AuditMapper.Row> list(@RequestParam(required = false) Long actor,
                                      @RequestParam(required = false) String action,
                                      @RequestParam(defaultValue = "100") int limit) {
        CurrentUser.requireAdmin();
        return audit.list(actor, action, limit);
    }
}
