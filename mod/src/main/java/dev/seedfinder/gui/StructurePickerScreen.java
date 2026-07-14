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
            {"ac", "ancient", "city"}, {"tc", "trial", "chamber"},
            {"po", "outpost", "pillager"}, {"vil", "village"},
            {"om", "monument", "ocean"}, {"wm", "mansion", "woodland"},
            {"nf", "fortress"}, {"br", "bastion"},
            {"ec", "endcity"}, {"sh", "stronghold"},
            {"pyramid", "desert"}, {"jungle", "temple"},
            {"igloo"}, {"hut", "swamp"},
            {"shipwreck", "ship"}, {"portal", "ruined"},
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

    /** Resolve a structure name or alias to a StructureType, or null. */
    public static StructureType resolveStructure(String input) {
        String lower = input.toLowerCase().trim();
        StructureType alias = ALIAS_MAP.get(lower);
        if (alias != null) return alias;
        for (StructureType t : StructureType.values()) {
            if (t.name().equalsIgnoreCase(lower)) return t;
        }
        for (StructureType t : StructureType.values()) {
            if (t.displayName.equalsIgnoreCase(lower)) return t;
        }
        return null;
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

    // ─── View / Sort ────────────────────────────────────────────
    private enum ViewMode { LIST, MAP }
    private ViewMode viewMode = ViewMode.LIST;

    private enum SortMode { NEAREST, TYPE, DIMENSION }
    private SortMode sortMode = SortMode.NEAREST;

    private static final int[] RADIUS_PRESETS = {500, 1000, 2000, 5000};
    private static final int MAX_FIND_ALL_TOTAL = 1000;
    private static final int MAX_FIND_ALL_PER_TYPE = 150;
    private static final int MAX_PICK_RESULTS = 200;

    // ─── GUI State ───────────────────────────────────────────────
    private final boolean touchDevice;
    private TextFieldWidget seedField;
    private TextFieldWidget searchField;
    private List<StructureType> filteredTypes = new ArrayList<>();
    private final List<StructureButton> structureButtons = new ArrayList<>();
    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    // Scrolling (structure list)
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Results
    public record SearchResult(StructureType type, BlockPos pos,
                               int color, int distance, String direction) {}
    private final List<SearchResult> results = new ArrayList<>();
    private int resultsScroll = 0;
    private boolean showSeedWarning = false;
    private Set<StructureType> searchingTypes = ConcurrentHashMap.newKeySet();
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_MS = 150;

    // Find All
    private boolean isSearchingAll = false;
    private final Set<StructureType> searchingAllTypes = ConcurrentHashMap.newKeySet();

    // Map view
    private double mapScale = 1.0;
    private int mapHoveredIndex = -1;
    private static final double MIN_MAP_SCALE = 0.25;
    private static final double MAX_MAP_SCALE = 20.0;

    // Buttons
    private ButtonWidget findAllBtn;

    // Clipboard toast
    private long clipboardToastUntil = 0;
    private String clipboardToastText = "";

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
                MinecraftClient.getInstance().setScreen(SeedFinderConfigScreen.create(this)))
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
            Text.literal("Search..."));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        // ─── Initial filter + buttons ───────────────────────────
        rebuildFilter("");
        rebuildButtons();

        // ─── Bottom bar ─────────────────────────────────────────
        int bottomY = this.height - (touchDevice ? 48 : 30);
        int btnH = touchDevice ? 36 : 20;
        int rx = 4;

        int radiusVal = SeedFinderConfig.getSearchRadiusChunks();
        findAllBtn = addDrawableChild(ButtonWidget.builder(
            Text.literal("Find All (" + radiusVal + " ch)"),
            btn -> findAllInRadius())
            .dimensions(rx, bottomY, touchDevice ? 130 : 110, btnH)
            .tooltip(Tooltip.of(Text.literal("Search all structure types within radius")))
            .build());
        rx += (touchDevice ? 134 : 114);

        // Radius presets (inline small buttons)
        for (int r : RADIUS_PRESETS) {
            int pr = r;
            addDrawableChild(ButtonWidget.builder(
                Text.literal(r >= 1000 ? (r / 1000 + "k") : String.valueOf(r)),
                btn -> {
                    SeedFinderConfig.setSearchRadius(pr);
                    findAllBtn.setMessage(Text.literal("Find All (" + pr + " ch)"));
                })
                .dimensions(rx, bottomY, touchDevice ? 48 : 34, btnH)
                .tooltip(Tooltip.of(Text.literal("Set search radius to " + r + " chunks")))
                .build());
            rx += (touchDevice ? 52 : 38);
        }

        // Clear Waypoints
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Clear"), btn -> {
                WaypointStore.clear();
                results.clear();
            })
            .dimensions(rx, bottomY, touchDevice ? 72 : 50, btnH)
            .tooltip(Tooltip.of(Text.literal("Remove all waypoints and results")))
            .build());
        rx += (touchDevice ? 76 : 54);

        // Close
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Close"), btn -> close())
            .dimensions(rx, bottomY, touchDevice ? 72 : 50, btnH)
            .build());
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
                    if (e.getValue() == t && e.getKey().startsWith(q)) {
                        matches = true;
                        break;
                    }
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

        int bottomY = this.height - (touchDevice ? 48 : 30);
        int availableH = bottomY - listTop - gap;
        maxScroll = Math.max(0, totalContentH - availableH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        while (structureButtons.size() > filteredTypes.size()) {
            remove(structureButtons.remove(structureButtons.size() - 1).button());
        }

        var waypoints = WaypointStore.snapshot();

        for (int i = 0; i < filteredTypes.size(); i++) {
            StructureType t = filteredTypes.get(i);
            int col = i % perRow, row = i / perRow;
            int x = gap + col * (btnW + gap);
            int y = listTop + row * (btnH + gap) - scrollOffset;

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
            if (filteredTypes.size() == 1) btn.setFocused(true);

            if (i < structureButtons.size()) {
                remove(structureButtons.get(i).button());
                structureButtons.set(i, new StructureButton(btn, t));
            } else {
                structureButtons.add(new StructureButton(btn, t));
            }
            addDrawableChild(btn);
        }
    }

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
            if (found == null) {
                client.player.sendMessage(
                    Text.literal("%s not found".formatted(type.displayName))
                        .formatted(Formatting.RED), false);
                rebuildButtons();
                return;
            }
            int dist = (int) Math.sqrt(found.getSquaredDistance(client.player.getBlockPos()));
            String dir = cardinalDirection(client.player.getBlockPos(), found);
            int color = COLORS[type.ordinal() % COLORS.length];
            WaypointStore.add(new WaypointStore.Waypoint(type.displayName, found, color));
            results.clear();
            resultsScroll = 0;
            results.add(new SearchResult(type, found, color, dist, dir));

            // All instances within radius
            var all = new ArrayList<>(StructureFinder.allWithin(seed, type, px, pz, radius));
            all.sort(java.util.Comparator.comparingDouble(
                p -> p.getSquaredDistance(client.player.getBlockPos())));
            int count = 0;
            for (BlockPos pos : all) {
                if (pos.equals(found)) continue;
                if (count++ >= MAX_PICK_RESULTS) break;
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

    // ─── Find All in Radius ──────────────────────────────────────

    private void findAllInRadius() {
        if (isSearchingAll) return;
        Long seedObj = SeedFinderConfig.getSeed();
        if (seedObj == null) { showSeedWarning = true; return; }
        long seed = seedObj;
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int px = client.player.getBlockX();
        int pz = client.player.getBlockZ();
        int radius = SeedFinderConfig.getSearchRadiusChunks();

        results.clear();
        resultsScroll = 0;
        isSearchingAll = true;

        List<StructureType> searchable = new ArrayList<>();
        for (StructureType t : StructureType.values()) {
            if (t.isSearchable()) searchable.add(t);
        }
        searchingAllTypes.clear();
        searchingAllTypes.addAll(searchable);

        for (StructureType t : searchable) {
            int color = COLORS[t.ordinal() % COLORS.length];
            CompletableFuture.supplyAsync(() -> {
                List<BlockPos> positions = StructureFinder.allWithin(seed, t, px, pz, radius);
                positions.sort(java.util.Comparator.comparingDouble(
                    p -> p.getSquaredDistance((double)px, 0.0, (double)pz)));
                int max = Math.min(positions.size(), MAX_FIND_ALL_PER_TYPE);
                return positions.subList(0, max);
            }).thenAcceptAsync(positions -> {
                if (client.player == null) return;
                BlockPos pp = client.player.getBlockPos();
                for (BlockPos pos : positions) {
                    int d = (int) Math.sqrt(pos.getSquaredDistance(pp));
                    results.add(new SearchResult(t, pos, color, d,
                        cardinalDirection(pp, pos)));
                }
                if (results.size() > MAX_FIND_ALL_TOTAL) {
                    results.sort(java.util.Comparator.comparingInt(r -> r.distance()));
                    while (results.size() > MAX_FIND_ALL_TOTAL)
                        results.remove(results.size() - 1);
                }
                reSortResults();
                searchingAllTypes.remove(t);
                if (searchingAllTypes.isEmpty()) isSearchingAll = false;
            }, client);
        }
    }

    // ─── Waypoint toggle ─────────────────────────────────────────

    private boolean isWaypointed(BlockPos pos) {
        return WaypointStore.snapshot().stream().anyMatch(w -> w.pos().equals(pos));
    }

    private void toggleWaypoint(SearchResult r) {
        var waypoints = WaypointStore.snapshot();
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).pos().equals(r.pos())) {
                WaypointStore.remove(i);
                return;
            }
        }
        WaypointStore.add(new WaypointStore.Waypoint(r.type().displayName, r.pos(), r.color()));
    }

    // ─── Clipboard ───────────────────────────────────────────────

    private void copyCoords(SearchResult r) {
        MinecraftClient.getInstance().keyboard.setClipboard(
            "X:" + r.pos().getX() + " Z:" + r.pos().getZ());
        showToast("Copied!");
    }

    private void copyAllResults() {
        StringBuilder sb = new StringBuilder();
        for (var r : results) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("\"").append(r.type().displayName).append("\" at X:").append(r.pos().getX())
                .append(" Z:").append(r.pos().getZ());
        }
        MinecraftClient.getInstance().keyboard.setClipboard(sb.toString());
        showToast("Copied all (" + results.size() + " results)");
    }

    private void showToast(String msg) {
        clipboardToastText = msg;
        clipboardToastUntil = System.currentTimeMillis() + 1500;
    }

    // ─── Sort ────────────────────────────────────────────────────

    private void reSortResults() {
        switch (sortMode) {
            case NEAREST -> results.sort(java.util.Comparator.comparingInt(r -> r.distance()));
            case TYPE -> results.sort(java.util.Comparator.comparing(r -> r.type().displayName));
            case DIMENSION -> results.sort(java.util.Comparator.comparing(
                r -> r.type().dimension.name()));
        }
    }

    private void cycleSortMode() {
        SortMode[] modes = SortMode.values();
        sortMode = modes[(sortMode.ordinal() + 1) % modes.length];
        reSortResults();
    }

    // ─── Nether coords ───────────────────────────────────────────

    private static String netherStr(BlockPos pos) {
        return "N:X" + (pos.getX() / 8) + " Z" + (pos.getZ() / 8);
    }

    // ─── Render ──────────────────────────────────────────────────

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
        boolean hasContent = !results.isEmpty() || isSearchingAll;
        if (!hasContent) return;

        int px = touchDevice ? 4 : (int) (this.width * 0.55);
        int pw = touchDevice ? this.width - 8 : this.width - px - 4;
        int py = touchDevice ? this.height - 170 : 50;
        int maxPh = this.height - py - (touchDevice ? 48 : 30);
        int rowH = 30;
        int headerH = 18;
        int contentH = rowH * results.size() + (isSearchingAll ? 20 : 0);
        int ph = Math.min(headerH + contentH, maxPh);
        if (ph < headerH + 20) ph = headerH + 20;

        ctx.fill(px, py, px + pw, py + ph, 0xCC000000);

        // ─── Header row ──────────────────────────────────────────
        int headerY = py + 4;
        ctx.drawText(tr, "Results (" + results.size() + ")", px + 6, headerY, 0xFFFFFF, true);

        // Copy All
        int copyAllX = px + pw - 58;
        ctx.drawText(tr, "[Copy All]", copyAllX, headerY, 0xFFFFAA, true);

        // View toggle
        int toggleX = copyAllX - 90;
        String listLbl = viewMode == ViewMode.LIST ? "[List]" : " List";
        String mapLbl = viewMode == ViewMode.MAP ? "[Map]" : " Map";
        ctx.drawText(tr, listLbl, toggleX, headerY,
            viewMode == ViewMode.LIST ? 0xFFFFFF : 0x888888, true);
        ctx.drawText(tr, "|", toggleX + 40, headerY, 0x666666, true);
        ctx.drawText(tr, mapLbl, toggleX + 46, headerY,
            viewMode == ViewMode.MAP ? 0xFFFFFF : 0x888888, true);

        // Sort toggle
        String sortLabel = switch (sortMode) {
            case NEAREST -> "Dist \u25BC";
            case TYPE -> "Type \u25BC";
            case DIMENSION -> "Dim \u25BC";
        };
        int sortX = toggleX - 50;
        ctx.drawText(tr, sortLabel, sortX, headerY, 0xAAAAAA, true);

        // Divider
        ctx.fill(px, py + headerH + 2, px + pw, py + headerH + 3, 0xFF444444);

        // ─── Content ─────────────────────────────────────────────
        int contentY = py + headerH + 6;

        if (isSearchingAll && results.isEmpty()) {
            ctx.drawText(tr, "Searching...", px + 10, contentY + 10, 0x888888, false);
            ctx.drawText(tr, searchingAllTypes.size() + " types remaining", px + 10,
                contentY + 22, 0x666666, false);
        } else if (viewMode == ViewMode.LIST) {
            renderResultsList(ctx, mouseX, mouseY, px, pw, contentY, ph - headerH - 6);
        } else {
            renderRadarMap(ctx, mouseX, mouseY, px, pw, contentY, ph - headerH - 6);
        }

        // Scroll indicator
        int visibleRows = (ph - headerH - 6) / rowH;
        if (results.size() > visibleRows && viewMode == ViewMode.LIST) {
            ctx.drawText(tr, "\u25BC scroll \u25B2", px + pw / 2 - 30,
                py + ph - 12, 0x888888, true);
        }

        // Clipboard toast
        if (System.currentTimeMillis() < clipboardToastUntil) {
            int tw = tr.getWidth(clipboardToastText);
            int tx = (this.width - tw) / 2;
            int ty = this.height / 2 - 20;
            ctx.fill(tx - 6, ty - 3, tx + tw + 6, ty + 12, 0xCC000000);
            ctx.drawText(tr, clipboardToastText, tx, ty, 0xFFFFFF, true);
        }
    }

    private void renderResultsList(DrawContext ctx, int mouseX, int mouseY,
                                    int px, int pw, int contentY, int availableH) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int rowH = 30;
        int visibleStart = Math.max(0, resultsScroll / rowH);
        int visibleEnd = Math.min(results.size(), visibleStart + availableH / rowH);

        for (int i = visibleStart; i < visibleEnd; i++) {
            SearchResult r = results.get(i);
            int ry = contentY + (i - visibleStart) * rowH;
            boolean hovered = mouseX >= px + 4 && mouseX <= px + pw - 4
                && mouseY >= ry && mouseY < ry + rowH;
            boolean waypointed = isWaypointed(r.pos());

            // Row highlight on hover
            if (hovered) ctx.fill(px + 4, ry, px + pw - 4, ry + rowH, 0x22FFFFFF);
            // Waypointed top border
            if (waypointed) ctx.fill(px + 4, ry, px + pw - 4, ry + 2, r.color());

            // Color bar
            ctx.fill(px + 6, ry + 4, px + 12, ry + rowH - 4, r.color());

            // Line 1: label + action buttons
            String label = (waypointed ? "\u2713 " : "") + r.type().displayName;
            ctx.drawText(tr, label, px + 16, ry + 1, 0xFFFFFF, true);

            boolean creative = MinecraftClient.getInstance().player != null
                && MinecraftClient.getInstance().player.isCreative();
            int btnRight = px + pw - 6;

            // [X] remove
            ctx.drawText(tr, "X", btnRight - 14, ry + 1,
                (hovered && mouseX >= btnRight - 14 && mouseX <= btnRight) ? 0xFF6666 : 0xFF5555,
                true);

            // [Copy]
            boolean copyHov = hovered && mouseX >= btnRight - 48 && mouseX <= btnRight - 14;
            ctx.drawText(tr, "[Copy]", btnRight - 48, ry + 1,
                copyHov ? 0xFFFFAA : 0xCCCC66, true);

            // [TP] (creative only)
            if (creative) {
                boolean tpHov = hovered && mouseX >= btnRight - 78 && mouseX <= btnRight - 48;
                ctx.drawText(tr, "[TP]", btnRight - 78, ry + 1,
                    tpHov ? 0x66FF66 : 0x55AA55, true);
            }

            // Line 2: coords + distance + nether
            String coords = "X:" + r.pos().getX() + " Z:" + r.pos().getZ();
            int cx2 = px + 16;
            ctx.drawText(tr, coords, cx2, ry + 14, 0xAAAAAA, false);

            cx2 += tr.getWidth(coords) + 8;
            String distDir = r.distance() + "m " + r.direction();
            ctx.drawText(tr, distDir, cx2, ry + 14, 0x888888, false);

            // Nether coords for overworld structures
            if (r.type().dimension == StructureType.Dimension.OVERWORLD
                || r.type().dimension == StructureType.Dimension.ALL) {
                cx2 += tr.getWidth(distDir) + 8;
                ctx.drawText(tr, "(" + netherStr(r.pos()) + ")", cx2, ry + 14, 0x666688, false);
            }
        }
    }

    private void renderRadarMap(DrawContext ctx, int mouseX, int mouseY,
                                 int px, int pw, int contentY, int availableH) {
        var client = MinecraftClient.getInstance();
        var tr = client.textRenderer;
        if (client.player == null) return;

        int cx = px + pw / 2;
        int cy = contentY + availableH / 2;
        int mapSize = Math.min(pw - 16, availableH - 16);
        int mapLeft = cx - mapSize / 2;
        int mapTop = cy - mapSize / 2;
        int mapRight = mapLeft + mapSize;
        int mapBottom = mapTop + mapSize;

        // Border and background
        ctx.fill(mapLeft - 1, mapTop - 1, mapRight + 1, mapBottom + 1, 0xFF444444);
        ctx.fill(mapLeft, mapTop, mapRight, mapBottom, 0xAA111111);

        BlockPos pp = client.player.getBlockPos();
        int maxDist = results.stream().mapToInt(r -> r.distance()).max().orElse(1);
        double scale = mapScale * mapSize / Math.max(maxDist * 2, 1);

        // Distance rings
        int[] ringBlocks = {250, 500, 1000};
        for (int ringR : ringBlocks) {
            int rPx = (int) (ringR * scale);
            if (rPx > mapSize / 2) break;
            drawCircleApprox(ctx, cx, cy, rPx, 0x33FFFFFF);
            ctx.drawText(tr, ringR + "b", cx + rPx + 3, cy - 3, 0x44AAAAAA, false);
        }

        // Cardinal labels
        ctx.drawText(tr, "N", cx - 3, mapTop + 2, 0xFF888888, false);
        ctx.drawText(tr, "S", cx - 3, mapBottom - 10, 0xFF888888, false);
        ctx.drawText(tr, "W", mapLeft + 2, cy - 4, 0xFF888888, false);
        ctx.drawText(tr, "E", mapRight - 8, cy - 4, 0xFF888888, false);

        // Structure dots
        mapHoveredIndex = -1;
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            double dx = (r.pos().getX() - pp.getX()) * scale;
            double dz = (r.pos().getZ() - pp.getZ()) * scale;
            int dotX = cx + (int) dx;
            int dotZ = cy + (int) dz;

            if (dotX < mapLeft + 2 || dotX > mapRight - 6
                || dotZ < mapTop + 2 || dotZ > mapBottom - 6) continue;

            boolean waypointed = isWaypointed(r.pos());
            boolean hovered = mouseX >= dotX - 3 && mouseX <= dotX + 7
                && mouseY >= dotZ - 3 && mouseY <= dotZ + 7;

            if (waypointed) {
                ctx.fill(dotX - 2, dotZ - 2, dotX + 6, dotZ + 6, 0xFFFFFFFF);
            }
            ctx.fill(dotX, dotZ, dotX + 4, dotZ + 4, r.color());

            if (hovered) {
                mapHoveredIndex = i;
                String tt = r.type().displayName + "  " + r.distance() + "m "
                    + r.direction() + "  X:" + r.pos().getX()
                    + " Z:" + r.pos().getZ();
                int tw = tr.getWidth(tt);
                int ttx = Math.min(dotX + 8, mapRight - tw - 4);
                int tty = dotZ - 14;
                ctx.fill(ttx - 2, tty - 2, ttx + tw + 2, tty + 12, 0xCC000000);
                ctx.drawText(tr, tt, ttx, tty + 1, 0xFFFFFF, true);
            }
        }

        // Player marker at center
        ctx.fill(cx - 5, cy - 1, cx + 6, cy + 2, 0xFFFFFFFF);
        ctx.fill(cx - 1, cy - 5, cx + 2, cy + 6, 0xFFFFFFFF);
        ctx.drawText(tr, "You", cx + 8, cy - 4, 0xFFFFFFFF, true);

        // Scale indicator
        String scaleText = "1px \u2248 " + (int) (1 / scale) + "b";
        ctx.drawText(tr, scaleText, mapLeft + 4, mapBottom - 10, 0x66AAAAAA, false);
    }

    /** Approximate a circle by drawing small dots along its perimeter. */
    private static void drawCircleApprox(DrawContext ctx, int cx, int cy, int r, int color) {
        int steps = Math.max(16, r * 2);
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int x = cx + (int) (r * Math.cos(angle));
            int y = cy + (int) (r * Math.sin(angle));
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    // ─── Mouse events ────────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.buttonInfo().button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x();
        double my = click.y();

        boolean hasContent = !results.isEmpty() || isSearchingAll;
        if (!hasContent) return super.mouseClicked(click, doubled);

        int px = touchDevice ? 4 : (int) (this.width * 0.55);
        int pw = touchDevice ? this.width - 8 : this.width - px - 4;
        int py = touchDevice ? this.height - 170 : 50;
        int maxPh = this.height - py - (touchDevice ? 48 : 30);
        int rowH = 30;
        int headerH = 18;
        int contentH = rowH * results.size() + (isSearchingAll ? 20 : 0);
        int ph = Math.min(headerH + contentH, maxPh);
        if (ph < headerH + 20) ph = headerH + 20;

        if (mx < px || mx > px + pw || my < py || my > py + ph) {
            return super.mouseClicked(click, doubled);
        }

        int headerY = py + 4;

        // Header clicks
        if (my >= headerY && my <= headerY + 12) {
            int copyAllX = px + pw - 58;
            if (mx >= copyAllX && mx <= copyAllX + 54) { copyAllResults(); return true; }

            int toggleX = copyAllX - 90;
            if (mx >= toggleX && mx <= toggleX + 36) { viewMode = ViewMode.LIST; return true; }
            if (mx >= toggleX + 44 && mx <= toggleX + 80) {
                if (viewMode != ViewMode.MAP) {
                    viewMode = ViewMode.MAP;
                    recalculateMapScale(pw, ph - headerH - 6);
                }
                return true;
            }

            int sortX = toggleX - 50;
            if (mx >= sortX && mx <= sortX + 44) { cycleSortMode(); return true; }
            return true;
        }

        int contentY = py + headerH + 6;

        // List view: result rows
        if (viewMode == ViewMode.LIST && my >= contentY) {
            int visibleStart = Math.max(0, resultsScroll / rowH);
            int rowIndex = visibleStart + (int) ((my - contentY) / rowH);
            if (rowIndex < 0 || rowIndex >= results.size()) return true;

            SearchResult r = results.get(rowIndex);
            int ry = contentY + (rowIndex - visibleStart) * rowH;
            boolean creative = MinecraftClient.getInstance().player != null
                && MinecraftClient.getInstance().player.isCreative();
            int btnRight = px + pw - 6;

            // [X]
            if (mx >= btnRight - 14 && mx <= btnRight) { removeResult(rowIndex); return true; }
            // [Copy]
            if (mx >= btnRight - 48 && mx <= btnRight - 14) { copyCoords(r); return true; }
            // [TP]
            if (creative && mx >= btnRight - 78 && mx <= btnRight - 48) {
                var client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.networkHandler.sendChatCommand(
                        "tp @p " + r.pos().getX() + " ~ " + r.pos().getZ());
                }
                return true;
            }
            // Row click -> toggle waypoint
            toggleWaypoint(r);
            return true;
        }

        // Map view: dot clicks
        if (viewMode == ViewMode.MAP && my >= contentY) {
            handleMapDotClick(mx, my, px, pw, contentY, ph - headerH - 6);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleMapDotClick(double mx, double my, int px, int pw,
                                    int contentY, int availableH) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int cx = px + pw / 2;
        int cy = contentY + availableH / 2;
        int mapSize = Math.min(pw - 16, availableH - 16);
        BlockPos pp = client.player.getBlockPos();
        int maxDist = results.stream().mapToInt(r -> r.distance()).max().orElse(1);
        double scale = mapScale * mapSize / Math.max(maxDist * 2, 1);

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            double dx = (r.pos().getX() - pp.getX()) * scale;
            double dz = (r.pos().getZ() - pp.getZ()) * scale;
            int dotX = cx + (int) dx;
            int dotZ = cy + (int) dz;

            if (mx >= dotX - 3 && mx <= dotX + 7
                && my >= dotZ - 3 && my <= dotZ + 7) {
                toggleWaypoint(r);
                return;
            }
        }
    }

    private void recalculateMapScale(int panelWidth, int panelHeight) {
        int maxDist = results.stream().mapToInt(r -> r.distance()).max().orElse(1);
        int mapSize = Math.min(panelWidth, panelHeight);
        mapScale = (mapSize * 0.8) / (Math.max(maxDist * 2, 1));
        mapScale = Math.clamp(mapScale, MIN_MAP_SCALE, MAX_MAP_SCALE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // Map zoom
        if (viewMode == ViewMode.MAP && !results.isEmpty()) {
            int px = touchDevice ? 4 : (int) (this.width * 0.55);
            int pw = touchDevice ? this.width - 8 : this.width - px - 4;
            if (mouseX >= px && mouseX <= px + pw) {
                double factor = vertical > 0 ? 1.2 : 1 / 1.2;
                mapScale = Math.clamp(mapScale * factor, MIN_MAP_SCALE, MAX_MAP_SCALE);
                return true;
            }
        }

        // Scroll structure list (left area)
        int listTop = touchDevice ? 76 : 50;
        int bottomY = this.height - (touchDevice ? 48 : 30);
        if (mouseY >= listTop && mouseY <= bottomY && mouseX < this.width * 0.5) {
            scrollOffset = Math.clamp(scrollOffset - (int) (vertical * 20), 0, maxScroll);
            rebuildButtons();
            return true;
        }

        // Scroll results list (right panel)
        if (viewMode == ViewMode.LIST && !results.isEmpty()) {
            int px = touchDevice ? 4 : (int) (this.width * 0.55);
            int pw = touchDevice ? this.width - 8 : this.width - px - 4;
            int py = touchDevice ? this.height - 170 : 50;
            int maxPh = this.height - py - (touchDevice ? 48 : 30);
            int headerH = 18;
            int rowH = 30;
            int contentH = rowH * results.size() + (isSearchingAll ? 20 : 0);
            int ph = Math.min(headerH + contentH, maxPh);
            if (mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph) {
                resultsScroll = Math.clamp(resultsScroll - (int) (vertical * rowH), 0,
                    Math.max(0, results.size() * rowH - (ph - headerH - 6)));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    public void removeResult(int index) {
        if (index >= 0 && index < results.size()) {
            var r = results.remove(index);
            var waypoints = new ArrayList<>(WaypointStore.snapshot());
            for (int i = waypoints.size() - 1; i >= 0; i--) {
                if (waypoints.get(i).pos().equals(r.pos())) {
                    WaypointStore.remove(i);
                    break;
                }
            }
        }
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
