package dev.seedfinder.waypoint;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders a colored vertical beam and top square at each waypoint.
 * Visible through walls (depth test disabled). Kept intentionally small — no textures.
 */
public final class WaypointRenderer {
    private WaypointRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointRenderer::render);
    }

    private static void render(WorldRenderContextCompat ctx) {
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

        for (var wp : waypoints) {
            BlockPos p = wp.pos();
            matrices.push();
            matrices.translate(p.getX() - camPos.x, -camPos.y, p.getZ() - camPos.z);
            drawBeam(matrices, wp.color());
            matrices.pop();
        }

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void drawBeam(MatrixStack matrices, int argb) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float half = 0.5f;
        float top = 320f;
        float bot = -64f;
        // 4 vertical faces
        buf.vertex(m, -half, bot, -half).color(r, g, b, a);
        buf.vertex(m, -half, top, -half).color(r, g, b, a);
        buf.vertex(m,  half, top, -half).color(r, g, b, a);
        buf.vertex(m,  half, bot, -half).color(r, g, b, a);

        buf.vertex(m,  half, bot,  half).color(r, g, b, a);
        buf.vertex(m,  half, top,  half).color(r, g, b, a);
        buf.vertex(m, -half, top,  half).color(r, g, b, a);
        buf.vertex(m, -half, bot,  half).color(r, g, b, a);

        buf.vertex(m, -half, bot,  half).color(r, g, b, a);
        buf.vertex(m, -half, top,  half).color(r, g, b, a);
        buf.vertex(m, -half, top, -half).color(r, g, b, a);
        buf.vertex(m, -half, bot, -half).color(r, g, b, a);

        buf.vertex(m,  half, bot, -half).color(r, g, b, a);
        buf.vertex(m,  half, top, -half).color(r, g, b, a);
        buf.vertex(m,  half, top,  half).color(r, g, b, a);
        buf.vertex(m,  half, bot,  half).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    // Small compat shim: WorldRenderContext type used inline to keep imports tidy.
    public interface WorldRenderContextCompat extends net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext {}
}