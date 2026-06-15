package com.airportbus.bus.seed;

import com.airportbus.bus.hash.CanonicalBus;
import com.airportbus.bus.hash.Canonicalizer;
import com.airportbus.bus.mapper.BusWriteMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/** E11:只落库不发事件;E2:复用 Canonicalizer。以自然键 upsert,子表先删后插,幂等。 */
@Service
public class SeedImporter {

    private final BusWriteMapper mapper;
    private final ObjectMapper json = new ObjectMapper();

    public SeedImporter(BusWriteMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void importFromClasspath(String path) {
        SeedDtos.Root root = read(path);
        for (SeedDtos.Country c : nz(root.countries())) {
            Long countryId = upsertCountry(c);
            for (SeedDtos.City city : nz(c.cities())) {
                Long cityId = upsertCity(countryId, city);
                for (SeedDtos.Airport ap : nz(city.airports())) {
                    Long airportId = upsertAirport(cityId, ap);
                    for (SeedDtos.Bus bus : nz(ap.buses())) {
                        upsertBus(airportId, bus);
                    }
                }
            }
        }
    }

    private SeedDtos.Root read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return json.readValue(in, SeedDtos.Root.class);
        } catch (Exception e) {
            throw new IllegalStateException("read seed failed: " + path, e);
        }
    }

    private Long upsertCountry(SeedDtos.Country c) {
        Long id = mapper.findCountryId(c.code());
        if (id != null) return id;
        mapper.insertCountry(c.code(), c.name());
        return mapper.findCountryId(c.code());
    }

    private Long upsertCity(Long countryId, SeedDtos.City city) {
        Long id = mapper.findCityId(countryId, city.name());
        if (id != null) return id;
        Map<String, Object> row = new HashMap<>();
        row.put("countryId", countryId);
        row.put("name", city.name());
        mapper.insertCity(row);
        return toLong(row.get("id"));
    }

    private Long upsertAirport(Long cityId, SeedDtos.Airport ap) {
        Long id = mapper.findAirportId(ap.code());
        Map<String, Object> row = new HashMap<>();
        row.put("cityId", cityId);
        row.put("code", ap.code());
        row.put("name", ap.name());
        row.put("officialUrl", ap.officialUrl());
        if (id == null) {
            mapper.insertAirport(row);
            return toLong(row.get("id"));
        }
        row.put("id", id);
        mapper.updateAirport(row);
        return id;
    }

    private void upsertBus(Long airportId, SeedDtos.Bus bus) {
        String hash = Canonicalizer.contentHash(toCanonical(bus));
        Long id = mapper.findBusId(bus.id());
        Map<String, Object> row = new HashMap<>();
        row.put("airportId", airportId);
        row.put("sourceId", bus.id());
        row.put("route", bus.route());
        row.put("destination", bus.destination());
        row.put("operator", bus.operator());
        row.put("officialUrl", bus.officialUrl());
        row.put("duration", bus.duration());
        row.put("price", bus.price());
        row.put("operatingHours", bus.operatingHours());
        row.put("lastUpdated", parseDate(bus.lastUpdated()));
        row.put("fetchFailed", bus.fetchFailed());
        row.put("contentHash", hash);
        if (id == null) {
            mapper.insertBus(row);
            id = toLong(row.get("id"));
        } else {
            row.put("id", id);
            mapper.updateBus(row);
        }
        replaceChildren(id, bus);
    }

    private void replaceChildren(Long busId, SeedDtos.Bus bus) {
        mapper.deleteStops(busId);
        mapper.deleteSchedules(busId);
        mapper.deleteImages(busId);
        mapper.deleteFiles(busId);
        mapper.deleteAlerts(busId);

        int seq = 0;
        for (String s : nz(bus.stops())) mapper.insertStop(busId, seq++, s);
        for (SeedDtos.Schedule sc : nz(bus.schedules())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId);
            r.put("timeRange", sc.timeRange());
            r.put("intervalText", sc.interval());
            r.put("note", sc.note());
            mapper.insertSchedule(r);
        }
        for (SeedDtos.Image im : nz(bus.images())) mapper.insertImage(busId, im.url(), im.caption());
        for (SeedDtos.FileRef f : nz(bus.files())) mapper.insertFile(busId, f.name(), f.url());
        for (SeedDtos.Alert a : nz(bus.alerts())) {
            Map<String, Object> r = new HashMap<>();
            r.put("busId", busId);
            r.put("type", a.type());
            r.put("message", a.message());
            r.put("startDate", parseDate(a.startDate()));
            r.put("endDate", parseDate(a.endDate()));
            mapper.insertAlert(r);
        }
    }

    /** SeedBus -> CanonicalBus,字段对齐(interval->intervalText, caption/name->label)。 */
    static CanonicalBus toCanonical(SeedDtos.Bus bus) {
        List<CanonicalBus.Schedule> schedules = new ArrayList<>();
        for (SeedDtos.Schedule sc : nz(bus.schedules()))
            schedules.add(new CanonicalBus.Schedule(sc.timeRange(), sc.interval(), sc.note()));
        List<CanonicalBus.Alert> alerts = new ArrayList<>();
        for (SeedDtos.Alert a : nz(bus.alerts()))
            alerts.add(new CanonicalBus.Alert(a.type(), a.message(), a.startDate(), a.endDate()));
        List<CanonicalBus.Media> images = new ArrayList<>();
        for (SeedDtos.Image im : nz(bus.images()))
            images.add(new CanonicalBus.Media(im.url(), im.caption()));
        List<CanonicalBus.Media> files = new ArrayList<>();
        for (SeedDtos.FileRef f : nz(bus.files()))
            files.add(new CanonicalBus.Media(f.url(), f.name()));
        return new CanonicalBus(bus.route(), bus.destination(), bus.operator(), bus.duration(),
                bus.price(), bus.operatingHours(), nz(bus.stops()), schedules, alerts, images, files);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim());
    }

    private static <T> List<T> nz(List<T> in) {
        return in == null ? List.of() : in;
    }

    /** MyBatis useGeneratedKeys 回填到 Map 时 MySQL Connector/J 可能返回 BigInteger，统一转 Long。 */
    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
