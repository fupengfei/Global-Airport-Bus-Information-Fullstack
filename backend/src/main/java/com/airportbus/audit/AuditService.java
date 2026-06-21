package com.airportbus.audit;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {
    private final AuditMapper mapper;

    public AuditService(AuditMapper mapper) {
        this.mapper = mapper;
    }

    public void record(long actorId, String action, String targetType, String targetId, String summary, String ip) {
        java.util.Map<String, Object> row = new java.util.HashMap<>();
        row.put("actorId", actorId);
        row.put("actorType", "ADMIN");
        row.put("action", action);
        row.put("targetType", targetType);
        row.put("targetId", targetId);
        row.put("summary", summary);
        row.put("ip", ip);
        mapper.insert(row);
    }

    public List<AuditMapper.Row> list(Long actorId, String action, int limit) {
        return mapper.list(actorId, action, limit < 1 ? 100 : Math.min(limit, 500));
    }
}
