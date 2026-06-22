package com.airportbus.ticket.api;

import com.airportbus.ticket.Ticket;
import com.airportbus.ticket.TicketService;
import com.airportbus.ticket.TicketThread;
import com.airportbus.ticket.api.dto.TicketDtos.CreateTicketRequest;
import com.airportbus.ticket.api.dto.TicketDtos.ReplyRequest;
import com.airportbus.user.security.CurrentUser;
import com.airportbus.user.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ticket", description = "用户建议工单")
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService service;
    public TicketController(TicketService service) { this.service = service; }

    @PostMapping
    public TicketThread create(@RequestBody CreateTicketRequest req) {
        JwtPrincipal me = CurrentUser.require();
        return service.create(me.userId(), req.sourceId(), req.body());
    }

    @GetMapping
    public List<Ticket> mine(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "20") int limit,
                             @RequestParam(defaultValue = "0") int offset) {
        JwtPrincipal me = CurrentUser.require();
        return service.listMine(me.userId(), status, limit, offset);
    }

    @GetMapping("/{id}")
    public TicketThread one(@PathVariable long id) {
        JwtPrincipal me = CurrentUser.require();
        return service.getMine(me.userId(), id);
    }

    @PostMapping("/{id}/replies")
    public TicketThread reply(@PathVariable long id, @RequestBody ReplyRequest req) {
        JwtPrincipal me = CurrentUser.require();
        return service.replyAsUser(me.userId(), id, req.body());
    }

    @PostMapping("/{id}/close")
    public Ticket close(@PathVariable long id) {
        JwtPrincipal me = CurrentUser.require();
        return service.closeAsUser(me.userId(), id);
    }
}
