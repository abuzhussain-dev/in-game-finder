package dev.seedfinder.gui;

import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.finder.StructureFinder;
import dev.seedfinder.finder.StructureType;
import dev.seedfinder.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class StructurePickerScreen extends Screen {
    private static final int[] COLORS = {
        0xFFFFAA00, 0xFF00FFAA, 0xFFAA00FF, 0xFFFF3355, 0xFF33CCFF,
        0xFFFFFF55, 0xFF55FF55, 0xFFFF55FF, 0xFFAAAAAA, 0xFFFF9900
    };
    private static int colorIdx = 0;

    public StructurePickerScreen() {
        super(Text.translatable("seedfinder.screen.title"));
    }

    @Override
    protected void init() {
        StructureType[] types = StructureType.values();
        int perRow = 3;
        int btnW = 160, btnH = 20, gap = 4;
        int totalW = perRow * btnW + (perRow - 1) * gap;
        int startX = (this.width - totalW) / 2;
        int startY = 60;

        for (int i = 0; i < types.length; i++) {
            StructureType t = types[i];
            int col = i % perRow;
            int row = i / perRow;
            int x = startX + col * (btnW + gap);
            int y = startY + row * (btnH + gap);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(t.displayName), b -> pick(t))
                .dimensions(x, y, btnW, btnH).build());
        }

        int bottomY = this.height - 30;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("seedfinder.screen.clear_waypoints"), b -> {
            WaypointStore.clear();
        }).dimensions(this.width / 2 - 165, bottomY, 160, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("seedfinder.screen.close"), b -> this.close())
            .dimensions(this.width / 2 + 5, bottomY, 160, 20).build());
    }

    private void pick(StructureType type) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (!SeedFinderConfig.hasSeed()) {
            mc.player.sendMessage(Text.translatable("seedfinder.msg.no_seed").formatted(Formatting.RED), false);
            this.close();
            return;
        }
        long seed = SeedFinderConfig.getSeed();
        int px = (int) mc.player.getX();
        int pz = (int) mc.player.getZ();
        BlockPos found = StructureFinder.nearest(seed, type, px, pz, SeedFinderConfig.getSearchRadiusChunks());
        if (found == null) {
            mc.player.sendMessage(Text.translatable("seedfinder.msg.not_found", type.displayName).formatted(Formatting.YELLOW), false);
            return;
        }
        int dx = found.getX() - px, dz = found.getZ() - pz;
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        String dir = compass(dx, dz);
        mc.player.sendMessage(Text.translatable("seedfinder.msg.found",
            type.displayName, found.getX(), found.getZ(), dist, dir).formatted(Formatting.GREEN), false);
        int color = COLORS[colorIdx++ % COLORS.length];
        WaypointStore.add(new WaypointStore.Waypoint(type.displayName, found, color));
    }

    private static String compass(int dx, int dz) {
        double a = Math.toDegrees(Math.atan2(-dz, dx));
        if (a < 0) a += 360;
        String[] pts = {"E","NE","N","NW","W","SW","S","SE"};
        return pts[(int) Math.round(a / 45.0) % 8];
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        String seedInfo = SeedFinderConfig.hasSeed()
            ? "Seed: " + SeedFinderConfig.getSeed()
            : "No seed set — /seedfinder seed <long>";
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(seedInfo), this.width / 2, 38, 0xAAAAAA);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}