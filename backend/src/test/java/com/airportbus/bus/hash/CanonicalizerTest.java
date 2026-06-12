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

    @Test
    void nfcNormalizationProducesSameHash() {
        // 预组合形:Å=U+00C5, ö=U+00F6。分解形:A+◌̊(U+030A), o+◌̈(U+0308)。
        // 两者在源码里是不同的码点序列,经 NFC 归一后应等价 → 同一 hash。
        // 守住 E2 的 NFC 路径:若 norm() 里的 Normalizer.normalize 被误删,本测试会变红。
        String pre = "\u00C5ngstr\u00F6m";        // precomposed: A-ring(U+00C5) + o-diaeresis(U+00F6)
        String dec = "A\u030Angstro\u0308m";      // decomposed: A + U+030A , o + U+0308
        assertThat(pre).isNotEqualTo(dec);          // 自检:源码层面确实不同
        CanonicalBus precomposed = new CanonicalBus(pre, "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                base().stops(), base().schedules(), base().alerts(), base().images(), base().files());
        CanonicalBus decomposed = new CanonicalBus(dec, "Westbahnhof", "ÖBB", "40min", "€11", "03:00-24:00",
                base().stops(), base().schedules(), base().alerts(), base().images(), base().files());
        assertThat(Canonicalizer.contentHash(precomposed)).isEqualTo(Canonicalizer.contentHash(decomposed));
    }
}
