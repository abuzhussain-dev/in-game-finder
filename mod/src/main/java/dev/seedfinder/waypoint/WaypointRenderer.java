package dev.seedfinder.waypoint;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
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
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
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

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class WaypointRenderer {
    private static WaypointRenderer instance;
    private static final Identifier MOD_ID = Identifier.of("seedfinder");

    // ponytail: custom through-walls pipeline based on DEBUG_FILLED_SNIPPET
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.of("seedfinder", "pipeline/debug_filled_box_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build()
    );

    private static final BufferAllocator allocator = new BufferAllocator(RenderLayer.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private MappableRingBuffer vertexBuffer;

    private WaypointRenderer() {}

    public static void register() {
        if (instance != null) return;
        instance = new WaypointRenderer();
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(instance::extractAndDraw);
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

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        for (var wp : waypoints) {
            BlockPos p = wp.pos();
            float r = ((wp.color() >> 16) & 0xFF) / 255f;
            float g = ((wp.color() >> 8) & 0xFF) / 255f;
            float b = (wp.color() & 0xFF) / 255f;

            float minX = p.getX() - 0.5f;
            float maxX = p.getX() + 0.5f;
            float minZ = p.getZ() - 0.5f;
            float maxZ = p.getZ() + 0.5f;
            float topY = 320f;
            float bottomY = 0f;

            renderFilledBox(matrices.peek().getPositionMatrix(), buffer, minX, bottomY, minZ, maxX, topY, maxZ, r, g, b);
        }

        matrices.pop();

        // --- draw phase ---
        BuiltBuffer builtBuffer = buffer.end();
        BuiltBuffer.DrawParameters drawParams = builtBuffer.getDrawParameters();
        VertexFormat format = drawParams.format();

        int vertexBufferSize = drawParams.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(
                () -> "seedfinder waypoint render",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                vertexBufferSize
            );
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var mappedView = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, builtBuffer.getBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.getBuffer(), mappedView.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (FILLED_THROUGH_WALLS.getVertexFormatMode() == VertexFormat.DrawMode.QUADS) {
            builtBuffer.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
            indices = FILLED_THROUGH_WALLS.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.getSortedBuffer());
            indexType = builtBuffer.getDrawParameters().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(FILLED_THROUGH_WALLS.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParams.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    () -> "seedfinder waypoint rendering",
                    client.getFramebuffer().getColorTextureView(),
                    OptionalInt.empty(),
                    client.getFramebuffer().getDepthTextureView(),
                    OptionalDouble.empty()
                )) {
            pass.setPipeline(FILLED_THROUGH_WALLS);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, drawParams.indexCount(), 1);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
        buffer = null;

        // --- labels ---
        drawLabels(ctx, waypoints, matrices, camera);
    }

    private static void drawLabels(WorldRenderContext ctx, List<Waypoint> waypoints, MatrixStack matrices, Vec3d camPos) {
        var client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Camera cam = client.gameRenderer.getCamera();
        TextRenderer textRenderer = client.textRenderer;

        for (var wp : waypoints) {
            BlockPos p = wp.pos();
            float topY = 320f;

            matrices.push();
            matrices.translate(p.getX() - camPos.x, topY + 2.0 - camPos.y, p.getZ() - camPos.z);
            matrices.multiply(cam.getRotation());

            double dist = Math.sqrt(client.player.getBlockPos().getSquaredDistance(p));
            String label = wp.label() + " " + (int) dist + "m";

            matrices.scale(0.025f, 0.025f, 0.025f);
            int textW = textRenderer.getWidth(label);
            float textX = -textW / 2f;

            textRenderer.draw(label, textX, 0, 0xFFFFFF, true, matrices.peek().getPositionMatrix(),
                client.getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, 0x000000, 0xF000F0);

            matrices.pop();
        }
    }

    private static void renderFilledBox(Matrix4fc posMat, BufferBuilder b, float minX, float minY, float minZ,
                                         float maxX, float maxY, float maxZ, float r, float g, float bl) {
        b.addVertex(posMat, minX, minY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, minY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, maxY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, maxY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, minY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, minY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, maxY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, maxY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, minY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, minY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, maxY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, maxY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, minY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, minY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, maxY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, maxY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, maxY, maxZ).setColor(r, g, bl, 0.6f);
        b.addVertex(posMat, maxX, maxY, maxZ).setColor(r, g, bl, 0.6f);
        b.addVertex(posMat, maxX, maxY, minZ).setColor(r, g, bl, 0.6f);
        b.addVertex(posMat, minX, maxY, minZ).setColor(r, g, bl, 0.6f);
        b.addVertex(posMat, minX, minY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, minY, minZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, maxX, minY, maxZ).setColor(r, g, bl, 0.2f);
        b.addVertex(posMat, minX, minY, maxZ).setColor(r, g, bl, 0.2f);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        var waypoints = WaypointStore.snapshot();
        if (waypoints.isEmpty()) return;

        var playerPos = client.player.getBlockPos();
        var best = waypoints.get(0);
        double bestDist = playerPos.getSquaredDistance(best.pos());
        for (var wp : waypoints) {
            double d = playerPos.getSquaredDistance(wp.pos());
            if (d < bestDist) {
                bestDist = d;
                best = wp;
            }
        }

        double dist = Math.sqrt(bestDist);
        String dir = cardinalDirection(playerPos, best.pos());
        String text = "\u2192 " + best.label() + " " + (int) dist + "m [" + dir + "]";

        int color = best.color();
        int w = client.textRenderer.getWidth(text);
        ctx.fill(8, 8, 10 + w + 2, 20, 0x88000000);
        ctx.drawText(client.textRenderer, text, 10, 10, color, true);
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
