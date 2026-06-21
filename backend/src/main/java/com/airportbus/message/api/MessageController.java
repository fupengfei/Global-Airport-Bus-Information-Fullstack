package com.airportbus.message.api;

import com.airportbus.message.Message;
import com.airportbus.message.MessageService;
import com.airportbus.message.api.dto.MarkReadRequest;
import com.airportbus.message.api.dto.UnreadCountDto;
import com.airportbus.user.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "message", description = "站内信(需登录)")
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    private final MessageService service;
    public MessageController(MessageService service) { this.service = service; }

    @GetMapping("/unread-count")
    public UnreadCountDto unread() {
        return new UnreadCountDto(service.unreadCount(CurrentUser.require().userId()));
    }

    @GetMapping
    public List<Message> list(@RequestParam(defaultValue = "20") int limit,
                              @RequestParam(defaultValue = "0") int offset) {
        return service.list(CurrentUser.require().userId(), limit, offset);
    }

    @PostMapping("/read")
    public java.util.Map<String, Integer> markRead(@RequestBody MarkReadRequest req) {
        int n = service.markRead(CurrentUser.require().userId(), req.ids());
        return java.util.Map.of("updated", n);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id) {
        service.delete(CurrentUser.require().userId(), id);
    }
}
