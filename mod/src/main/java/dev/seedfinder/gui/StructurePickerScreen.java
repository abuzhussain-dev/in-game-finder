package dev.seedfinder.gui;

import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.finder.StructureFinder;
import dev.seedfinder.finder.StructureType;
import dev.seedfinder.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class StructurePickerScreen extends Screen {
    private static final int[] COLORS = {
        0xFFFFAA00, 0xFF00FFAA, 0xFFAA00FF, 0xFFFF3355, 0xFF33CCFF,
        0xFFFFFF55, 0xFF55FF55, 0xFFFF55FF, 0xFFAAAAAA, 0xFFFF9900
    };
    private static int colorIdx = 0;

    private TextFieldWidget searchField;
    private List<StructureType> filteredTypes;

    public StructurePickerScreen() {
        super(Text.translatable("seedfinder.screen.title"));
        filteredTypes = new ArrayList<>();
    }

    private void rebuildFilter(String query) {
        filteredTypes.clear();
        StructureType[] all = StructureType.values();
        if (query.isEmpty()) {
            for (StructureType t : all) filteredTypes.add(t);
            return;
        }
        String q = query.toLowerCase();
        for (StructureType t : all) {
            if (t.displayName.toLowerCase().contains(q)) {
                filteredTypes.add(t);
            }
        }
    }

    @Override
    protected void init() {
        filteredTypes.clear();
        for (StructureType t : StructureType.values()) filteredTypes.add(t);

        searchField = new TextFieldWidget(textRenderer, this.width / 2 - 80, 20, 160, 18,
            Text.translatable("seedfinder.screen.search"));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        rebuildButtons();
    }

    private void onSearchChanged(String query) {
        rebuildFilter(query);
        rebuildButtons();
    }

    private void rebuildButtons() {
        // Remove old buttons (all children after the search field)
        var toRemove = new ArrayList<>(children());
        toRemove.remove(searchField);
        for (var child : toRemove) remove(child);

        int perRow = 3;
        int btnW = 160, btnH = 20, gap = 4;
        int totalW = perRow * btnW + (perRow - 1) * gap;
        int startX = (this.width - totalW) / 2;
        int startY = 60;

        for (int i = 0; i < filteredTypes.size(); i++) {
            StructureType t = filteredTypes.get(i);
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

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("seedfinder.screen.close"), b -> {
            close();
        }).dimensions(this.width / 2 + 5, bottomY, 160, 20).build());
    }

    private void pick(StructureType type) {
        long seed = SeedFinderConfig.getSeed();
        if (seed == Long.MIN_VALUE) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.translatable("seedfinder.msg.no_seed").formatted(Formatting.RED), false);
            return;
        }
        BlockPos found = StructureFinder.nearest(seed, type,
            MinecraftClient.getInstance().player.getBlockX(),
            MinecraftClient.getInstance().player.getBlockZ(), 200);
        if (found == null) {
            MinecraftClient.getInstance().player.sendMessage(
                Text.translatable("seedfinder.msg.not_found", type.displayName).formatted(Formatting.RED), false);
            return;
        }
        int dist = (int) Math.sqrt(found.getSquaredDistance(
            MinecraftClient.getInstance().player.getBlockPos()));
        String dir = cardinalDirection(
            MinecraftClient.getInstance().player.getBlockPos(), found);
        MinecraftClient.getInstance().player.sendMessage(
            Text.translatable("seedfinder.msg.found", type.displayName, found.getX(), found.getZ(), dist, dir), false);

        int color = COLORS[colorIdx % COLORS.length];
        colorIdx++;
        WaypointStore.add(new WaypointStore.Waypoint(type.displayName, found, color));
        close();
    }

    private static String cardinalDirection(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        String[] dirs = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        return dirs[(int) Math.round(angle / 45.0) % 8];
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        searchField.render(ctx, mouseX, mouseY, delta);
    }
}
