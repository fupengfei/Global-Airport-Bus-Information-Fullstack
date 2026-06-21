package com.airportbus.bus.seed;

import com.airportbus.bus.mapper.BusWriteMapper;
import com.airportbus.bus.service.BusCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;

/** E11:只落库不发事件;E5:委托 BusCommandService 统一写路径。以自然键 upsert,幂等。 */
@Service
public class SeedImporter {

    private final BusWriteMapper mapper;
    private final BusCommandService busCommand;
    private final ObjectMapper json = new ObjectMapper();

    public SeedImporter(BusWriteMapper mapper, BusCommandService busCommand) {
        this.mapper = mapper;
        this.busCommand = busCommand;
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
        com.airportbus.bus.api.dto.BusInput input = new com.airportbus.bus.api.dto.BusInput(
                bus.route(), bus.destination(), bus.operator(), bus.officialUrl(),
                bus.duration(), bus.price(), bus.operatingHours(), parseDate(bus.lastUpdated()),
                nz(bus.stops()),
                bus.schedules() == null ? java.util.List.of() : bus.schedules().stream()
                        .map(s -> new com.airportbus.bus.api.dto.BusDetailDto.Schedule(s.timeRange(), s.interval(), s.note())).toList(),
                bus.alerts() == null ? java.util.List.of() : bus.alerts().stream()
                        .map(a -> new com.airportbus.bus.api.dto.BusDetailDto.Alert(a.type(), a.message(), parseDate(a.startDate()), parseDate(a.endDate()))).toList(),
                bus.images() == null ? java.util.List.of() : bus.images().stream()
                        .map(im -> new com.airportbus.bus.api.dto.BusDetailDto.Image(im.url(), im.caption())).toList(),
                bus.files() == null ? java.util.List.of() : bus.files().stream()
                        .map(f -> new com.airportbus.bus.api.dto.BusDetailDto.FileRef(f.name(), f.url())).toList());
        busCommand.save(bus.id(), airportId, input, null, "seed", true); // E11 抑制事件
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
