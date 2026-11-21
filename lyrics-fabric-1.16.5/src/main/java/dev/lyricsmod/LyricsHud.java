package dev.lyricsmod;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

public final class LyricsHud implements HudRenderCallback {
    private static final int LINE_H = 12;

    private final LyricsConfig cfg;
    private final LyricsManager mgr;

    public LyricsHud(LyricsConfig cfg, LyricsManager mgr) {
        this.cfg = cfg;
        this.mgr = mgr;
    }

    @Override
    public void onHudRender(MatrixStack m, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.enabled || mc.options.hudHidden || mc.player == null) return;
        TrackInfo t = mgr.track();
        if (t == null) return;

        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        if (cfg.showToast && mgr.toastActive()) {
            drawToast(m, tr, t, sw);
        }
        drawLyrics(m, tr, t, sw, sh);
    }

    private void drawLyrics(MatrixStack m, TextRenderer tr, TrackInfo t, int sw, int sh) {
        boolean compact = cfg.style == LyricsConfig.Style.COMPACT;
        int count = compact ? 1 : Math.max(1, Math.min(5, cfg.lines));
        int cur = count / 2;
        String[] texts = mgr.visibleLines(t, count, cfg.offsetMs);

        float scale = (compact ? 0.85f : 1.0f) * cfg.scale;
        int maxW = (int) (sw * 0.6f / scale);
        int totalH = count * LINE_H;
        int baseY = cfg.position == LyricsConfig.Position.BOTTOM ? sh - 78 - (int) (totalH * scale) : 22;

        int widest = 0;
        for (int i = 0; i < count; i++) {
            if (texts[i] != null && !texts[i].isEmpty()) {
                texts[i] = tr.trimToWidth(texts[i], maxW);
                widest = Math.max(widest, tr.getWidth(texts[i]));
            }
        }

        m.push();
        m.translate(sw / 2.0, baseY, 0);
        m.scale(scale, scale, 1f);

        if (cfg.style == LyricsConfig.Style.BORDERED && widest > 0) {
            int x1 = -widest / 2 - 6, x2 = widest / 2 + 6, y1 = -3, y2 = totalH;
            DrawableHelper.fill(m, x1, y1, x2, y2, 0x90000000);
            int bc = 0xCCFFFFFF;
            DrawableHelper.fill(m, x1, y1, x2, y1 + 1, bc);
            DrawableHelper.fill(m, x1, y2 - 1, x2, y2, bc);
            DrawableHelper.fill(m, x1, y1, x1 + 1, y2, bc);
            DrawableHelper.fill(m, x2 - 1, y1, x2, y2, bc);
        }

        for (int i = 0; i < count; i++) {
            String s = texts[i];
            if (s == null || s.isEmpty()) continue;
            int dist = Math.abs(i - cur);
            int color = dist == 0 ? 0xFFFFFFFF : dist == 1 ? 0xFF9A9A9A : 0xFF666666;
            tr.drawWithShadow(m, s, -tr.getWidth(s) / 2f, i * LINE_H, color);
        }
        m.pop();
    }

    private void drawToast(MatrixStack m, TextRenderer tr, TrackInfo t, int sw) {
        int w = 160, h = 34;
        int x = sw - w - 6, y = 6;
        DrawableHelper.fill(m, x, y, x + w, y + h, 0xB0101010);
        int bc = 0x66FFFFFF;
        DrawableHelper.fill(m, x, y, x + w, y + 1, bc);
        DrawableHelper.fill(m, x, y + h - 1, x + w, y + h, bc);
        DrawableHelper.fill(m, x, y, x + 1, y + h, bc);
        DrawableHelper.fill(m, x + w - 1, y, x + w, y + h, bc);

        tr.drawWithShadow(m, tr.trimToWidth(t.title, w - 12), x + 6, y + 5, 0xFFFFFFFF);
        tr.drawWithShadow(m, tr.trimToWidth(t.artist, w - 12), x + 6, y + 16, 0xFFAAAAAA);

        float p = t.durationMs > 0 ? Math.min(1f, (float) t.currentPositionMs() / t.durationMs) : 0f;
        int bx1 = x + 6, bx2 = x + w - 6, by = y + h - 6;
        DrawableHelper.fill(m, bx1, by, bx2, by + 2, 0xFF444444);
        DrawableHelper.fill(m, bx1, by, bx1 + (int) ((bx2 - bx1) * p), by + 2, t.playing ? 0xFF55FF55 : 0xFFFFAA00);
    }
}
