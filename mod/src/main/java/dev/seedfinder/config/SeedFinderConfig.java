package dev.seedfinder.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny properties-backed config so we don't pull a JSON lib. */
public class SeedFinderConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("seedfinder.properties");
    private static Long seed = null;
    private static int searchRadiusChunks = 3200; // ~50 000 blocks

    public static synchronized void load() {
        if (!Files.exists(FILE)) return;
        try (var in = Files.newInputStream(FILE)) {
            Properties p = new Properties();
            p.load(in);
            String s = p.getProperty("seed");
            if (s != null && !s.isBlank()) seed = Long.parseLong(s.trim());
            String r = p.getProperty("radius");
            if (r != null) searchRadiusChunks = Integer.parseInt(r.trim());
        } catch (IOException | NumberFormatException ignored) {}
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            if (seed != null) p.setProperty("seed", Long.toString(seed));
            p.setProperty("radius", Integer.toString(searchRadiusChunks));
            try (var out = Files.newOutputStream(FILE)) {
                p.store(out, "SeedFinder config");
            }
        } catch (IOException ignored) {}
    }

    /** Set seed in memory without disk write (GUI keystroke debounce). */
    public static synchronized void setSeedMem(long s) { seed = s; }

    public static Long getSeed() { return seed; }
    public static void setSeed(long s) { seed = s; save(); }
    public static int getSearchRadiusChunks() { return searchRadiusChunks; }
    public static void setSearchRadius(int r) { searchRadiusChunks = r; save(); }
    public static boolean hasSeed() { return seed != null; }
}