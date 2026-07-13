package dev.seedfinder.finder;

/**
 * Vanilla structures with their placement parameters.
 * Values from Minecraft Wiki + decompiled MC 1.21.11 source.
 *
 * IMPORTANT: frequency and triangular spread affect the RNG call sequence.
 * When frequency < 1.0, the RNG calls are:
 *   1. nextInt(range) -> offsetX
 *   2. nextInt(range) -> offsetZ  (or 4 calls for triangular)
 *   3. nextFloat()     -> frequency check
 */
public enum StructureType {
    VILLAGE("Village", Placement.SCATTER, 34, 8, 10387312,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    PILLAGER_OUTPOST("Pillager Outpost", Placement.SCATTER, 32, 8, 165745296,
        SpreadType.LINEAR, 0.2, 0, 0, Dimension.OVERWORLD, 0),
    DESERT_PYRAMID("Desert Pyramid", Placement.SCATTER, 32, 8, 14357617,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    JUNGLE_TEMPLE("Jungle Temple", Placement.SCATTER, 32, 8, 14357619,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    SWAMP_HUT("Swamp Hut", Placement.SCATTER, 32, 8, 14357620,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    IGLOO("Igloo", Placement.SCATTER, 32, 8, 14357618,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    OCEAN_MONUMENT("Ocean Monument", Placement.SCATTER, 32, 5, 10387313,
        SpreadType.TRIANGULAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    WOODLAND_MANSION("Woodland Mansion", Placement.SCATTER, 80, 20, 10387319,
        SpreadType.TRIANGULAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    RUINED_PORTAL("Ruined Portal", Placement.SCATTER, 40, 15, 34222645,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.ALL, 0),
    SHIPWRECK("Shipwreck", Placement.SCATTER, 24, 4, 165745295,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    BURIED_TREASURE("Buried Treasure", Placement.SCATTER, 1, 0, 0,
        SpreadType.LINEAR, 0.01, 9, 9, Dimension.OVERWORLD, 0),
    ANCIENT_CITY("Ancient City", Placement.SCATTER, 24, 8, 20083232,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    TRIAL_CHAMBERS("Trial Chambers", Placement.SCATTER, 34, 12, 94251327,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0),
    NETHER_FORTRESS("Nether Fortress", Placement.SCATTER, 27, 4, 30084232,
        SpreadType.LINEAR, 0.4, 0, 0, Dimension.NETHER, 1),
    BASTION_REMNANT("Bastion Remnant", Placement.SCATTER, 27, 4, 30084232,
        SpreadType.LINEAR, 0.6, 0, 0, Dimension.NETHER, 1),
    END_CITY("End City", Placement.SCATTER, 20, 11, 10387313,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.END),
    // DISABLED — PER_CHUNK structures hidden from GUI
    MINESHAFT("Mineshaft", Placement.PER_CHUNK, 1, 0, 0,
        SpreadType.LINEAR, 0.004, 0, 0, Dimension.OVERWORLD, 0),
    STRONGHOLD("Stronghold", Placement.STRONGHOLD_RING, 0, 0, 0,
        SpreadType.LINEAR, 1.0, 0, 0, Dimension.OVERWORLD, 0);

    public enum Placement { SCATTER, STRONGHOLD_RING, PER_CHUNK }
    public enum SpreadType { LINEAR, TRIANGULAR }
    public enum Dimension { OVERWORLD, NETHER, END, ALL }

    public final String displayName;
    public final Placement placement;
    public final int spacing;
    public final int separation;
    public final int salt;
    public final SpreadType spreadType;
    public final double frequency;
    public final int locateOffsetX;
    public final int locateOffsetZ;
    public final Dimension dimension;
    /**
     * Non-zero when multiple structure types share the same salt/spacing/separation
     * (e.g. Nether Fortress + Bastion are the same nether_complexes set).
     * The frequency field encodes their relative split within the group.
     */
    public final int sharedSaltGroup;

    StructureType(String displayName, Placement placement, int spacing, int separation, int salt,
                   SpreadType spreadType, double frequency, int locateOffsetX, int locateOffsetZ,
                   Dimension dimension, int sharedSaltGroup) {
        this.displayName = displayName;
        this.placement = placement;
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.spreadType = spreadType;
        this.frequency = frequency;
        this.locateOffsetX = locateOffsetX;
        this.locateOffsetZ = locateOffsetZ;
        this.dimension = dimension;
        this.sharedSaltGroup = sharedSaltGroup;
    }

    /** Whether this structure should appear in the GUI. */
    public boolean isSearchable() {
        return placement != Placement.PER_CHUNK;
    }
}
