package dev.seedfinder.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.gui.StructurePickerScreen;
import dev.seedfinder.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class SeedFinderCommand {
    private SeedFinderCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("seedfinder")
                .then(ClientCommandManager.literal("open").executes(ctx -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new StructurePickerScreen()));
                    return 1;
                }))
                .then(ClientCommandManager.literal("seed")
                    .then(ClientCommandManager.argument("value", LongArgumentType.longArg()).executes(ctx -> {
                        long v = LongArgumentType.getLong(ctx, "value");
                        SeedFinderConfig.setSeed(v);
                        ctx.getSource().sendFeedback(Text.translatable("seedfinder.msg.seed_set"));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    WaypointStore.clear();
                    ctx.getSource().sendFeedback(Text.literal("Waypoints cleared."));
                    return 1;
                })));
        });
    }
}