package com.airportbus.message;

import com.airportbus.bus.service.BusDeletedEvent;
import com.airportbus.bus.service.BusUpdatedEvent;
import com.airportbus.message.mapper.MessageMapper;
import com.airportbus.user.service.FavoriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 站内信扇出 + 读写。订阅者/收藏软删走 FavoriteService(E5)。 */
@Service
public class MessageService {

    private static final int BATCH = 500; // E12 分批

    private final MessageMapper mapper;
    private final MessageUnreadCounter counter;
    private final FavoriteService favorites;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public MessageService(MessageMapper mapper, MessageUnreadCounter counter, FavoriteService favorites) {
        this.mapper = mapper; this.counter = counter; this.favorites = favorites;
    }

    @Transactional
    public void fanOutUpdated(BusUpdatedEvent e) {
        List<Long> userIds = favorites.activeSubscriberUserIds(e.busRouteId());
        if (userIds.isEmpty()) return;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("route", e.route());
        params.put("sourceId", e.sourceId());
        params.put("changed", e.summary() == null ? List.of() : e.summary().scalars());
        params.put("changedSubtables", e.summary() == null ? List.of() : e.summary().changedSubtables());
        String paramsJson = writeJson(params);
        String dedup = "bus:" + e.busRouteId() + ":v:" + e.version();
        insertFanout(userIds, "BUS_UPDATED", paramsJson, e.busRouteId(), dedup);
    }

    @Transactional
    public void fanOutOffline(BusDeletedEvent e) {
        List<Long> userIds = favorites.activeSubscriberUserIds(e.busRouteId());
        if (!userIds.isEmpty()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("route", e.route());
            params.put("sourceId", e.sourceId());
            insertFanout(userIds, "BUS_OFFLINE", writeJson(params), e.busRouteId(), "bus:" + e.busRouteId() + ":offline");
        }
        favorites.softDeleteByBusRouteId(e.busRouteId(), "system");
    }

    /** 对账回填:对单个 (user, bus, version) 补一条 BUS_UPDATED(幂等)。 */
    @Transactional
    public void backfill(MessageMapper.Backfill b) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("route", b.route()); params.put("sourceId", b.sourceId());
        params.put("changed", List.of()); params.put("changedSubtables", List.of());
        insertFanout(List.of(b.userId()), "BUS_UPDATED", writeJson(params), b.busRouteId(),
                "bus:" + b.busRouteId() + ":v:" + b.version());
    }

    private void insertFanout(List<Long> userIds, String code, String paramsJson, long busRouteId, String dedup) {
        for (int i = 0; i < userIds.size(); i += BATCH) {
            List<Long> chunk = userIds.subList(i, Math.min(i + BATCH, userIds.size()));
            List<Map<String, Object>> rows = new ArrayList<>(chunk.size());
            for (Long uid : chunk) {
                Map<String, Object> row = new HashMap<>();
                row.put("userId", uid); row.put("templateCode", code); row.put("paramsJson", paramsJson);
                row.put("relatedBusRouteId", busRouteId); row.put("dedupKey", dedup); row.put("actor", "system");
                rows.add(row);
            }
            mapper.batchInsert(rows);
        }
        for (Long uid : userIds) counter.invalidate(uid);
    }

    public long unreadCount(long userId) { return counter.unread(userId); }

    public List<Message> list(long userId, int limit, int offset) {
        return mapper.selectPage(userId, limit < 1 ? 20 : Math.min(limit, 100), Math.max(offset, 0));
    }

    @Transactional
    public int markRead(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int n = mapper.markRead(userId, ids);
        counter.invalidate(userId);
        return n;
    }

    @Transactional
    public void delete(long userId, long id) { mapper.softDelete(userId, id); counter.invalidate(userId); }

    private String writeJson(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
