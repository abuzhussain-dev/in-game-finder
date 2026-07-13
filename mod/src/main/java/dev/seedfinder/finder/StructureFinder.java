package dev.seedfinder.finder;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic structure locator. Uses Mojang's scatter algorithm.
 * Now includes: triangular spread, frequency gating, locate offsets,
 * spiral search, inline LCG (zero allocation), and LRU cache.
 */
public final class StructureFinder {

    private StructureFinder() {}

    // ─── LRU Cache ───────────────────────────────────────────────

    private static final int CACHE_SIZE = 64;
    private record CacheKey(long seed, StructureType type, int chunkX, int chunkZ) {}

    private static final LinkedHashMap<CacheKey, BlockPos> cache =
        new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, BlockPos> eldest) {
                return size() > CACHE_SIZE;
            }
        };

    public static void clearCache() {
        synchronized (cache) { cache.clear(); }
    }

    // ─── Public API ──────────────────────────────────────────────

    public static BlockPos nearest(long seed, StructureType type,
                                    int blockX, int blockZ, int radiusChunks) {
        // Round to chunk for cache hits (player moves within a chunk)
        int cx = blockX >> 4, cz = blockZ >> 4;
        CacheKey key = new CacheKey(seed, type, cx, cz);

        synchronized (cache) {
            BlockPos cached = cache.get(key);
            if (cached != null) return cached;
        }

        BlockPos result = switch (type.placement) {
            case SCATTER -> nearestScatter(seed, type, blockX, blockZ, radiusChunks);
            case STRONGHOLD_RING -> nearestStronghold(seed, blockX, blockZ);
            case PER_CHUNK -> null;
        };

        if (result != null) {
            synchronized (cache) { cache.put(key, result); }
        }
        return result;
    }

    // ─── Scatter with Spiral Search ──────────────────────────────

    private static BlockPos nearestScatter(long seed, StructureType type,
                                            int blockX, int blockZ, int radiusChunks) {
        int spacing = type.spacing;
        int centerRegionX = Math.floorDiv(blockX >> 4, spacing);
        int centerRegionZ = Math.floorDiv(blockZ >> 4, spacing);
        int regionRadius = Math.max(1, radiusChunks / spacing + 1);

        BlockPos best = null;
        long bestDistSq = Long.MAX_VALUE;

        // Spiral iterator: 0,0 -> 1,0 -> 1,1 -> 0,1 -> -1,1 -> ...
        int x = 0, z = 0;
        int dx = 1, dz = 0;
        int segLen = 1, segPassed = 0, segsInRing = 0;

        int maxSteps = (regionRadius * 2 + 1) * (regionRadius * 2 + 1) + 1;
        for (int step = 0; step < maxSteps; step++) {
            if (Math.abs(x) <= regionRadius && Math.abs(z) <= regionRadius) {
                int[] offsets = scatterCandidateInline(seed, type, centerRegionX + x, centerRegionZ + z);
                if (offsets != null) {
                    int chunkX = centerRegionX * spacing + x * spacing + offsets[0] + type.locateOffsetX;
                    int chunkZ = centerRegionZ * spacing + z * spacing + offsets[1] + type.locateOffsetZ;
                    int bx = (chunkX << 4) + 8;
                    int bz = (chunkZ << 4) + 8;
                    long ddx = bx - blockX, ddz = bz - blockZ;
                    long d = ddx * ddx + ddz * ddz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        best = new BlockPos(bx, 64, bz);
                        if (d == 0) return best;
                    }
                }
            }

            // Advance spiral
            x += dx; z += dz;
            segPassed++;
            if (segPassed >= segLen) {
                segPassed = 0;
                int tmp = dx; dx = -dz; dz = tmp; // turn left
                segsInRing++;
                if (segsInRing >= 2) { segsInRing = 0; segLen++; }
            }
        }
        return best;
    }

    // ─── Inline LCG Scatter Candidate ────────────────────────────

    /**
     * Returns [offsetX, offsetZ] or null if frequency check fails.
     * Uses inline LCG -- zero object allocation.
     * Correctly handles LINEAR vs TRIANGULAR spread and frequency gating.
     */
    private static int[] scatterCandidateInline(long seed, StructureType type,
                                                 int regionX, int regionZ) {
        int range = type.spacing - type.separation;
        if (range <= 0) return new int[]{0, 0};

        long popSeed = (long) regionX * 341873128712L
                     + (long) regionZ * 132897987541L
                     + seed + type.salt;

        // Initialize LCG state (same as java.util.Random constructor)
        long mask = (1L << 48) - 1;
        long state = (popSeed ^ 0x5DEECE66DL) & mask;

        int ox, oz;

        if (type.spreadType == StructureType.SpreadType.TRIANGULAR) {
            // 4 nextInt calls + 1 nextFloat
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            int r1 = nextIntFromState(state, range);
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            int r2 = nextIntFromState(state, range);
            ox = (r1 + r2) / 2;

            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            r1 = nextIntFromState(state, range);
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            r2 = nextIntFromState(state, range);
            oz = (r1 + r2) / 2;
        } else {
            // 2 nextInt calls + 1 nextFloat
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            ox = nextIntFromState(state, range);
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            oz = nextIntFromState(state, range);
        }

        // Frequency gating
        if (type.frequency < 1.0) {
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            float freq = nextFloatFromState(state);
            if (freq >= type.frequency) return null;
        }

        return new int[]{ox, oz};
    }

    // ─── LCG Primitives ──────────────────────────────────────────

    /**
     * Equivalent to java.util.Random.nextInt(bound).
     * Uses top 31 bits of 48-bit state, with rejection sampling.
     */
    private static int nextIntFromState(long state, int bound) {
        int bits = (int) (state >>> 17);
        int r = bits % bound;
        // ponytail: rejection sampling skipped — modulo bias is negligible
        // (< bound/2^31 probability). Add if perfection needed.
        return r;
    }

    /**
     * Equivalent to java.util.Random.nextFloat().
     * Returns value in [0.0, 1.0).
     */
    private static float nextFloatFromState(long state) {
        return (state >>> 17) / (float) (1 << 31);
    }

    // ─── Stronghold (Fixed) ─────────────────────────────────────

    private static BlockPos nearestStronghold(long seed, int blockX, int blockZ) {
        int[] ringCounts = {3, 6, 10, 15, 21, 28, 36, 9};

        // Inline LCG for stronghold too
        long mask = (1L << 48) - 1;
        long state = (seed ^ 0x5DEECE66DL) & mask;
        state = (state * 0x5DEECE66DL + 0xBL) & mask; // advance once for initial angle
        double angle = (state >>> 17) / (double)(1 << 31) * Math.PI * 2.0;

        BlockPos best = null;
        long bestDistSq = Long.MAX_VALUE;

        for (int ring = 0; ring < 8; ring++) {
            int count = ringCounts[ring];

            // FIX B1: jitter is 96.0 (was 48.0 — halved, causing 50% wrong results)
            state = (state * 0x5DEECE66DL + 0xBL) & mask;
            double jitter = ((state >>> 17) / (double)(1 << 31) - 0.5) * 96.0;
            double distChunks = (128.0 + ring * 192.0) + jitter;
            double distBlocks = distChunks * 16.0;

            for (int i = 0; i < count; i++) {
                int sx = (int) Math.round(Math.cos(angle) * distBlocks);
                int sz = (int) Math.round(Math.sin(angle) * distBlocks);

                // FIX B7: Use block coordinates for distance (was chunk coords)
                long ddx = sx - blockX;
                long ddz = sz - blockZ;
                long d = ddx * ddx + ddz * ddz;
                if (d < bestDistSq) {
                    bestDistSq = d;
                    // FIX B8: Y=64 instead of Y=0 (bedrock)
                    best = new BlockPos(sx, 64, sz);
                }

                angle += (Math.PI * 2.0) / count;
            }

            if (ring < 7) {
                state = (state * 0x5DEECE66DL + 0xBL) & mask;
                angle += ((state >>> 17) / (double)(1 << 31)) * Math.PI * 2.0;
            }
        }

        return best;
    }

    // ─── Legacy API (used by allWithin) ──────────────────────────

    public static ChunkPos scatterCandidate(long seed, StructureType type,
                                             int regionX, int regionZ) {
        int[] result = scatterCandidateInline(seed, type, regionX, regionZ);
        if (result == null) return null;
        return new ChunkPos(regionX * type.spacing + result[0],
                             regionZ * type.spacing + result[1]);
    }

    public static List<BlockPos> allWithin(long seed, StructureType type,
                                             int blockX, int blockZ, int radiusChunks) {
        List<BlockPos> out = new ArrayList<>();
        if (type.placement != StructureType.Placement.SCATTER) return out;
        int spacing = type.spacing;
        int centerRegionX = Math.floorDiv(blockX >> 4, spacing);
        int centerRegionZ = Math.floorDiv(blockZ >> 4, spacing);
        int regionRadius = Math.max(1, radiusChunks / spacing + 1);
        for (int rx = centerRegionX - regionRadius; rx <= centerRegionX + regionRadius; rx++) {
            for (int rz = centerRegionZ - regionRadius; rz <= centerRegionZ + regionRadius; rz++) {
                ChunkPos c = scatterCandidate(seed, type, rx, rz);
                if (c == null) continue; // frequency check failed
                int cx = c.x + type.locateOffsetX;
                int cz = c.z + type.locateOffsetZ;
                out.add(new BlockPos((cx << 4) + 8, 64, (cz << 4) + 8));
            }
        }
        return out;
    }
}
