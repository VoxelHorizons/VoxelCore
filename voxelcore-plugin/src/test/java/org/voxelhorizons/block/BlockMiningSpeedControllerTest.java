package org.voxelhorizons.block;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BlockMiningSpeedControllerTest {

    @Test
    public void scalesCarrierSpeedToConfiguredHardness() {
        assertEquals(0.8D / 12.0D,
                BlockMiningSpeedController.hardnessMultiplier(0.8D, 12.0D), 0.000001D);
        assertEquals(4.0D,
                BlockMiningSpeedController.hardnessMultiplier(2.0D, 0.5D), 0.000001D);
    }

    @Test
    public void ignoresNonPositiveHardnessInputsAndBoundsExtremeValues() {
        assertEquals(1.0D,
                BlockMiningSpeedController.hardnessMultiplier(0.8D, 0.0D), 0.000001D);
        assertEquals(0.0001D,
                BlockMiningSpeedController.hardnessMultiplier(0.8D, Double.MAX_VALUE), 0.0D);
        assertEquals(1024.0D,
                BlockMiningSpeedController.hardnessMultiplier(Double.MAX_VALUE, 0.1D), 0.0D);
    }
}
