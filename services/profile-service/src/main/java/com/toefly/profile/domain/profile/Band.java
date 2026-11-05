package com.toefly.profile.domain.profile;

import java.util.Collection;

/** Score on the TOEFL iBT 2026 scale: 1.0 to 6.0 in steps of 0.5. */
public record Band(double value) {

    public static final double MIN = 1.0;
    public static final double MAX = 6.0;

    public Band {
        if (value < MIN || value > MAX || value * 2 != Math.rint(value * 2)) {
            throw new InvalidProfileValueException("band", "A band is between 1.0 and 6.0 in steps of 0.5.");
        }
    }

    public static Band of(double value) {
        return new Band(value);
    }

    /** Mean of the bands rounded half up to the nearest 0.5 (INV-PRF-01). */
    public static Band roundHalfMean(Collection<Band> bands) {
        if (bands.isEmpty()) {
            throw new IllegalArgumentException("no bands");
        }
        double mean = bands.stream().mapToDouble(Band::value).average().orElseThrow();
        return new Band(Math.floor(mean * 2 + 0.5) / 2);
    }
}
