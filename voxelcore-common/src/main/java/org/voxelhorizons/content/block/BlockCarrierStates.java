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

    /**
     * A deliberately narrow pool for NEW modern custom blocks: a pair of opposite
     * horizontal cap/stem faces is exposed while the perpendicular pair is hidden.
     * The vanilla giant-mushroom cap and stem generators do not form these opposed,
     * isolated pairs. This excludes naturally grown mushrooms and the all-true
     * block placed from a vanilla inventory; commands/debug sticks can still make
     * ANY valid Java blockstate, so this is not a universal noncollision guarantee.
     *
     * Existing allocations remain stable even if they predate this reservation policy.
     */
    public static boolean reservedMushroomSlot(int slot) {
        if (slot < 0 || slot >= 189) return false;
        int bits = (slot % 63) + 1;
        boolean east = bit(bits, 1), north = bit(bits, 2);
        boolean south = bit(bits, 3), west = bit(bits, 5);
        return (east && west && !north && !south) || (north && south && !east && !west);
    }

    /** Note blocks have no states provably unused by normal vanilla music mechanics. */
    public static boolean safeForNewAllocation(BlockMethod method, int slot, boolean modern) {
        if (!modern) return method == BlockMethod.MUSHROOM && slot >= 0 && slot < capacity(method, false);
        return method == BlockMethod.MUSHROOM && reservedMushroomSlot(slot);
    }

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
