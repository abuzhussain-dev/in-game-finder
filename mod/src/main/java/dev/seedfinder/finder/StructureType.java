package dev.seedfinder.finder;

/**
 * Vanilla structures with their scatter placement parameters.
 * Values mirror Mojang's data-driven structure_set spacing/separation/salt as of 1.21.11.
 * Stronghold is special (ring algorithm), End City and Nether Fortress use scatter but in other dimensions.
 */
public enum StructureType {
    VILLAGE("Village", Placement.SCATTER, 34, 8, 10387312),
    PILLAGER_OUTPOST("Pillager Outpost", Placement.SCATTER, 32, 8, 165745296, SpreadType.LINEAR, 0.2, 0, 0),
    DESERT_PYRAMID("Desert Pyramid", Placement.SCATTER, 32, 8, 14357617),
    JUNGLE_TEMPLE("Jungle Temple", Placement.SCATTER, 32, 8, 14357619),
    SWAMP_HUT("Swamp Hut", Placement.SCATTER, 32, 8, 14357620),
    IGLOO("Igloo", Placement.SCATTER, 32, 8, 14357618),
    OCEAN_MONUMENT("Ocean Monument", Placement.SCATTER, 32, 5, 10387313, SpreadType.TRIANGULAR, 1.0, 0, 0),
    WOODLAND_MANSION("Woodland Mansion", Placement.SCATTER, 80, 20, 10387319, SpreadType.TRIANGULAR, 1.0, 0, 0),
    RUINED_PORTAL("Ruined Portal", Placement.SCATTER, 40, 15, 34222645),
    SHIPWRECK("Shipwreck", Placement.SCATTER, 24, 4, 165745295),
    BURIED_TREASURE("Buried Treasure", Placement.SCATTER, 1, 0, 0, SpreadType.LINEAR, 0.01, 9, 9),
    ANCIENT_CITY("Ancient City", Placement.SCATTER, 24, 8, 20083232),
    TRIAL_CHAMBERS("Trial Chambers", Placement.SCATTER, 34, 12, 94251327),
    NETHER_FORTRESS("Nether Fortress", Placement.SCATTER, 27, 4, 30084232),
    BASTION_REMNANT("Bastion Remnant", Placement.SCATTER, 27, 4, 30084232),
    END_CITY("End City", Placement.SCATTER, 20, 11, 10387313),
    MINESHAFT("Mineshaft", Placement.PER_CHUNK, 1, 0, 0, SpreadType.LINEAR, 0.004, 0, 0),
    STRONGHOLD("Stronghold", Placement.STRONGHOLD_RING, 0, 0, 0);

    public enum Placement { SCATTER, STRONGHOLD_RING, PER_CHUNK }
    public enum SpreadType { LINEAR, TRIANGULAR }

    public final String displayName;
    public final Placement placement;
    public final int spacing;      // in chunks
    public final int separation;   // in chunks
    public final int salt;
    public final SpreadType spreadType;
    public final double frequency; // probability of placement attempt (1.0 = always)
    public final int locateOffsetX; // chunk offset for /locate coordinates
    public final int locateOffsetZ; // chunk offset for /locate coordinates

    // Full constructor for structures with non-default parameters
    StructureType(String displayName, Placement placement, int spacing, int separation, int salt,
                  SpreadType spreadType, double frequency, int locateOffsetX, int locateOffsetZ) {
        this.displayName = displayName;
        this.placement = placement;
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.spreadType = spreadType;
        this.frequency = frequency;
        this.locateOffsetX = locateOffsetX;
        this.locateOffsetZ = locateOffsetZ;
    }

    // Simplified constructor for common case (linear, 100% frequency, no offset)
    StructureType(String displayName, Placement placement, int spacing, int separation, int salt) {
        this(displayName, placement, spacing, separation, salt, SpreadType.LINEAR, 1.0, 0, 0);
    }
}
