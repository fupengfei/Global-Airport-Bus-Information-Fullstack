package com.airportbus.bus.service;

import com.airportbus.bus.api.dto.BusDetailDto;
import com.airportbus.bus.api.dto.BusInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusDiffTest {
    private BusInput base() {
        return new BusInput("VAB 1", "西站", "ÖBB", null, "40min", "€11", "03:00-24:00", null,
                List.of("A", "B"),
                List.of(new BusDetailDto.Schedule("all day", "30min", null)),
                List.of(), List.of(), List.of());
    }

    @Test void scalarChange_isDetected() {
        BusInput a = base();
        BusInput b = new BusInput("VAB 1", "西站", "ÖBB", null, "40min", "€13", "03:00-24:00", null,
                a.stops(), a.schedules(), a.alerts(), a.images(), a.files());
        ChangedSummary s = BusCommandService.diff(a, b);
        assertThat(s.scalars()).anySatisfy(f -> {
            assertThat(f.field()).isEqualTo("price");
            assertThat(f.oldValue()).isEqualTo("€11");
            assertThat(f.newValue()).isEqualTo("€13");
        });
        assertThat(s.changedSubtables()).isEmpty();
    }

    @Test void subtableChange_isFlagged() {
        BusInput a = base();
        BusInput b = new BusInput("VAB 1", "西站", "ÖBB", null, "40min", "€11", "03:00-24:00", null,
                List.of("A", "B", "C"), a.schedules(), a.alerts(), a.images(), a.files());
        ChangedSummary s = BusCommandService.diff(a, b);
        assertThat(s.scalars()).isEmpty();
        assertThat(s.changedSubtables()).contains("stops");
    }

    @Test void identical_isEmpty() {
        ChangedSummary s = BusCommandService.diff(base(), base());
        assertThat(s.scalars()).isEmpty();
        assertThat(s.changedSubtables()).isEmpty();
    }
}
