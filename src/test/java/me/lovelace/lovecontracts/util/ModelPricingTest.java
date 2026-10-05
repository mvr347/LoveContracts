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

    @Test
    void minedBlocksMapToTheirDrops() {
        assertEquals("COBBLESTONE", ModelPricing.dropName("STONE"));
        assertEquals("COBBLED_DEEPSLATE", ModelPricing.dropName("DEEPSLATE"));
        assertEquals("NETHERITE_SCRAP", ModelPricing.dropName("ANCIENT_DEBRIS"));
        assertEquals("RAW_IRON", ModelPricing.dropName("DEEPSLATE_IRON_ORE"));
        assertEquals("COAL", ModelPricing.dropName("COAL_ORE"));
        assertEquals("GOLD_NUGGET", ModelPricing.dropName("NETHER_GOLD_ORE"));
        assertEquals(null, ModelPricing.dropName("SAND"));
    }
}
