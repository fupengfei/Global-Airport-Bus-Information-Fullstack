package com.airportbus.ticket.api;

import com.airportbus.audit.Audited;
import com.airportbus.ticket.Ticket;
import com.airportbus.ticket.TicketService;
import com.airportbus.ticket.TicketThread;
import com.airportbus.ticket.api.dto.TicketDtos.ReplyRequest;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "admin-ticket", description = "工单队列(管理员)")
@RestController
@RequestMapping("/api/v1/admin/tickets")
public class AdminTicketController {
    private final TicketService service;
    public AdminTicketController(TicketService service) { this.service = service; }

    @GetMapping
    public List<Ticket> list(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "20") int limit,
                             @RequestParam(defaultValue = "0") int offset) {
        CurrentUser.requireAdmin();
        return service.listForAdmin(status, limit, offset);
    }

    @GetMapping("/{id}")
    public TicketThread one(@PathVariable long id) {
        CurrentUser.requireAdmin();
        return service.getForAdmin(id);
    }

    @Audited(action = "REPLY_TICKET", target = "ticket")
    @PostMapping("/{id}/replies")
    public TicketThread reply(@PathVariable long id, @RequestBody ReplyRequest req) {
        JwtPrincipal me = CurrentUser.requireAdmin();
        return service.replyAsAdmin(me.userId(), id, req.body());
    }

    @Audited(action = "CLOSE_TICKET", target = "ticket")
    @PostMapping("/{id}/close")
    public Ticket close(@PathVariable long id) {
        CurrentUser.requireAdmin();
        return service.closeAsAdmin(id);
    }
}
