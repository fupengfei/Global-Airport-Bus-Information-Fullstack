package com.airportbus.ticket;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.airportbus.ticket.api.dto.CorrectionDtos.SubmitCorrectionRequest;
import com.airportbus.ticket.api.dto.CorrectionDtos.UpdateCorrectionRequest;
import com.airportbus.ticket.mapper.CorrectionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 匿名纠错上报:公开提交(校验+限流)+ 管理员队列处理。 */
@Service
public class CorrectionService {
    private static final Set<String> VALID_STATUS = Set.of("OPEN", "RESOLVED", "DISMISSED");

    private final CorrectionMapper mapper;
    private final BusWriteMapper busWrite;
    private final CorrectionRateLimiter rateLimiter;

    public CorrectionService(CorrectionMapper mapper, BusWriteMapper busWrite, CorrectionRateLimiter rateLimiter) {
        this.mapper = mapper; this.busWrite = busWrite; this.rateLimiter = rateLimiter;
    }

    @Transactional
    public CorrectionReport submit(SubmitCorrectionRequest req, String ip) {
        if (req.description() == null || req.description().isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "description required");
        String src = (req.sourceId() == null || req.sourceId().isBlank()) ? null : req.sourceId();
        if (src != null && busWrite.selectVersionHash(src) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, src);
        if (!rateLimiter.allow(ip))
            throw new ApiException(ErrorCode.RATE_LIMITED, "too many reports, try later");
        Map<String, Object> row = new HashMap<>();
        row.put("relatedSourceId", src);
        row.put("description", req.description().trim());
        row.put("contact", (req.contact() == null || req.contact().isBlank()) ? null : req.contact().trim());
        row.put("reporterIp", ip);
        row.put("createdBy", "anonymous");
        mapper.insert(row);
        return mapper.selectById(((Number) row.get("id")).longValue());
    }

    public List<CorrectionReport> listForAdmin(String status, int limit, int offset) {
        int lim = limit < 1 ? 20 : Math.min(limit, 100);
        int off = Math.max(offset, 0);
        return mapper.selectPage(status, lim, off);
    }

    /** @Audited 不放这里:它会触发 CurrentUser.require(),而 IT 直调本方法无主体会抛错。审计放在控制器方法上(见 Task 5)。 */
    @Transactional
    public CorrectionReport updateStatus(long id, UpdateCorrectionRequest req, String adminUser) {
        if (req.status() == null || !VALID_STATUS.contains(req.status()))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "bad status");
        int n = mapper.updateStatus(id, req.status(), req.resolutionNote(), adminUser);
        if (n == 0) throw new ApiException(ErrorCode.CORRECTION_NOT_FOUND, String.valueOf(id));
        return mapper.selectById(id);
    }
}
