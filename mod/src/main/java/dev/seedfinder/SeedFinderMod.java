package dev.seedfinder;

import dev.seedfinder.command.SeedFinderCommand;
import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.gui.StructurePickerScreen;
import dev.seedfinder.waypoint.WaypointRenderer;
import dev.seedfinder.waypoint.WaypointStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return; // ponytail: guard main menu NPE
            while (openKey.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new StructurePickerScreen());
            }
        });

        // Floating SF button for mobile (Zalith Launcher 2)
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            var cl = MinecraftClient.getInstance();
            if (cl.world == null) return;
            if (!isTouchDevice()) return;
            int x = cl.getWindow().getWidth() - 50;
            int y = cl.getWindow().getHeight() - 50;
            ctx.fill(x, y, x + 40, y + 40, 0x8800AAFF);
            ctx.drawText(cl.textRenderer, "SF", x + 10, y + 12, 0xFFFFFF, true);
        });
    }

    private static boolean isTouchDevice() {
        try {
            return MinecraftClient.getInstance().getWindow().getWidth() < 800;
        } catch (Exception e) { return false; }
    }
}
