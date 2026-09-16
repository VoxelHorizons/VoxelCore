package org.voxelhorizons.content.block;

/** Deterministic mappings between stable allocation slots and vanilla carrier states. */
public final class BlockCarrierStates {
    private static final String[] INSTRUMENTS = {
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone"
    };
    private static final String[] MUSHROOMS = {
            "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block", "minecraft:mushroom_stem"
    };
    private static final int[] LEGACY_MUSHROOM_DATA = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15};

    private BlockCarrierStates() {}

    public static int capacity(BlockMethod method, boolean modern) {
        if (method == BlockMethod.SOLID) return modern ? INSTRUMENTS.length * 25 : LEGACY_MUSHROOM_DATA.length * 2;
        if (method == BlockMethod.MUSHROOM) return modern ? 189 : LEGACY_MUSHROOM_DATA.length * 2;
        return 0;
    }

    public static String state(BlockAllocation allocation, boolean modern) {
        if (!modern) {
            int slot = allocation.slot();
            int perBlock = LEGACY_MUSHROOM_DATA.length;
            String material = slot < perBlock ? "HUGE_MUSHROOM_1" : "HUGE_MUSHROOM_2";
            return "legacy:" + material + ":" + LEGACY_MUSHROOM_DATA[slot % perBlock];
        }
        if (allocation.method() == BlockMethod.SOLID) {
            int instrument = allocation.slot() / 25;
            int note = allocation.slot() % 25;
            if (instrument >= INSTRUMENTS.length) throw new IllegalArgumentException("Solid block allocation is outside supported capacity");
            return "minecraft:note_block[instrument=" + INSTRUMENTS[instrument] + ",note=" + note + ",powered=false]";
        }
        if (allocation.method() == BlockMethod.MUSHROOM) {
            int block = allocation.slot() / 63;
            int bits = (allocation.slot() % 63) + 1;
            if (block >= MUSHROOMS.length) throw new IllegalArgumentException("Mushroom block allocation is outside supported capacity");
            return MUSHROOMS[block] + "[down=" + bit(bits, 0) + ",east=" + bit(bits, 1)
                    + ",north=" + bit(bits, 2) + ",south=" + bit(bits, 3) + ",up=" + bit(bits, 4)
                    + ",west=" + bit(bits, 5) + "]";
        }
        throw new IllegalArgumentException("Block method " + allocation.method() + " is not implemented yet");
    }

    public static String blockName(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    public static String properties(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? "" : state.substring(bracket + 1, state.length() - 1);
    }

    private static boolean bit(int value, int bit) { return (value & (1 << bit)) != 0; }
}
