package dev.seedfinder.gui;

import dev.seedfinder.config.SeedFinderConfig;
import dev.seedfinder.finder.StructureFinder;
import dev.seedfinder.finder.StructureType;
import dev.seedfinder.util.TouchUtil;
import dev.seedfinder.waypoint.WaypointStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    private final List<StructureButton> structureButtons = new ArrayList<>();
    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    // Scrolling
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public record SearchResult(StructureType type, BlockPos pos,
                               int color, int distance, String direction) {}
    private final List<SearchResult> results = new ArrayList<>();
    private int resultsScroll = 0;
    private boolean showSeedWarning = false;
    private Set<StructureType> searchingTypes = ConcurrentHashMap.newKeySet();
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_MS = 150;

    public StructurePickerScreen() {
        super(Text.literal("Seed Finder"));
        this.touchDevice = TouchUtil.isTouchDevice();
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

        // Auto-detect + Config buttons
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Auto"), btn -> autoDetectSeed())
            .dimensions(this.width - 52, 2, 48, touchDevice ? 28 : 18)
            .build());
        addDrawableChild(ButtonWidget.builder(
            Text.literal("\u2699"), btn ->
                MinecraftClient.getInstance().setScreen(new SeedFinderConfigScreen(this)))
            .dimensions(this.width - 104, 2, 48, touchDevice ? 28 : 18)
            .tooltip(Tooltip.of(Text.literal("Settings")))
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
                    scrollOffset = 0;
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
            Text.literal("Search..."),
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        // ─── Initial filter + buttons ───────────────────────────
        rebuildFilter("");
        rebuildButtons();

        // ─── Bottom buttons ─────────────────────────────────────
        int bottomY = this.height - (touchDevice ? 48 : 30);
        int btnH = touchDevice ? 36 : 20;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Clear Waypoints"), btn -> {
                WaypointStore.clear(); results.clear();
            }).dimensions(4, bottomY, 160, btnH).tooltip(
                Tooltip.of(Text.literal("Remove all waypoints"))).build());
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Close"),
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
        } else if (client.player != null) {
            client.player.sendMessage(
                Text.literal("Cannot auto-detect seed on multiplayer. Enter seed manually.")
                    .formatted(Formatting.RED), false);
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
        scrollOffset = 0;
        if (now - lastSearchTime > SEARCH_DEBOUNCE_MS) {
            lastSearchTime = now;
            rebuildButtons();
        }
    }

    private void rebuildButtons() {
        int btnH = touchDevice ? 36 : 20;
        int gap = touchDevice ? 5 : 3;
        int listTop = touchDevice ? 76 : 50;
        int perRow = touchDevice ? 1 : 2;
        int btnW = (this.width / perRow) - (perRow + 1) * gap;
        int totalRows = (filteredTypes.size() + perRow - 1) / perRow;
        int totalContentH = totalRows * (btnH + gap);

        // Available height for the button area: from listTop to clear-waypoints button
        int bottomY = this.height - (touchDevice ? 48 : 30);
        int availableH = bottomY - listTop - gap;
        maxScroll = Math.max(0, totalContentH - availableH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Remove old buttons
        while (structureButtons.size() > filteredTypes.size()) {
            remove(structureButtons.remove(structureButtons.size() - 1));
        }

        // Waypoints snapshot for indicator
        var waypoints = WaypointStore.snapshot();

        for (int i = 0; i < filteredTypes.size(); i++) {
            StructureType t = filteredTypes.get(i);
            int col = i % perRow, row = i / perRow;
            int x = gap + col * (btnW + gap);
            int y = listTop + row * (btnH + gap) - scrollOffset;

            // Skip off-screen buttons (but still create for others)
            boolean waypointed = waypoints.stream().anyMatch(w -> w.label().equals(t.displayName));
            String label = (waypointed ? "\u2713 " : "") + t.displayName
                + (searchingTypes.contains(t) ? " ..." : "");
            var btn = ButtonWidget.builder(
                Text.literal(label), b -> pick(t))
                .dimensions(x, y, btnW, btnH)
                .tooltip(Tooltip.of(Text.literal(
                    String.format("%s | Spacing: %d, Separation: %d | %s",
                        t.displayName, t.spacing, t.separation, t.dimension))))
                .build();
            // Auto-focus if this is the only result
            if (filteredTypes.size() == 1) btn.setFocused(true);

            if (i < structureButtons.size()) {
                remove(structureButtons.get(i));
                var old = structureButtons.get(i);
                old.setPosition(x, y);
                structureButtons.set(i, btn);
            } else {
                structureButtons.add(new StructureButton(btn, t));
            }
            addDrawableChild(btn);
        }
    }

    /** Wrapper pairing button with its type so we can show waypoint status. */
    private record StructureButton(ButtonWidget button, StructureType type) {}

    private void pick(StructureType type) {
        if (searchingTypes.contains(type)) return;
        Long seedObj = SeedFinderConfig.getSeed();
        if (seedObj == null) { showSeedWarning = true; return; }
        long seed = seedObj;
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int px = client.player.getBlockX();
        int pz = client.player.getBlockZ();
        int radius = SeedFinderConfig.getSearchRadiusChunks();

        searchingTypes.add(type);
        rebuildButtons();

        CompletableFuture.supplyAsync(() ->
            StructureFinder.nearest(seed, type, px, pz, radius)
        ).thenAcceptAsync(found -> {
            if (client.player == null) return;
            searchingTypes.remove(type);
            rebuildButtons();
            if (found == null) {
                client.player.sendMessage(
                    Text.literal("%s not found".formatted(type.displayName))
                        .formatted(Formatting.RED), false);
                return;
            }
            int dist = (int) Math.sqrt(found.getSquaredDistance(client.player.getBlockPos()));
            String dir = cardinalDirection(client.player.getBlockPos(), found);
            int color = COLORS[type.ordinal() % COLORS.length];
            WaypointStore.add(new WaypointStore.Waypoint(type.displayName, found, color));
            results.clear();
            results.add(new SearchResult(type, found, color, dist, dir));
            // Also show other instances (sorted by distance)
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
                Text.literal("Found %s at X:%d Z:%d %dm %s".formatted(
                    type.displayName, found.getX(), found.getZ(), dist, dir)), false);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // Scroll structure list if mouse is in the left area
        int listTop = touchDevice ? 76 : 50;
        int bottomY = this.height - (touchDevice ? 48 : 30);
        if (mouseY >= listTop && mouseY <= bottomY && mouseX < this.width * 0.5) {
            scrollOffset = Math.clamp(scrollOffset - (int)(vertical * 20), 0, maxScroll);
            rebuildButtons();
            return true;
        }
        // Scroll results panel if mouse is in results area
        if (results.size() > 0) {
            int px = touchDevice ? 4 : (int)(this.width * 0.55);
            int py = touchDevice ? this.height - 170 : 50;
            int ph = results.size() * 20 + 30;
            if (mouseX >= px && mouseX <= px + (touchDevice ? this.width - 8 : this.width - px - 4)
                && mouseY >= py) {
                resultsScroll = Math.clamp(resultsScroll - (int)(vertical * 20), 0,
                    Math.max(0, results.size() * 20 - (ph - 30)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
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
            int py = touchDevice ? this.height - 170 : 50;
            int ph = Math.min(results.size() * 20 + 30, this.height - py - (touchDevice ? 48 : 30));
            ctx.fill(px, py, px + pw, py + ph, 0xCC000000);
            ctx.drawText(tr, "Results (" + results.size() + ")", px + 6, py + 4, 0xFFFFFF, true);
            int ey = py + 20;
            int visibleStart = Math.max(0, resultsScroll / 20);
            int visibleEnd = Math.min(results.size(), visibleStart + (ph - 30) / 20);
            for (int i = visibleStart; i < visibleEnd; i++) {
                SearchResult r = results.get(i);
                int ry = ey + (i - visibleStart) * 20;
                ctx.fill(px + 8, ry + 2, px + 16, ry + 10, r.color());
                ctx.drawText(tr, r.type().displayName, px + 22, ry, 0xFFFFFF, true);
                ctx.drawText(tr,
                    "X:" + r.pos().getX() + " Z:" + r.pos().getZ() + "  "
                    + r.distance() + "m " + r.direction(), px + 22, ry + 10, 0xAAAAAA, false);
                ctx.drawText(tr, "[X]", px + pw - 24, ry, 0xFF5555, true);
            }
            // Scroll indicator
            if (results.size() > visibleEnd - visibleStart) {
                ctx.drawText(tr, "\u25BC scroll \u25B2", px + pw / 2 - 30, py + ph - 12, 0x888888, true);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() == 0 && !results.isEmpty()) {
            int px = touchDevice ? 4 : (int)(this.width * 0.55);
            int pw = touchDevice ? this.width - 8 : this.width - px - 4;
            int py = touchDevice ? this.height - 170 : 50;
            int ph = Math.min(results.size() * 20 + 30, this.height - py - (touchDevice ? 48 : 30));
            int visibleStart = Math.max(0, resultsScroll / 20);
            int visibleEnd = Math.min(results.size(), visibleStart + (ph - 30) / 20);
            double mx = click.x();
            double my = click.y();
            for (int i = visibleStart; i < visibleEnd; i++) {
                int ey = py + 20 + (i - visibleStart) * 20;
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
