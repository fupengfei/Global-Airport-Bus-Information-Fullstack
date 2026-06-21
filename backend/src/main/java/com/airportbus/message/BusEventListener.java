package com.airportbus.message;

import com.airportbus.bus.service.BusDeletedEvent;
import com.airportbus.bus.service.BusUpdatedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/** 推送闭环:bus 提交后异步扇出站内信。E3:失败由 MessageReconciler 兜底。 */
@Component
public class BusEventListener {
    private final MessageService messages;
    public BusEventListener(MessageService messages) { this.messages = messages; }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBusUpdated(BusUpdatedEvent e) { messages.fanOutUpdated(e); }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBusDeleted(BusDeletedEvent e) { messages.fanOutOffline(e); }
}
