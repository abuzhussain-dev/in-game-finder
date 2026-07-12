package dev.seedfinder.waypoint;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WaypointStore {
    public record Waypoint(String label, BlockPos pos, int color) {}

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();

    public static synchronized void add(Waypoint w) { WAYPOINTS.add(w); }
    public static synchronized void clear() { WAYPOINTS.clear(); }
    public static synchronized List<Waypoint> snapshot() { return Collections.unmodifiableList(new ArrayList<>(WAYPOINTS)); }
}