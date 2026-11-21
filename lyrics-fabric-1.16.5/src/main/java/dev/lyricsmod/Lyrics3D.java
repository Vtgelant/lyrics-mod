package dev.lyricsmod;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;

/** Floating billboard lyrics in the world. Elytra mode pushes them far ahead while gliding. */
public final class Lyrics3D {
    private static final int FULL_BRIGHT = 0xF000F0;

    private final LyricsConfig cfg;
    private final LyricsManager mgr;
    private Vec3d anchor;

    public Lyrics3D(LyricsConfig cfg, LyricsManager mgr) {
        this.cfg = cfg;
        this.mgr = mgr;
    }

    public void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }

    private void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (!cfg.enabled || !cfg.lyrics3d || p == null || mc.options.hudHidden) return;
        TrackInfo t = mgr.track();
        VertexConsumerProvider consumers = ctx.consumers();
        if (t == null || consumers == null) return;

        float tickDelta = ctx.tickDelta();
        boolean elytra = cfg.elytraMode && p.isFallFlying();
        Vec3d eye = p.getCameraPosVec(tickDelta);
        Vec3d look = p.getRotationVec(tickDelta);

        Vec3d desired;
        double smoothing;
        if (elytra) {
            desired = eye.add(look.multiply(16.0));
            smoothing = 0.3;
        } else {
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1.0E-4 ? (anchor == null ? new Vec3d(0, 0, 1) : flat) : flat.normalize();
            if (flat.lengthSquared() < 1.0E-4) return;
            desired = eye.add(flat.multiply(5.0)).add(0, 0.3, 0);
            smoothing = 0.08;
        }
        if (anchor == null || anchor.squaredDistanceTo(desired) > 900) {
            anchor = desired;
        } else {
            anchor = anchor.add(desired.subtract(anchor).multiply(smoothing));
        }

        int count = cfg.style == LyricsConfig.Style.COMPACT ? 1 : Math.max(1, Math.min(5, cfg.lines));
        int cur = count / 2;
        String[] texts = mgr.visibleLines(t, count, cfg.offsetMs);

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        float s = (float) (anchor.distanceTo(eye) * 0.006 * cfg.scale);
        TextRenderer tr = mc.textRenderer;

        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(anchor.x - cp.x, anchor.y - cp.y, anchor.z - cp.z);
        ms.multiply(cam.getRotation());
        ms.scale(-s, -s, s);
        Matrix4f mat = ms.peek().getModel();

        for (int i = 0; i < count; i++) {
            String line = texts[i];
            if (line == null || line.isEmpty()) continue;
            int dist = Math.abs(i - cur);
            int color = dist == 0 ? 0xFFFFFFFF : dist == 1 ? 0xFFB0B0B0 : 0xFF808080;
            float x = -tr.getWidth(line) / 2f;
            float y = (i - cur) * 12f - 4f;
            tr.draw(line, x, y, color, false, mat, consumers, false, 0x40000000, FULL_BRIGHT);
        }
        ms.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate) {
            ((VertexConsumerProvider.Immediate) consumers).draw();
        }
    }
}
