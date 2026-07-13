package dev.seedfinder.waypoint;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WaypointStore {
    public record Waypoint(String label, BlockPos pos, int color) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(WaypointStore.class);
    private static final Path SAVE_FILE =
        FabricLoader.getInstance().getConfigDir().resolve("seedfinder-waypoints.json");
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();

    public static synchronized void add(Waypoint w) { WAYPOINTS.add(w); save(); }
    public static synchronized void clear() { WAYPOINTS.clear(); save(); }

    public static synchronized void remove(int index) {
        if (index >= 0 && index < WAYPOINTS.size()) {
            WAYPOINTS.remove(index);
            save();
        }
    }

    public static synchronized List<Waypoint> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(WAYPOINTS));
    }

    // ─── JSON Persistence (no external library) ──────────────────

    public static synchronized void load() {
        WAYPOINTS.clear();
        if (!Files.exists(SAVE_FILE)) return;
        try {
            String json = Files.readString(SAVE_FILE).trim();
            if (!json.startsWith("[") || !json.endsWith("]")) return;
            String inner = json.substring(1, json.length() - 1).trim();
            if (inner.isEmpty()) return;
            String[] entries = inner.split("\\},\\s*\\{");
            for (String entry : entries) {
                entry = entry.replace("{", "").replace("}", "").trim();
                String label = extractStr(entry, "label");
                int x = Integer.parseInt(extractStr(entry, "x"));
                int y = Integer.parseInt(extractStr(entry, "y"));
                int z = Integer.parseInt(extractStr(entry, "z"));
                long colorHex = Long.parseLong(extractStr(entry, "color").replace("0x", ""), 16);
                WAYPOINTS.add(new Waypoint(label, new BlockPos(x, y, z), (int) colorHex));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse waypoints file, starting fresh: {}", e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < WAYPOINTS.size(); i++) {
                var wp = WAYPOINTS.get(i);
                sb.append("  {\"label\":\"").append(esc(wp.label())).append("\",");
                sb.append("\"x\":").append(wp.pos().getX()).append(",");
                sb.append("\"y\":").append(wp.pos().getY()).append(",");
                sb.append("\"z\":").append(wp.pos().getZ()).append(",");
                sb.append("\"color\":\"0x")
                  .append(Long.toHexString(wp.color() & 0xFFFFFFFFL)).append("\"");
                sb.append("}").append(i < WAYPOINTS.size() - 1 ? "," : "").append("\n");
            }
            sb.append("]");
            Files.writeString(SAVE_FILE, sb.toString());
        } catch (IOException ignored) {}
    }

    private static String extractStr(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        i = json.indexOf(":", i) + 1;
        while (i < json.length() && json.charAt(i) == ' ') i++;
        if (i < json.length() && json.charAt(i) == '"') {
            i++; // skip opening quote
            StringBuilder val = new StringBuilder();
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    i++;
                    if (json.charAt(i) == '"') val.append('"');
                    else if (json.charAt(i) == '\\') val.append('\\');
                    else { val.append('\\'); val.append(json.charAt(i)); }
                } else {
                    val.append(json.charAt(i));
                }
                i++;
            }
            return val.toString();
        }
        // numeric value
        StringBuilder val = new StringBuilder();
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || json.charAt(i) == '-')) {
            val.append(json.charAt(i)); i++;
        }
        return val.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
