package dev.seedfinder.gui;

import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.finder.StructureFinder;
import dev.seedfinder.finder.StructureType;
import dev.seedfinder.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure picker screen with seed input, dimension tabs, search, and async search.
 * Touch-adapted for Zalith Launcher 2 (single-column, larger buttons).
 */
public class StructurePickerScreen extends Screen {

    private static final int[] COLORS = {
        0xFFFFAA00, 0xFF00FFAA, 0xFFAA00FF, 0xFFFF3355, 0xFF33CCFF,
        0xFFFFFF55, 0xFF55FF55, 0xFFFF55FF, 0xFFAAAAAA, 0xFFFF9900
    };

    // ─── Alias map ───────────────────────────────────────────────
    private static final Map<String, StructureType> ALIAS_MAP = new HashMap<>();
    static {
        String[][] aliases = {
            {"ac","ancient","city"}, {"tc","trial","chamber"},
            {"po","outpost","pillager"}, {"vil","village"},
            {"om","monument","ocean"}, {"wm","mansion","woodland"},
            {"nf","fortress"}, {"br","bastion"},
            {"ec","endcity"}, {"sh","stronghold"},
            {"pyramid","desert"}, {"jungle","temple"},
            {"igloo"}, {"hut","swamp"},
            {"shipwreck","ship"}, {"portal","ruined"},
        };
        StructureType[] types = {
            StructureType.ANCIENT_CITY, StructureType.TRIAL_CHAMBERS,
            StructureType.PILLAGER_OUTPOST, StructureType.VILLAGE,
            StructureType.OCEAN_MONUMENT, StructureType.WOODLAND_MANSION,
            StructureType.NETHER_FORTRESS, StructureType.BASTION_REMNANT,
            StructureType.END_CITY, StructureType.STRONGHOLD,
            StructureType.DESERT_PYRAMID, StructureType.JUNGLE_TEMPLE,
            StructureType.IGLOO, StructureType.SWAMP_HUT,
            StructureType.SHIPWRECK, StructureType.RUINED_PORTAL,
        };
        for (int i = 0; i < aliases.length; i++) {
            for (String a : aliases[i]) ALIAS_MAP.put(a, types[i]);
        }
    }

    // ─── Tabs ────────────────────────────────────────────────────
    private enum Tab { OVERWORLD, NETHER, END, ALL }
    private Tab activeTab = Tab.OVERWORLD;

    private static Tab getTab(StructureType t) {
        return switch (t.dimension) {
            case NETHER -> Tab.NETHER;
            case END -> Tab.END;
            default -> Tab.OVERWORLD;
        };
    }

    // ─── GUI State ───────────────────────────────────────────────
    private final boolean touchDevice;
    private TextFieldWidget seedField;
    private TextFieldWidget searchField;
    private List<StructureType> filteredTypes = new ArrayList<>();
    private final List<ButtonWidget> structureButtons = new ArrayList<>();
    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    public record SearchResult(StructureType type, BlockPos pos,
                               int color, int distance, String direction) {}
    private final List<SearchResult> results = new ArrayList<>();
    private boolean showSeedWarning = false;
    private Set<StructureType> searchingTypes = ConcurrentHashMap.newKeySet();
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_MS = 150;

    public StructurePickerScreen() {
        super(Text.translatable("seedfinder.screen.title"));
        this.touchDevice = detectTouch();
        filteredTypes = new ArrayList<>();
    }

    private static boolean detectTouch() {
        try {
            return MinecraftClient.getInstance().getWindow().getWidth() < 800;
        } catch (Exception e) { return false; }
    }

    @Override
    protected void init() {
        // ─── Seed field ─────────────────────────────────────────
        Long currentSeed = SeedFinderConfig.getSeed();
        String seedText = currentSeed != null ? Long.toString(currentSeed) : "";
        int seedW = touchDevice ? 180 : 130;
        seedField = new TextFieldWidget(textRenderer,
            this.width - seedW - 90, 2, seedW, touchDevice ? 28 : 18,
            Text.literal("Seed"));
        seedField.setText(seedText);
        seedField.setChangedListener(this::onSeedChanged);
        addDrawableChild(seedField);

        // Auto-detect button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Auto"), btn -> autoDetectSeed())
            .dimensions(this.width - 52, 2, 48, touchDevice ? 28 : 18)
            .build());

        showSeedWarning = !SeedFinderConfig.hasSeed();

        // ─── Tab buttons ────────────────────────────────────────
        int tabY = touchDevice ? 36 : 24;
        int tabX = 4;
        int tabW = touchDevice ? 80 : 60;
        int tabH = touchDevice ? 32 : 20;
        tabButtons.clear();
        for (Tab tab : Tab.values()) {
            final Tab t = tab;
            var btn = ButtonWidget.builder(
                Text.literal(tab.name()), b -> {
                    activeTab = t;
                    rebuildFilter(searchField.getText());
                    rebuildButtons();
                }).dimensions(tabX, tabY, tabW, tabH).build();
            tabButtons.add(btn);
            addDrawableChild(btn);
            tabX += tabW + 2;
        }

        // ─── Search field ───────────────────────────────────────
        int searchX = tabX + 4;
        int searchW = this.width - searchX - 4;
        searchField = new TextFieldWidget(textRenderer, searchX, tabY,
            searchW, touchDevice ? 28 : 18,
            Text.translatable("seedfinder.screen.search"));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        // ─── Initial filter + buttons ───────────────────────────
        rebuildFilter("");
        rebuildButtons();

        // ─── Bottom buttons ─────────────────────────────────────
        int bottomY = this.height - (touchDevice ? 48 : 30);
        int btnH = touchDevice ? 36 : 20;
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("seedfinder.screen.clear_waypoints"), btn -> {
                WaypointStore.clear(); results.clear();
            }).dimensions(4, bottomY, 160, btnH).build());
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("seedfinder.screen.close"),
            btn -> close())
            .dimensions(this.width - 164, bottomY, 160, btnH).build());
    }

    @Override
    public void close() {
        SeedFinderConfig.save();
        super.close();
    }

    private void autoDetectSeed() {
        var client = MinecraftClient.getInstance();
        var server = client.getServer();
        if (server != null) {
            long worldSeed = server.getSaveProperties().getGeneratorOptions().getSeed();
            if (worldSeed != 0 && worldSeed != -1) {
                SeedFinderConfig.setSeed(worldSeed);
                seedField.setText(Long.toString(worldSeed));
                showSeedWarning = false;
            }
        }
    }

    private void onSeedChanged(String text) {
        try {
            long s = Long.parseLong(text.trim());
            SeedFinderConfig.setSeedMem(s);
            showSeedWarning = false;
        } catch (NumberFormatException e) {
            showSeedWarning = text.trim().isEmpty();
        }
    }

    private void rebuildFilter(String query) {
        filteredTypes.clear();
        String q = query.toLowerCase().trim();
        for (StructureType t : StructureType.values()) {
            if (!t.isSearchable()) continue;
            if (activeTab != Tab.ALL && getTab(t) != activeTab) continue;
            if (q.isEmpty()) { filteredTypes.add(t); continue; }
            boolean matches = t.displayName.toLowerCase().contains(q)
                || t.name().toLowerCase().contains(q);
            if (!matches) {
                for (Map.Entry<String, StructureType> e : ALIAS_MAP.entrySet()) {
                    if (e.getValue() == t && e.getKey().startsWith(q)) { matches = true; break; }
                }
            }
            if (matches) filteredTypes.add(t);
        }
    }

    private void onSearchChanged(String query) {
        long now = System.nanoTime() / 1_000_000;
        rebuildFilter(query);
        if (now - lastSearchTime > SEARCH_DEBOUNCE_MS) {
            lastSearchTime = now;
            rebuildButtons();
        }
    }

    private void rebuildButtons() {
        while (structureButtons.size() > filteredTypes.size()) {
            remove(structureButtons.remove(structureButtons.size() - 1));
        }
        int btnH = touchDevice ? 36 : 20;
        int gap = touchDevice ? 5 : 3;
        int startY = touchDevice ? 76 : 50;
        int perRow = touchDevice ? 1 : 2;
        int btnW = (this.width / perRow) - (perRow + 1) * gap;
        for (int i = 0; i < filteredTypes.size(); i++) {
            StructureType t = filteredTypes.get(i);
            int col = i % perRow, row = i / perRow;
            int x = gap + col * (btnW + gap), y = startY + row * (btnH + gap);
            String label = t.displayName + (searchingTypes.contains(t) ? " ..." : "");
            var btn = ButtonWidget.builder(
                Text.literal(label), b -> pick(t))
                .dimensions(x, y, btnW, btnH).build();
            if (i < structureButtons.size()) {
                remove(structureButtons.get(i));
                structureButtons.set(i, btn);
            } else {
                structureButtons.add(btn);
            }
            addDrawableChild(btn);
        }
    }

    private void pick(StructureType type) {
        if (searchingTypes.contains(type)) return;
        Long seedObj = SeedFinderConfig.getSeed();
        if (seedObj == null) { showSeedWarning = true; return; }
        long seed = seedObj;
        var client = MinecraftClient.getInstance();
        int px = client.player.getBlockX();
        int pz = client.player.getBlockZ();
        int radius = SeedFinderConfig.getSearchRadiusChunks();

        searchingTypes.add(type);
        rebuildButtons();

        CompletableFuture.supplyAsync(() ->
            StructureFinder.nearest(seed, type, px, pz, radius)
        ).thenAcceptAsync(found -> {
            searchingTypes.remove(type);
            rebuildButtons();
            if (found == null) {
                client.player.sendMessage(
                    Text.translatable("seedfinder.msg.not_found", type.displayName)
                        .formatted(Formatting.RED), false);
                return;
            }
            int dist = (int) Math.sqrt(found.getSquaredDistance(client.player.getBlockPos()));
            String dir = cardinalDirection(client.player.getBlockPos(), found);
            int color = COLORS[type.ordinal() % COLORS.length];
            WaypointStore.add(new WaypointStore.Waypoint(type.displayName, found, color));
            results.clear();
            results.add(new SearchResult(type, found, color, dist, dir));
            // Also show other instances of this type (sorted by distance)
            var all = new java.util.ArrayList<>(StructureFinder.allWithin(seed, type, px, pz, radius));
            all.sort(java.util.Comparator.comparingDouble(
                p -> p.getSquaredDistance(client.player.getBlockPos())));
            for (BlockPos pos : all) {
                if (pos.equals(found) || results.size() >= 20) continue;
                int d = (int) Math.sqrt(pos.getSquaredDistance(client.player.getBlockPos()));
                results.add(new SearchResult(type, pos, color, d,
                    cardinalDirection(client.player.getBlockPos(), pos)));
            }
            rebuildButtons();
            client.player.sendMessage(
                Text.translatable("seedfinder.msg.found",
                    type.displayName, found.getX(), found.getZ(), dist, dir), false);
        }, client);
    }

    public void removeResult(int index) {
        if (index >= 0 && index < results.size()) {
            var r = results.remove(index);
            var waypoints = new ArrayList<>(WaypointStore.snapshot());
            for (int i = waypoints.size() - 1; i >= 0; i--) {
                if (waypoints.get(i).pos().equals(r.pos())) {
                    WaypointStore.remove(i); break;
                }
            }
        }
    }

    @Override
    public void tick() {
        if (System.nanoTime() / 1_000_000 - lastSearchTime > SEARCH_DEBOUNCE_MS) {
            rebuildButtons();
            lastSearchTime = Long.MAX_VALUE;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var tr = MinecraftClient.getInstance().textRenderer;

        // Seed warning banner
        if (showSeedWarning) {
            int wy = touchDevice ? 30 : 22;
            ctx.fill(0, wy, this.width, wy + 16, 0xAAFFAA00);
            String warn = "No seed set! Enter a seed above or press [Auto] in a world.";
            int tw = tr.getWidth(warn);
            ctx.drawText(tr, warn, (this.width - tw) / 2, wy + 3, 0x000000, true);
        }

        // Results panel
        if (!results.isEmpty()) {
            int px = touchDevice ? 4 : (int)(this.width * 0.55);
            int pw = touchDevice ? this.width - 8 : this.width - px - 4;
            int py = touchDevice ? this.height - 110 : 50;
            int ph = touchDevice ? 60 : this.height - 80;
            ctx.fill(px, py, px + pw, py + ph, 0xCC000000);
            ctx.drawText(tr, "Results (" + results.size() + ")", px + 6, py + 4, 0xFFFFFF, true);
            int ey = py + 20;
            int max = touchDevice ? 2 : (ph - 30) / 38;
            for (int i = 0; i < Math.min(results.size(), max); i++) {
                SearchResult r = results.get(i);
                ctx.fill(px + 8, ey + 2, px + 16, ey + 10, r.color());
                ctx.drawText(tr, r.type().displayName, px + 22, ey, 0xFFFFFF, true);
                ctx.drawText(tr,
                    "X:" + r.pos().getX() + " Z:" + r.pos().getZ() + "  "
                    + r.distance() + "m " + r.direction(), px + 22, ey + 12, 0xAAAAAA, false);
                ctx.drawText(tr, "[X]", px + pw - 24, ey, 0xFF5555, true);
                ey += 38;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() == 0 && !results.isEmpty()) {
            int px = touchDevice ? 4 : (int)(this.width * 0.55);
            int pw = touchDevice ? this.width - 8 : this.width - px - 4;
            int py = touchDevice ? this.height - 110 : 50;
            int ph = touchDevice ? 60 : this.height - 80;
            int max = Math.min(results.size(), touchDevice ? 2 : (ph - 30) / 38);
            double mx = click.x();
            double my = click.y();
            for (int i = 0; i < max; i++) {
                int ey = py + 20 + i * 38;
                int xBtn = px + pw - 24;
                if (mx >= xBtn && mx <= xBtn + 12 && my >= ey && my <= ey + 12) {
                    removeResult(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
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
