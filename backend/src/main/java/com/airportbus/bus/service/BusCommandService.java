package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import com.airportbus.bus.hash.CanonicalBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** bus 单一写入口(E5)。本任务先放纯逻辑辅助;实例方法在 Task 6 补。 */
public class BusCommandService {

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
