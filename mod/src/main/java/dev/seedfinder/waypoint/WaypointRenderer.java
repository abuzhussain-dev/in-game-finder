package dev.seedfinder.waypoint;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import dev.seedfinder.waypoint.WaypointStore.Waypoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class WaypointRenderer {
    private static WaypointRenderer instance;
    private static final RenderPipeline.Snippet FILLED_SNIPPET = new RenderPipeline.Snippet(
        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
        java.util.Optional.of(DepthTestFunction.NO_DEPTH_TEST),
        java.util.Optional.empty(), java.util.Optional.empty(),
        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
        java.util.Optional.empty(),
        java.util.Optional.of(net.minecraft.client.render.VertexFormats.POSITION_COLOR),
        java.util.Optional.of(VertexFormat.DrawMode.QUADS)
    );
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(new RenderPipeline.Snippet[]{FILLED_SNIPPET})
            .withLocation(net.minecraft.util.Identifier.of("seedfinder", "pipeline/debug_filled_box_through_walls"))
            .build()
    );
    private static final BufferAllocator allocator = new BufferAllocator(8192);
    private BufferBuilder buffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private MappableRingBuffer vertexBuffer;

    // Performance constants
    private static final int MAX_RENDER_DIST = 2048;
    private static final int MAX_VISIBLE_BEAMS = 8;
    private static final int BEAM_HEIGHT = 32;
    private static final int MAX_WAYPOINTS = 50;

    private WaypointRenderer() {}

    public static void register() {
        if (instance != null) return;
        instance = new WaypointRenderer();
        WorldRenderEvents.END_MAIN.register(instance::extractAndDraw);
        HudRenderCallback.EVENT.register(WaypointRenderer::renderHud);
    }

    private void extractAndDraw(WorldRenderContext ctx) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        var waypoints = WaypointStore.snapshot();
        if (waypoints.isEmpty()) return;

        VertexFormat.DrawMode mode = FILLED_THROUGH_WALLS.getVertexFormatMode();
        VertexFormat fmt = FILLED_THROUGH_WALLS.getVertexFormat();
        if (buffer == null) {
            buffer = new BufferBuilder(allocator, mode, fmt);
        }

        MatrixStack matrices = ctx.matrices();
        Vec3d camera = ctx.worldState().cameraRenderState.pos;
        double camX = camera.x, camY = camera.y, camZ = camera.z;

        long time = System.currentTimeMillis();
        float pulse = 0.15f + 0.1f * (float) Math.sin(time * 0.003);
        float topPulse = pulse + 0.35f;

        matrices.push();
        matrices.translate(-camX, -camY, -camZ);

        int beamsDrawn = 0;
        for (var wp : waypoints) {
            if (beamsDrawn >= MAX_VISIBLE_BEAMS) break;

            BlockPos p = wp.pos();
            double dx = p.getX() - camX, dz = p.getZ() - camZ;

            if (dx * dx + dz * dz > (double) MAX_RENDER_DIST * MAX_RENDER_DIST) continue;

            Camera cam = client.gameRenderer.getCamera();
            float yawRad = (float) Math.toRadians(cam.getYaw());
            float lookX = -(float) Math.sin(yawRad);
            float lookZ = (float) Math.cos(yawRad);
            if (dx * lookX + dz * lookZ < -200) continue;

            float r = ((wp.color() >> 16) & 0xFF) / 255f;
            float g = ((wp.color() >> 8) & 0xFF) / 255f;
            float b = (wp.color() & 0xFF) / 255f;

            float cx = p.getX(), cz = p.getZ();
            float botY = (float) camY - 2f;
            float topY = (float) camY + BEAM_HEIGHT;

            renderFilledBox(matrices.peek().getPositionMatrix(), buffer,
                cx - 0.5f, botY, cz - 0.5f, cx + 0.5f, topY, cz + 0.5f,
                r, g, b, pulse, topPulse);
            beamsDrawn++;
        }

        matrices.pop();

        if (beamsDrawn == 0) { buffer = null; return; }

        BuiltBuffer builtBuffer = buffer.end();
        BuiltBuffer.DrawParameters drawParams = builtBuffer.getDrawParameters();
        VertexFormat format = drawParams.format();

        int vertexBufferSize = drawParams.vertexCount() * format.getVertexSize();
        int allocSize = Math.max(vertexBufferSize, MAX_WAYPOINTS * 384);
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(
                () -> "seedfinder waypoint pool",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                allocSize
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var mappedView = encoder.mapBuffer(
                vertexBuffer.getBlocking().slice(0, builtBuffer.getBuffer().remaining()),
                false, true)) {
            MemoryUtil.memCopy(builtBuffer.getBuffer(), mappedView.data());
        }

        GpuBuffer vertices = vertexBuffer.getBlocking();
        RenderSystem.ShapeIndexBuffer sib = RenderSystem.getSequentialBuffer(
            FILLED_THROUGH_WALLS.getVertexFormatMode());
        GpuBuffer indices = sib.getIndexBuffer(drawParams.indexCount());
        VertexFormat.IndexType indexType = sib.getIndexType();

        GpuBufferSlice dt = RenderSystem.getDynamicUniforms()
            .write(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        var fb = client.getFramebuffer();
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "seedfinder waypoint rendering",
                    fb.getColorAttachmentView(), OptionalInt.empty(),
                    fb.getDepthAttachmentView(), OptionalDouble.empty())) {
            pass.setPipeline(FILLED_THROUGH_WALLS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dt);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, drawParams.indexCount(), 1);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
        buffer = null;

        drawLabels(ctx, waypoints, matrices, camera);
    }

    private static void renderFilledBox(Matrix4fc pm, BufferBuilder b,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float bl, float sa, float ta) {
        b.vertex(pm, x1, y1, z2).color(r, g, bl, sa);
        b.vertex(pm, x2, y1, z2).color(r, g, bl, sa);
        b.vertex(pm, x2, y2, z2).color(r, g, bl, sa);
        b.vertex(pm, x1, y2, z2).color(r, g, bl, sa);
        b.vertex(pm, x2, y1, z1).color(r, g, bl, sa);
        b.vertex(pm, x1, y1, z1).color(r, g, bl, sa);
        b.vertex(pm, x1, y2, z1).color(r, g, bl, sa);
        b.vertex(pm, x2, y2, z1).color(r, g, bl, sa);
        b.vertex(pm, x1, y1, z1).color(r, g, bl, sa);
        b.vertex(pm, x1, y1, z2).color(r, g, bl, sa);
        b.vertex(pm, x1, y2, z2).color(r, g, bl, sa);
        b.vertex(pm, x1, y2, z1).color(r, g, bl, sa);
        b.vertex(pm, x2, y1, z2).color(r, g, bl, sa);
        b.vertex(pm, x2, y1, z1).color(r, g, bl, sa);
        b.vertex(pm, x2, y2, z1).color(r, g, bl, sa);
        b.vertex(pm, x2, y2, z2).color(r, g, bl, sa);
        b.vertex(pm, x1, y2, z2).color(r, g, bl, ta);
        b.vertex(pm, x2, y2, z2).color(r, g, bl, ta);
        b.vertex(pm, x2, y2, z1).color(r, g, bl, ta);
        b.vertex(pm, x1, y2, z1).color(r, g, bl, ta);
        b.vertex(pm, x1, y1, z1).color(r, g, bl, sa);
        b.vertex(pm, x2, y1, z1).color(r, g, bl, sa);
        b.vertex(pm, x2, y1, z2).color(r, g, bl, sa);
        b.vertex(pm, x1, y1, z2).color(r, g, bl, sa);
    }

    private static void drawLabels(WorldRenderContext ctx, List<Waypoint> waypoints,
                                    MatrixStack matrices, Vec3d camPos) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Camera cam = client.gameRenderer.getCamera();
        TextRenderer tr = client.textRenderer;

        for (var wp : waypoints) {
            BlockPos p = wp.pos();
            double dx = p.getX() - camPos.x, dz = p.getZ() - camPos.z;
            if (dx * dx + dz * dz > (double) MAX_RENDER_DIST * MAX_RENDER_DIST) continue;

            float ly = (float) camPos.y + BEAM_HEIGHT + 2.0f;
            matrices.push();
            matrices.translate(p.getX() - camPos.x, ly - camPos.y, p.getZ() - camPos.z);
            matrices.multiply(cam.getRotation());

            double dist = Math.sqrt(client.player.getBlockPos().getSquaredDistance(p));
            String label = wp.label() + " " + (int) dist + "m";

            matrices.scale(0.025f, 0.025f, 0.025f);
            int tw = tr.getWidth(label);
            tr.draw(label, -tw / 2f, 0, 0xFFFFFF, true,
                matrices.peek().getPositionMatrix(),
                client.getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, 0x000000, 0xF000F0);
            matrices.pop();
        }
    }

    // ─── HUD: waypoint list + compass bar ──────────────────────────

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        var waypoints = WaypointStore.snapshot();
        if (waypoints.isEmpty()) return;

        renderCompassBar(ctx, client, waypoints);

        var pp = client.player.getBlockPos();
        List<Waypoint> sorted = waypoints.stream()
            .sorted(Comparator.comparingDouble(w -> pp.getSquaredDistance(w.pos())))
            .limit(8).toList();
        var tr = client.textRenderer;

        int maxLabelW = 0;
        int maxCoordW = 0;
        List<String> labelLines = new ArrayList<>();
        List<String> coordLines = new ArrayList<>();
        List<Float> relAngles = new ArrayList<>();
        for (var wp : sorted) {
            double dist = Math.sqrt(pp.getSquaredDistance(wp.pos()));
            labelLines.add(wp.label() + "  " + (int) dist + "m");
            coordLines.add("X:" + wp.pos().getX() + " Z:" + wp.pos().getZ());
            relAngles.add(relativeAngle(pp, wp.pos(), client.player.getYaw()));
            maxLabelW = Math.max(maxLabelW, tr.getWidth(labelLines.get(labelLines.size() - 1)));
            maxCoordW = Math.max(maxCoordW, tr.getWidth(coordLines.get(coordLines.size() - 1)));
        }
        int indicatorW = 10;
        int bgW = maxLabelW + maxCoordW + indicatorW + 30;
        int bgH = 10 + labelLines.size() * 16;
        int leftX = 4;
        ctx.fill(leftX, 4, leftX + bgW, bgH + 4, 0x88000000);

        ctx.drawText(tr, "Waypoints (" + waypoints.size() + ")", leftX + 8, 6, 0xCCCCCC, true);
        for (int i = 0; i < labelLines.size(); i++) {
            int y = 12 + i * 16;
            ctx.fill(leftX + 8, y + 3, leftX + 16, y + 9, sorted.get(i).color());
            ctx.drawText(tr, labelLines.get(i), leftX + 20, y, 0xFFFFFF, true);
            ctx.drawText(tr, coordLines.get(i), leftX + 20, y + 8, 0x888888, false);
            // Drawn direction indicator
            drawDirectionIndicator(ctx, leftX + 20 + maxLabelW + 6, y + 4, relAngles.get(i), sorted.get(i).color());
        }

        // Click-to-remove on HUD entry
        if (client.mouse.wasLeftButtonClicked()) {
            double mx = client.mouse.getX() * client.getWindow().getScaledWidth()
                / client.getWindow().getWidth();
            double my = client.mouse.getY() * client.getWindow().getScaledHeight()
                / client.getWindow().getHeight();
            for (int i = 0; i < labelLines.size(); i++) {
                int y = 12 + i * 16;
                int xRight = leftX + bgW;
                if (mx >= xRight - 12 && mx <= xRight && my >= y && my <= y + 14) {
                    var wp = sorted.get(i);
                    var snap = WaypointStore.snapshot();
                    for (int j = snap.size() - 1; j >= 0; j--) {
                        if (snap.get(j).pos().equals(wp.pos())
                            && snap.get(j).label().equals(wp.label())) {
                            WaypointStore.remove(j); break;
                        }
                    }
                    break;
                }
            }
        }
    }

    /** Compass bar at top-center showing N/E/S/W with waypoint bearings. */
    private static void renderCompassBar(DrawContext ctx, MinecraftClient client, List<Waypoint> waypoints) {
        var tr = client.textRenderer;
        int sw = client.getWindow().getScaledWidth();
        int barW = Math.min(sw - 40, 240);
        int barX = (sw - barW) / 2;
        int barY = 4;
        int barH = 12;

        ctx.fill(barX, barY, barX + barW, barY + barH, 0x88000000);
        // Center line
        ctx.fill(barX + barW / 2 - 1, barY, barX + barW / 2 + 1, barY + barH, 0x44FFFFFF);

        // Convert player yaw (Minecraft: 0=S, 90=W, ±180=N, -90=E) to north-based bearing
        float facingBearing = ((client.player.getYaw() % 360) + 360 + 180) % 360;
        int centerX = barX + barW / 2;

        // Direction labels
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int[] bearings = {0, 45, 90, 135, 180, 225, 270, 315};
        for (int i = 0; i < dirs.length; i++) {
            float rel = normalizeAngle(bearings[i] - facingBearing);
            float x = centerX + (rel / 180f) * (barW / 2f);
            if (x >= barX + 2 && x <= barX + barW - 2) {
                boolean cardinal = dirs[i].length() == 1;
                ctx.drawText(tr, dirs[i], (int) x - tr.getWidth(dirs[i]) / 2, barY + 2,
                    cardinal ? 0xFFFFFF : 0x666666, false);
            }
        }

        // Waypoint bearing ticks
        var pp = client.player.getBlockPos();
        for (var wp : waypoints) {
            double bearing = bearingFromNorth(pp, wp.pos());
            float rel = normalizeAngle((float) bearing - facingBearing);
            float x = centerX + (rel / 180f) * (barW / 2f);
            if (x >= barX + 2 && x <= barX + barW - 2) {
                ctx.fill((int) x - 1, barY + barH - 4, (int) x + 2, barY + barH - 1, wp.color());
            }
        }
    }

    /** Draw a direction indicator: center dot + tip dot pointing in the relative angle. */
    private static void drawDirectionIndicator(DrawContext ctx, int x, int y, float relAngle, int color) {
        int cx = x + 4;
        int cy = y + 4;
        double rad = Math.toRadians(relAngle);
        double sin = Math.sin(rad);
        double cos = -Math.cos(rad); // flip Y for screen coords
        // Center dot
        ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
        // Direction tip
        int tx = cx + (int) Math.round(sin * 4);
        int ty = cy + (int) Math.round(cos * 4);
        ctx.fill(tx - 1, ty - 1, tx + 1, ty + 1, 0xFFFFFF);
    }

    /** Bearing from player to target, in degrees from north (0=N, 90=E, 180=S, 270=W). */
    private static double bearingFromNorth(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double raw = 90 - Math.toDegrees(Math.atan2(dz, dx));
        if (raw < 0) raw += 360;
        return raw;
    }

    /** Returns the clockwise angle difference from player yaw to waypoint, in degrees the indicator should rotate (0=up=forward). */
    private static float relativeAngle(BlockPos from, BlockPos to, float playerYaw) {
        double bearing = bearingFromNorth(from, to);
        float facingBearing = ((playerYaw % 360) + 360 + 180) % 360;
        float rel = normalizeAngle((float) bearing - facingBearing);
        // rel=0 means waypoint is directly ahead → indicator points up (0° rotation)
        return rel;
    }

    /** Normalize angle to -180..180. */
    private static float normalizeAngle(float a) {
        while (a < -180) a += 360;
        while (a > 180) a -= 360;
        return a;
    }
}
