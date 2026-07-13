package dev.seedfinder.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.finder.StructureFinder;
import dev.seedfinder.finder.StructureType;
import dev.seedfinder.gui.StructurePickerScreen;
import dev.seedfinder.waypoint.WaypointStore;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.minecraft.server.command.CommandManager.literal;

public final class SeedFinderCommand {
    private SeedFinderCommand() {}
    private static final Random RAND = new Random();

    /** Build list of all valid structure names+aliases for tab completion. */
    private static final List<String> STRUCTURE_SUGGESTIONS = buildSuggestions();
    private static final SuggestionProvider<FabricClientCommandSource> STRUCTURE_SUGGESTION_PROVIDER =
        (ctx, builder) -> {
            for (String s : STRUCTURE_SUGGESTIONS) {
                if (s.startsWith(builder.getRemainingLowerCase())) builder.suggest(s);
            }
            return builder.buildFuture();
        };

    private static List<String> buildSuggestions() {
        List<String> out = new ArrayList<>();
        for (StructureType t : StructureType.values()) {
            if (!t.isSearchable()) continue;
            out.add(t.name().toLowerCase());
            out.add(t.displayName.toLowerCase());
        }
        // Add all aliases
        out.add("ac"); out.add("ancient"); out.add("city");
        out.add("tc"); out.add("trial"); out.add("chamber");
        out.add("po"); out.add("outpost"); out.add("pillager");
        out.add("vil"); out.add("village");
        out.add("om"); out.add("monument"); out.add("ocean");
        out.add("wm"); out.add("mansion"); out.add("woodland");
        out.add("nf"); out.add("fortress");
        out.add("br"); out.add("bastion");
        out.add("ec"); out.add("endcity");
        out.add("sh"); out.add("stronghold");
        out.add("pyramid"); out.add("desert");
        out.add("jungle"); out.add("temple");
        out.add("igloo");
        out.add("hut"); out.add("swamp");
        out.add("shipwreck"); out.add("ship");
        out.add("portal"); out.add("ruined");
        return out;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("seedfinder")
                // ─── open ───────────────────────────────────────────
                .then(ClientCommandManager.literal("open").executes(ctx -> {
                    MinecraftClient.getInstance().send(() ->
                        MinecraftClient.getInstance().setScreen(new StructurePickerScreen()));
                    return 1;
                }))
                // ─── seed <value> ─────────────────────────────────────
                .then(ClientCommandManager.literal("seed")
                    .then(ClientCommandManager.argument("value", LongArgumentType.longArg()).executes(ctx -> {
                        long v = LongArgumentType.getLong(ctx, "value");
                        SeedFinderConfig.setSeed(v);
                        ctx.getSource().sendFeedback(Text.literal("Seed set to " + v));
                        return 1;
                    })))
                // ─── clear ────────────────────────────────────────────
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    WaypointStore.clear();
                    ctx.getSource().sendFeedback(Text.literal("Waypoints cleared."));
                    return 1;
                }))
                // ─── find <structure> [radius] [nearest|random] ──────
                .then(ClientCommandManager.literal("find")
                    .then(ClientCommandManager.argument("structure", StringArgumentType.word())
                        .suggests(STRUCTURE_SUGGESTION_PROVIDER)
                        .executes(ctx -> findStructure(ctx, ctx.getArgument("structure", String.class), 640, false))
                        .then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(1, 10000))
                            .executes(ctx -> findStructure(ctx, ctx.getArgument("structure", String.class),
                                IntegerArgumentType.getInteger(ctx, "radius"), false))
                            .then(ClientCommandManager.literal("nearest").executes(ctx ->
                                findStructure(ctx, ctx.getArgument("structure", String.class),
                                    IntegerArgumentType.getInteger(ctx, "radius"), false)))
                            .then(ClientCommandManager.literal("random").executes(ctx ->
                                findStructure(ctx, ctx.getArgument("structure", String.class),
                                    IntegerArgumentType.getInteger(ctx, "radius"), true)))
                        )
                    ))
                // ─── list ─────────────────────────────────────────────
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    var wps = WaypointStore.snapshot();
                    if (wps.isEmpty()) {
                        ctx.getSource().sendFeedback(Text.literal("No waypoints.").formatted(Formatting.GRAY));
                        return 1;
                    }
                    ctx.getSource().sendFeedback(Text.literal("Waypoints (" + wps.size() + "):").formatted(Formatting.WHITE));
                    var client = MinecraftClient.getInstance();
                    var pp = client.player != null ? client.player.getBlockPos() : BlockPos.ORIGIN;
                    for (int i = 0; i < wps.size(); i++) {
                        var wp = wps.get(i);
                        int dist = (int) Math.sqrt(pp.getSquaredDistance(wp.pos()));
                        String dir = cardinalDirection(pp, wp.pos());
                        Text remove = Text.literal(" [X]").styled(s ->
                            s.withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/seedfinder remove " + i))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Remove"))));
                        Text tp = Text.literal(" [TP]").styled(s ->
                            s.withColor(Formatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + wp.pos().getX() + " ~ " + wp.pos().getZ()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Teleport"))));
                        ctx.getSource().sendFeedback(Text.literal(
                            i + ". " + wp.label() + " X:" + wp.pos().getX() + " Z:" + wp.pos().getZ()
                            + " " + dist + "m " + dir).append(remove).append(tp));
                    }
                    return 1;
                }))
                // ─── remove <index|all> ───────────────────────────────
                .then(ClientCommandManager.literal("remove")
                    .then(ClientCommandManager.argument("index", StringArgumentType.word()).executes(ctx -> {
                        String arg = ctx.getArgument("index", String.class);
                        if (arg.equalsIgnoreCase("all")) {
                            WaypointStore.clear();
                            ctx.getSource().sendFeedback(Text.literal("All waypoints removed."));
                            return 1;
                        }
                        try {
                            int idx = Integer.parseInt(arg);
                            var snap = WaypointStore.snapshot();
                            if (idx >= 0 && idx < snap.size()) {
                                WaypointStore.remove(idx);
                                ctx.getSource().sendFeedback(Text.literal("Removed waypoint " + idx + "."));
                            } else {
                                ctx.getSource().sendFeedback(Text.literal("Invalid index. Use /seedfinder list to see indices.")
                                    .formatted(Formatting.RED));
                            }
                        } catch (NumberFormatException e) {
                            ctx.getSource().sendFeedback(Text.literal("Use a number or \"all\".").formatted(Formatting.RED));
                        }
                        return 1;
                    })))
                // ─── tp <index> ───────────────────────────────────────
                .then(ClientCommandManager.literal("tp")
                    .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
                        var client = MinecraftClient.getInstance();
                        if (client.player == null || !client.player.isCreative()) {
                            ctx.getSource().sendFeedback(Text.literal("Creative mode required.").formatted(Formatting.RED));
                            return 1;
                        }
                        int idx = IntegerArgumentType.getInteger(ctx, "index");
                        var snap = WaypointStore.snapshot();
                        if (idx >= 0 && idx < snap.size()) {
                            var pos = snap.get(idx).pos();
                            client.player.networkHandler.sendCommand("tp @p " + pos.getX() + " ~ " + pos.getZ());
                            ctx.getSource().sendFeedback(Text.literal("Teleported to " + snap.get(idx).label() + "."));
                        } else {
                            ctx.getSource().sendFeedback(Text.literal("Invalid index.").formatted(Formatting.RED));
                        }
                        return 1;
                    })))
                // ─── export ───────────────────────────────────────────
                .then(ClientCommandManager.literal("export").executes(ctx -> {
                    var wps = WaypointStore.snapshot();
                    if (wps.isEmpty()) {
                        ctx.getSource().sendFeedback(Text.literal("No waypoints to export.").formatted(Formatting.GRAY));
                        return 1;
                    }
                    StringBuilder sb = new StringBuilder();
                    for (var wp : wps) {
                        if (sb.length() > 0) sb.append(" | ");
                        sb.append("\"").append(wp.label()).append("\" at X:").append(wp.pos().getX())
                            .append(" Z:").append(wp.pos().getZ());
                    }
                    ctx.getSource().sendFeedback(Text.literal(sb.toString()));
                    return 1;
                }))
                // ─── config radius <value> ───────────────────────────
                .then(ClientCommandManager.literal("config")
                    .then(ClientCommandManager.literal("radius")
                        .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(100, 10000)).executes(ctx -> {
                            int r = IntegerArgumentType.getInteger(ctx, "value");
                            SeedFinderConfig.setSearchRadius(r);
                            ctx.getSource().sendFeedback(Text.literal("Search radius set to " + r + " chunks."));
                            return 1;
                        }))))
            );
        });
    }

    private static int findStructure(com.mojang.brigadier.CommandContext<FabricClientCommandSource> ctx,
                                      String name, int radiusChunks, boolean random) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return 0;

        StructureType type = StructurePickerScreen.resolveStructure(name);
        if (type == null) {
            ctx.getSource().sendFeedback(Text.literal("Unknown structure \"" + name + "\"." +
                " Try: ac, tc, po, vil, om, wm, nf, br, ec, sh, pyramid, jungle, igloo, hut, shipwreck, portal")
                .formatted(Formatting.RED));
            return 0;
        }

        Long seedObj = SeedFinderConfig.getSeed();
        if (seedObj == null) {
            ctx.getSource().sendFeedback(Text.literal("No seed set. Use /seedfinder seed <value> or set in GUI.")
                .formatted(Formatting.RED));
            return 0;
        }
        long seed = seedObj;
        int px = client.player.getBlockX();
        int pz = client.player.getBlockZ();

        ctx.getSource().sendFeedback(Text.literal("Searching for " + type.displayName + "...").formatted(Formatting.GRAY));

        if (random) {
            var all = StructureFinder.allWithin(seed, type, px, pz, radiusChunks);
            if (all.isEmpty()) {
                ctx.getSource().sendFeedback(Text.literal("No " + type.displayName + " found within " + radiusChunks + " chunks.")
                    .formatted(Formatting.RED));
                return 1;
            }
            BlockPos pos = all.get(RAND.nextInt(all.size()));
            int dist = (int) Math.sqrt(client.player.getBlockPos().getSquaredDistance(pos));
            String dir = cardinalDirection(client.player.getBlockPos(), pos);
            int color = 0xFFFFAA00;
            WaypointStore.add(new WaypointStore.Waypoint(type.displayName, pos, color));
            Text tp = Text.literal(" [TP]").styled(s ->
                s.withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + pos.getX() + " ~ " + pos.getZ()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Teleport"))));
            ctx.getSource().sendFeedback(Text.literal(
                "Random " + type.displayName + " at X:" + pos.getX() + " Z:" + pos.getZ()
                + " (" + dist + "m " + dir + ")").append(tp));
            return 1;
        }

        // nearest
        BlockPos found = StructureFinder.nearest(seed, type, px, pz, radiusChunks);
        if (found == null) {
            ctx.getSource().sendFeedback(Text.literal("No " + type.displayName + " found within " + radiusChunks + " chunks.")
                .formatted(Formatting.RED));
            return 1;
        }
        int dist = (int) Math.sqrt(client.player.getBlockPos().getSquaredDistance(found));
        String dir = cardinalDirection(client.player.getBlockPos(), found);
        int color = 0xFFFFAA00;
        WaypointStore.add(new WaypointStore.Waypoint(type.displayName, found, color));

        Text tp = Text.literal(" [TP]").styled(s ->
            s.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @p " + found.getX() + " ~ " + found.getZ()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Teleport"))));
        ctx.getSource().sendFeedback(Text.literal(
            "Nearest " + type.displayName + " at X:" + found.getX() + " Z:" + found.getZ()
            + " (" + dist + "m " + dir + ")").append(tp));

        // List other instances
        var all = new ArrayList<>(StructureFinder.allWithin(seed, type, px, pz, radiusChunks));
        all.sort(java.util.Comparator.comparingDouble(p -> p.getSquaredDistance(client.player.getBlockPos())));
        if (all.size() > 1) {
            StringBuilder sb = new StringBuilder("Other instances (" + all.size() + " total): ");
            boolean first = true;
            for (BlockPos p : all) {
                if (p.equals(found)) continue;
                if (!first) sb.append(", ");
                first = false;
                int d = (int) Math.sqrt(client.player.getBlockPos().getSquaredDistance(p));
                sb.append("X:").append(p.getX()).append(" Z:").append(p.getZ()).append(" (").append(d).append("m)");
                if (sb.length() > 200) { sb.append("..."); break; }
            }
            ctx.getSource().sendFeedback(Text.literal(sb.toString()).formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static String cardinalDirection(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        String[] dirs = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        return dirs[(int) Math.round(angle / 45.0) % 8];
    }
}
