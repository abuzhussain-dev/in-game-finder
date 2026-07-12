package dev.seedfinder;

import dev.seedfinder.command.SeedFinderCommand;
import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.gui.StructurePickerScreen;
import dev.seedfinder.waypoint.WaypointRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SeedFinderMod implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        SeedFinderConfig.load();
        SeedFinderCommand.register();
        WaypointRenderer.register();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seedfinder.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.seedfinder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new StructurePickerScreen());
            }
        });
    }
}