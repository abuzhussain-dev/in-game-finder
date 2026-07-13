package dev.seedfinder.util;

import net.minecraft.client.MinecraftClient;

/** Shared touch device detection. Eliminates duplicate in SeedFinderMod + StructurePickerScreen. */
public final class TouchUtil {
    private static Boolean cached = null;

    public static boolean isTouchDevice() {
        if (cached != null) return cached;
        try {
            cached = MinecraftClient.getInstance().getWindow().getWidth() < 800;
        } catch (Exception e) {
            cached = false;
        }
        return cached;
    }

    /** Re-check on window resize. */
    public static void invalidate() { cached = null; }

    private TouchUtil() {}
}
