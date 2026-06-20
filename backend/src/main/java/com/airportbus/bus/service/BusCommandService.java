package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.api.dto.BusView;
import com.airportbus.bus.hash.CanonicalBus;
import com.airportbus.bus.mapper.BusVersionMapper;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.common.ApiException;
import com.airportbus.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** bus 单一写入口(E5)。含静态辅助 toCanonical/diff(Task 5)+ 实例写方法(Task 6)。 */
@Service
public class BusCommandService {

    // ─── 实例字段 ────────────────────────────────────────────────────────────
    private final BusWriteMapper writeMapper;
    private final BusVersionMapper versionMapper;
    private final BusQueryService busQuery;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public BusCommandService(BusWriteMapper writeMapper, BusVersionMapper versionMapper,
                             BusQueryService busQuery, ApplicationEventPublisher events) {
        this.writeMapper = writeMapper;
        this.versionMapper = versionMapper;
        this.busQuery = busQuery;
        this.events = events;
    }

    // ─── 实例写方法 ──────────────────────────────────────────────────────────

    @Transactional
    public BusView save(String sourceId, long airportId, BusInput input,
                        Integer expectedVersion, String actor, boolean suppressEvents) {
        if (input.route() == null || input.route().isBlank())
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "route required");

        String newHash = com.airportbus.bus.hash.Canonicalizer.contentHash(toCanonical(input));
        BusWriteMapper.VersionHash cur = writeMapper.selectVersionHash(sourceId);

        if (cur == null) {
            // ── CREATE ──
            Map<String, Object> row = busRow(airportId, sourceId, input, newHash);
            writeMapper.insertBus(row);
            long busRouteId = toLong(row.get("id"));
            replaceChildren(busRouteId, input);
            int newVersion = 1;
            Map<String, Object> up = busRow(airportId, sourceId, input, newHash);
            up.put("id", busRouteId);
            up.put("newVersion", newVersion);
            up.put("actor", actor);
            writeMapper.updateBusFull(up);
            ChangedSummary summary = diff(emptyInput(), input);
            writeSnapshot(busRouteId, newVersion, input, newHash, summary, actor);
            if (!suppressEvents)
                events.publishEvent(new BusUpdatedEvent(busRouteId, sourceId, null, newHash, summary));
            return view(sourceId, airportId, newVersion, input);
        }

        // ── UPDATE ──
        if (expectedVersion != null && expectedVersion != cur.version())
            throw new ApiException(ErrorCode.BUS_VERSION_CONFLICT, "version conflict");
        long busRouteId = writeMapper.findBusId(sourceId);
        String oldHash = cur.contentHash();

        if (newHash.equals(oldHash)) {
            // no-op: unchanged content
            return view(sourceId, airportId, cur.version(), input);
        }

        BusInput oldInput = toInput(busQuery.detail(sourceId));
        int newVersion = cur.version() + 1;
        Map<String, Object> up = busRow(airportId, sourceId, input, newHash);
        up.put("id", busRouteId);
        up.put("newVersion", newVersion);
        up.put("actor", actor);
        writeMapper.updateBusFull(up);
        replaceChildren(busRouteId, input);
        ChangedSummary summary = diff(oldInput, input);
        writeSnapshot(busRouteId, newVersion, input, newHash, summary, actor);
        if (!suppressEvents)
            events.publishEvent(new BusUpdatedEvent(busRouteId, sourceId, oldHash, newHash, summary));
        return view(sourceId, airportId, newVersion, input);
    }

    @Transactional
    public void verify(String sourceId, String actor) {
        if (writeMapper.selectVersionHash(sourceId) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        writeMapper.updateVerify(sourceId, LocalDateTime.now(), actor);
    }

    @Transactional
    public void delete(String sourceId, String actor) {
        if (writeMapper.selectVersionHash(sourceId) == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId);
        writeMapper.softDeleteBus(sourceId, actor);
    }

    @Transactional
    public BusView rollback(String sourceId, int targetVersion, String actor) {
        long busRouteId = writeMapper.findBusId(sourceId);
        String snap = versionMapper.selectSnapshotJson(busRouteId, targetVersion);
        if (snap == null)
            throw new ApiException(ErrorCode.BUS_NOT_FOUND, sourceId + "@v" + targetVersion);
        BusInput restored = readSnapshot(snap);
        int airportId = writeMapper.selectAirportIdBySource(sourceId);
        int cur = writeMapper.selectVersionHash(sourceId).version();
        return save(sourceId, airportId, restored, cur, actor, false);
    }

    /** 给 admin GET 用:detail + version + 核对时间 组 BusView。 */
    public BusView viewFor(String sourceId) {
        var d = busQuery.detail(sourceId);
        var vh = writeMapper.selectVersionHash(sourceId);
        var meta = writeMapper.selectAdminMeta(sourceId);
        return new BusView(sourceId, meta.airportCode(), vh.version(), meta.lastVerifiedAt(), toInput(d));
    }

    // ─── 私有辅助 ────────────────────────────────────────────────────────────

    private Map<String, Object> busRow(long airportId, String sourceId, BusInput in, String hash) {
        Map<String, Object> row = new HashMap<>();
        row.put("airportId", airportId);
        row.put("sourceId", sourceId);
        row.put("route", in.route());
        row.put("destination", in.destination());
        row.put("operator", in.operator());
        row.put("officialUrl", in.officialUrl());
        row.put("duration", in.duration());
        row.put("price", in.price());
        row.put("operatingHours", in.operatingHours());
        row.put("lastUpdated", in.lastUpdated());
        row.put("fetchFailed", false);
        row.put("contentHash", hash);
        return row;
    }

    private void replaceChildren(long busId, BusInput in) {
        writeMapper.deleteStops(busId);
        writeMapper.deleteSchedules(busId);
        writeMapper.deleteImages(busId);
        writeMapper.deleteFiles(busId);
        writeMapper.deleteAlerts(busId);
        int seq = 0;
        for (String s : nz2(in.stops())) writeMapper.insertStop(busId, seq++, s);
        for (var sc : nz2(in.schedules())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId);
            r.put("timeRange", sc.timeRange());
            r.put("intervalText", sc.intervalText());
            r.put("note", sc.note());
            writeMapper.insertSchedule(r);
        }
        for (var im : nz2(in.images())) writeMapper.insertImage(busId, im.url(), im.caption());
        for (var f : nz2(in.files())) writeMapper.insertFile(busId, f.name(), f.url());
        for (var a : nz2(in.alerts())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId);
            r.put("type", a.type());
            r.put("message", a.message());
            r.put("startDate", a.startDate());
            r.put("endDate", a.endDate());
            writeMapper.insertAlert(r);
        }
    }

    private void writeSnapshot(long busRouteId, int version, BusInput in, String hash,
                               ChangedSummary summary, String actor) {
        Map<String, Object> row = new HashMap<>();
        row.put("busRouteId", busRouteId);
        row.put("version", version);
        row.put("snapshotJson", writeJson(in));
        row.put("contentHash", hash);
        row.put("changedSummary", writeJson(summary));
        row.put("actor", actor);
        versionMapper.insertSnapshot(row);
    }

    private BusView view(String sourceId, long airportId, int version, BusInput in) {
        return new BusView(sourceId, writeMapper.selectAirportCodeById(airportId), version, null, in);
    }

    private BusInput toInput(BusDetailDto d) {
        return new BusInput(d.route(), d.destination(), d.operator(), d.officialUrl(),
                d.duration(), d.price(), d.operatingHours(), d.lastUpdated(),
                d.stops(), d.schedules(), d.alerts(), d.images(), d.files());
    }

    private static BusInput emptyInput() {
        return new BusInput(null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private String writeJson(Object o) {
        try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private BusInput readSnapshot(String s) {
        try { return json.readValue(s, BusInput.class); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static <T> List<T> nz2(List<T> in) { return in == null ? List.of() : in; }

    private static Long toLong(Object v) { return v == null ? null : ((Number) v).longValue(); }

    // ─── 静态辅助(Task 5,保留) ───────────────────────────────────────────

    /** BusInput → CanonicalBus(与 SeedImporter.toCanonical 对齐:files 的 label 取 name)。 */
    static CanonicalBus toCanonical(BusInput in) {
        List<CanonicalBus.Schedule> sch = new ArrayList<>();
        for (BusDetailDto.Schedule s : nz(in.schedules()))
            sch.add(new CanonicalBus.Schedule(s.timeRange(), s.intervalText(), s.note()));
        List<CanonicalBus.Alert> al = new ArrayList<>();
        for (BusDetailDto.Alert a : nz(in.alerts()))
            al.add(new CanonicalBus.Alert(a.type(), a.message(),
                    a.startDate() == null ? null : a.startDate().toString(),
                    a.endDate() == null ? null : a.endDate().toString()));
        List<CanonicalBus.Media> img = new ArrayList<>();
        for (BusDetailDto.Image m : nz(in.images())) img.add(new CanonicalBus.Media(m.url(), m.caption()));
        List<CanonicalBus.Media> fl = new ArrayList<>();
        for (BusDetailDto.FileRef f : nz(in.files())) fl.add(new CanonicalBus.Media(f.url(), f.name()));
        return new CanonicalBus(in.route(), in.destination(), in.operator(), in.duration(),
                in.price(), in.operatingHours(), nz(in.stops()), sch, al, img, fl);
    }

    /** 字段级 diff:标量 old→new + 变更子表名。 */
    static ChangedSummary diff(BusInput oldI, BusInput newI) {
        List<ChangedSummary.FieldChange> scalars = new ArrayList<>();
        addIfChanged(scalars, "route", oldI.route(), newI.route());
        addIfChanged(scalars, "destination", oldI.destination(), newI.destination());
        addIfChanged(scalars, "operator", oldI.operator(), newI.operator());
        addIfChanged(scalars, "officialUrl", oldI.officialUrl(), newI.officialUrl());
        addIfChanged(scalars, "duration", oldI.duration(), newI.duration());
        addIfChanged(scalars, "price", oldI.price(), newI.price());
        addIfChanged(scalars, "operatingHours", oldI.operatingHours(), newI.operatingHours());
        addIfChanged(scalars, "lastUpdated",
                oldI.lastUpdated() == null ? null : oldI.lastUpdated().toString(),
                newI.lastUpdated() == null ? null : newI.lastUpdated().toString());

        List<String> subs = new ArrayList<>();
        if (!Objects.equals(nz(oldI.stops()), nz(newI.stops()))) subs.add("stops");
        if (!Objects.equals(nz(oldI.schedules()), nz(newI.schedules()))) subs.add("schedules");
        if (!Objects.equals(nz(oldI.alerts()), nz(newI.alerts()))) subs.add("alerts");
        if (!Objects.equals(nz(oldI.images()), nz(newI.images()))) subs.add("images");
        if (!Objects.equals(nz(oldI.files()), nz(newI.files()))) subs.add("files");
        return new ChangedSummary(scalars, subs);
    }

    private static void addIfChanged(List<ChangedSummary.FieldChange> out, String f, String o, String n) {
        if (!Objects.equals(o, n)) out.add(new ChangedSummary.FieldChange(f, o, n));
    }

    private static <T> List<T> nz(List<T> in) { return in == null ? List.of() : in; }
}
