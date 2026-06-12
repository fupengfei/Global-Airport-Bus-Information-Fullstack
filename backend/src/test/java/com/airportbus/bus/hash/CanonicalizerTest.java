package com.airportbus.bus.hash;

import com.airportbus.bus.hash.CanonicalBus.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalizerTest {

    private CanonicalBus base() {
        return new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                List.of("A", "B"),
                List.of(new Schedule("all day", "30min", "note")),
                List.of(new Alert("info", "msg", "2026-05-01", "2026-06-30")),
                List.of(new Media("http://x/2.png", "two"), new Media("http://x/1.png", "one")),
                List.of(new Media("http://x/f.pdf", "file")));
    }

    @Test
    void hashIsStableAcrossRuns() {
        assertThat(Canonicalizer.contentHash(base()))
                .isEqualTo(Canonicalizer.contentHash(base()))
                .hasSize(64);
    }

    @Test
    void imageOrderDoesNotChangeHash_sortedByUrl() {
        CanonicalBus reordered = new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                List.of("A", "B"),
                List.of(new Schedule("all day", "30min", "note")),
                List.of(new Alert("info", "msg", "2026-05-01", "2026-06-30")),
                List.of(new Media("http://x/1.png", "one"), new Media("http://x/2.png", "two")),
                List.of(new Media("http://x/f.pdf", "file")));
        assertThat(Canonicalizer.contentHash(reordered)).isEqualTo(Canonicalizer.contentHash(base()));
    }

    @Test
    void stopOrderChangesHash() {
        CanonicalBus swapped = new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                List.of("B", "A"),
                base().schedules(), base().alerts(), base().images(), base().files());
        assertThat(Canonicalizer.contentHash(swapped)).isNotEqualTo(Canonicalizer.contentHash(base()));
    }

    @Test
    void nullAndEmptyAndWhitespaceNormalizeToSame() {
        CanonicalBus withNulls = new CanonicalBus("VAB 1", null, "  ", null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of());
        CanonicalBus withEmpties = new CanonicalBus("VAB 1", "", "", "", "", "",
                List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(Canonicalizer.contentHash(withNulls)).isEqualTo(Canonicalizer.contentHash(withEmpties));
    }

    @Test
    void trailingWhitespaceTrimmed() {
        CanonicalBus trimmed = new CanonicalBus("VAB 1", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                base().stops(), base().schedules(), base().alerts(), base().images(), base().files());
        CanonicalBus untrimmed = new CanonicalBus("VAB 1 ", "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                base().stops(), base().schedules(), base().alerts(), base().images(), base().files());
        assertThat(Canonicalizer.contentHash(trimmed)).isEqualTo(Canonicalizer.contentHash(untrimmed));
    }
}
