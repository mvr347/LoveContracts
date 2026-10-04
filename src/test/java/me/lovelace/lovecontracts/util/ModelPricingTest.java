package me.lovelace.lovecontracts.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelPricingTest {

    @Test
    void ratioIsClamped() {
        assertEquals(3.0, ModelPricing.clamp(10.0, 0.5, 3.0));
        assertEquals(0.5, ModelPricing.clamp(0.01, 0.5, 3.0));
        assertEquals(1.2, ModelPricing.clamp(1.2, 0.5, 3.0));
    }

    @Test
    void brokenRatioFallsBackToOne() {
        assertEquals(1.0, ModelPricing.clamp(Double.NaN, 0.5, 3.0));
        assertEquals(1.0, ModelPricing.clamp(Double.POSITIVE_INFINITY, 0.5, 3.0));
    }
}
