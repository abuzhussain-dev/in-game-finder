package dev.seedfinder.waypoint;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthTestFunction;
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
import net.minecraft.util.Identifier;
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
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.of("seedfinder", "pipeline/debug_filled_box_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );
    private static final BufferAllocator allocator = new BufferAllocator(256);
    private BufferBuilder buffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private MappableRingBuffer vertexBuffer;

    // Phase 4: performance constants
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

            // Distance culling
            if (dx * dx + dz * dz > (double) MAX_RENDER_DIST * MAX_RENDER_DIST) continue;

            // Behind-camera culling
            float lookX = (float) camera.x, lookZ = (float) camera.z;
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
        // Pre-allocated buffer: at least MAX_WAYPOINTS * estimated size
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

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        var waypoints = WaypointStore.snapshot();
        if (waypoints.isEmpty()) return;

        var pp = client.player.getBlockPos();
        List<Waypoint> sorted = waypoints.stream()
            .sorted(Comparator.comparingDouble(w -> pp.getSquaredDistance(w.pos())))
            .limit(8).toList();
        var tr = client.textRenderer;
        int maxW = 0;
        List<String> lines = new ArrayList<>();
        for (var wp : sorted) {
            double dist = Math.sqrt(pp.getSquaredDistance(wp.pos()));
            String dir = cardinalDirection(pp, wp.pos());
            String line = wp.label() + "  " + (int) dist + "m " + dir;
            lines.add(line);
            maxW = Math.max(maxW, tr.getWidth(line));
        }
        int bgH = 6 + lines.size() * 14;
        ctx.fill(4, 4, maxW + 20, bgH, 0x88000000);
        for (int i = 0; i < lines.size(); i++) {
            int y = 8 + i * 14;
            ctx.fill(8, y + 3, 16, y + 9, sorted.get(i).color());
            ctx.drawText(tr, lines.get(i), 20, y, 0xFFFFFF, true);
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
