package dev.seedfinder;

import dev.seedfinder.command.SeedFinderCommand;
import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.gui.StructurePickerScreen;
import dev.seedfinder.util.TouchUtil;
import dev.seedfinder.waypoint.WaypointRenderer;
import dev.seedfinder.waypoint.WaypointStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SeedFinderMod implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        SeedFinderConfig.load();
        WaypointStore.load();
        SeedFinderCommand.register();
        WaypointRenderer.register();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seedfinder.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            KeyBinding.Category.create(Identifier.of("key.categories.seedfinder"))
        ));

        // ponytail: window resize callback to invalidate touch cache
        var cl = MinecraftClient.getInstance();
        cl.getWindow().setCallback(new WindowCallback(cl));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;
            while (openKey.wasPressed()) {
                client.setScreen(new StructurePickerScreen());
            }
        });

        // Floating SF button for mobile (Zalith Launcher 2)
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            var c = MinecraftClient.getInstance();
            if (c.world == null) return;
            if (!TouchUtil.isTouchDevice()) return;
            int size = 44;
            int x = c.getWindow().getScaledWidth() - size - 6;
            int y = c.getWindow().getScaledHeight() - size - 6;
            renderFloatingButton(ctx, x, y, size);
            if (c.mouse.wasLeftButtonClicked()) {
                double mx = c.mouse.getX() * c.getWindow().getScaledWidth() / c.getWindow().getWidth();
                double my = c.mouse.getY() * c.getWindow().getScaledHeight() / c.getWindow().getHeight();
                if (mx >= x && mx <= x + size && my >= y && my <= y + size) {
                    c.setScreen(new StructurePickerScreen());
                }
            }
        });
    }

    /** Draw a rounded-ish floating action button. */
    private static void renderFloatingButton(DrawContext ctx, int x, int y, int size) {
        // Background circle (approximate with filled rect + inset)
        ctx.fill(x, y, x + size, y + size, 0xCC0066FF);
        // Inner lighter square
        ctx.fill(x + 4, y + 4, x + size - 4, y + size - 4, 0xDD0088FF);
        // Label
        var tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawText(tr, "SF", x + size / 2 - tr.getWidth("SF") / 2,
            y + size / 2 - 4, 0xFFFFFF, true);
        // Border
        ctx.fill(x, y, x + size, y + 1, 0x44000000);
        ctx.fill(x, y + size - 1, x + size, y + size, 0x44000000);
        ctx.fill(x, y, x + 1, y + size, 0x44000000);
        ctx.fill(x + size - 1, y, x + size, y + size, 0x44000000);
    }

    /**
     * Window callback wrapper that invalidates touch detection on resize.
     * ponytail: minimal wrapper, avoids a separate event listener class.
     */
    private record WindowCallback(MinecraftClient client)
            implements org.lwjgl.glfw.GLFWWindowSizeCallbackI {
        @Override
        public void invoke(long window, int w, int h) {
            TouchUtil.invalidate();
            if (client.getWindow().getCallback() != null) {
                client.getWindow().getCallback().invoke(window, w, h);
            }
        }
    }
}
