package dev.seedfinder.waypoint;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders colored beacon beams + labels for waypoints, plus a HUD indicator
 * for the nearest waypoint direction and distance.
 */
public final class WaypointRenderer {
    private WaypointRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointRenderer::render);
        HudRenderCallback.EVENT.register(WaypointRenderer::renderHud);
    }

    private static void render(WorldRenderContext ctx) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        Camera cam = client.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();

        var matrices = ctx.matrixStack();
        var waypoints = WaypointStore.snapshot();
        if (waypoints.isEmpty()) return;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        TextRenderer textRenderer = client.textRenderer;

        for (var wp : waypoints) {
            BlockPos p = wp.pos();
            matrices.push();
            matrices.translate(p.getX() - camPos.getX(), 0, p.getZ() - camPos.getZ());

            // Colored beam from y=0 to y=320
            float r = ((wp.color() >> 16) & 0xFF) / 255f;
            float g = ((wp.color() >> 8) & 0xFF) / 255f;
            float b = (wp.color() & 0xFF) / 255f;

            var builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float topY = 320f;
            float bottomY = 0f;
            float half = 0.5f;

            // Top quad
            builder.vertex(matrices.peek().getPositionMatrix(), -half, topY, -half).color(r, g, b, 0.6f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, topY, -half).color(r, g, b, 0.6f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, topY, half).color(r, g, b, 0.6f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, topY, half).color(r, g, b, 0.6f);

            // North face
            builder.vertex(matrices.peek().getPositionMatrix(), -half, bottomY, -half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, bottomY, -half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, topY, -half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, topY, -half).color(r, g, b, 0.2f);

            // South face
            builder.vertex(matrices.peek().getPositionMatrix(), half, bottomY, half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, bottomY, half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, topY, half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, topY, half).color(r, g, b, 0.2f);

            // East face
            builder.vertex(matrices.peek().getPositionMatrix(), half, bottomY, -half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, bottomY, half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, topY, half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), half, topY, -half).color(r, g, b, 0.2f);

            // West face
            builder.vertex(matrices.peek().getPositionMatrix(), -half, bottomY, half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, bottomY, -half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, topY, -half).color(r, g, b, 0.2f);
            builder.vertex(matrices.peek().getPositionMatrix(), -half, topY, half).color(r, g, b, 0.2f);

            BufferRenderer.drawWithGlobalProgram(builder.end());

            matrices.pop();

            // Label floating above the beam top
            matrices.push();
            Vec3d topPos = new Vec3d(p.getX(), topY + 2.0, p.getZ());
            Vec3d labelPos = topPos.subtract(camPos);
            matrices.translate(labelPos.x, labelPos.y, labelPos.z);
            matrices.multiply(cam.getRotation());

            double dist = Math.sqrt(client.player.getBlockPos().getSquaredDistance(p));
            String label = wp.label() + " " + (int) dist + "m";

            float scale = 0.025f;
            matrices.scale(scale, scale, scale);
            int textW = textRenderer.getWidth(label);
            float textX = -textW / 2f;

            Matrix4f posMat = matrices.peek().getPositionMatrix();
            textRenderer.draw(label, textX, 0, 0xFFFFFF, true, posMat, client.getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, 0x000000, 0xF000F0);

            matrices.pop();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderHud(DrawContext ctx, float tickDelta) {
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

        int x = 10;
        int y = 10;
        int color = best.color();
        ctx.fill(x - 2, y - 2, x + client.textRenderer.getWidth(text) + 2, y + 10 + 2, 0x88000000);
        ctx.drawText(client.textRenderer, text, x, y, color, true);
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
