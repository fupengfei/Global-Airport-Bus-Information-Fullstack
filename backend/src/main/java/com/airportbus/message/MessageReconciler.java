package com.airportbus.message;

import com.airportbus.message.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** E3 投递对账:周期回填「有活跃订阅者但缺当前 version 消息」的漏发。幂等(走 fanout 的 ON DUPLICATE 去重)。 */
@Component
public class MessageReconciler {
    private static final Logger log = LoggerFactory.getLogger(MessageReconciler.class);
    private final MessageMapper mapper;
    private final MessageService messages;

    public MessageReconciler(MessageMapper mapper, MessageService messages) {
        this.mapper = mapper; this.messages = messages;
    }

    @Scheduled(fixedDelayString = "${airportbus.message.reconcile-delay-ms:300000}")
    public void reconcile() {
        try {
            var missing = mapper.selectMissingForCurrentVersion();
            if (missing.isEmpty()) return;
            for (MessageMapper.Backfill b : missing) messages.backfill(b);
            log.info("message reconcile backfilled {} rows", missing.size());
        } catch (Exception e) {
            log.warn("message reconcile failed: {}", e.toString());
        }
    }
}
