package dev.lyricsmod;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

public final class LyricsSettingsScreen extends Screen {
    private static final float[] SCALES = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private final Screen parent;
    private final LyricsConfig cfg;
    private int infoY;

    public LyricsSettingsScreen(Screen parent, LyricsConfig cfg) {
        super(new LiteralText("Lyrics settings"));
        this.parent = parent;
        this.cfg = cfg;
    }

    private static Text onOff(String name, boolean v) {
        return new LiteralText(name + ": " + (v ? "ON" : "OFF"));
    }

    private static Text val(String name, Object v) {
        return new LiteralText(name + ": " + v);
    }

    @Override
    protected void init() {
        int w = 150, h = 20, gap = 24;
        int left = width / 2 - w - 5, right = width / 2 + 5;
        int y = height / 6;

        addButton(new ButtonWidget(left, y, w, h, onOff("HUD", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled;
            b.setMessage(onOff("HUD", cfg.enabled));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("Now playing toast", cfg.showToast), b -> {
            cfg.showToast = !cfg.showToast;
            b.setMessage(onOff("Now playing toast", cfg.showToast));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Style", cfg.style), b -> {
            cfg.style = cfg.style.next();
            b.setMessage(val("Style", cfg.style));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("3D lyrics", cfg.lyrics3d), b -> {
            cfg.lyrics3d = !cfg.lyrics3d;
            b.setMessage(onOff("3D lyrics", cfg.lyrics3d));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Position", cfg.position), b -> {
            cfg.position = cfg.position.next();
            b.setMessage(val("Position", cfg.position));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("Elytra mode", cfg.elytraMode), b -> {
            cfg.elytraMode = !cfg.elytraMode;
            b.setMessage(onOff("Elytra mode", cfg.elytraMode));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Lines", cfg.lines), b -> {
            cfg.lines = cfg.lines >= 5 ? 1 : cfg.lines + 1;
            b.setMessage(val("Lines", cfg.lines));
        }));
        addButton(new ButtonWidget(right, y, w, h, val("Scale", cfg.scale + "x"), b -> {
            int next = 0;
            for (int i = 0; i < SCALES.length; i++) {
                if (Math.abs(SCALES[i] - cfg.scale) < 0.01f) {
                    next = (i + 1) % SCALES.length;
                    break;
                }
            }
            cfg.scale = SCALES[next];
            b.setMessage(val("Scale", cfg.scale + "x"));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, new LiteralText("Lyrics earlier  (-250 ms)"), b -> cfg.offsetMs -= 250));
        addButton(new ButtonWidget(right, y, w, h, new LiteralText("Lyrics later  (+250 ms)"), b -> cfg.offsetMs += 250));
        infoY = y + gap;

        addButton(new ButtonWidget(width / 2 - 100, infoY + 22, 200, h, new LiteralText("Done"), b -> onClose()));
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        renderBackground(m);
        drawCenteredText(m, textRenderer, title, width / 2, 20, 0xFFFFFF);
        drawCenteredText(m, textRenderer, new LiteralText("Sync offset: " + cfg.offsetMs + " ms"), width / 2, infoY + 4, 0xAAAAAA);
        super.render(m, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        cfg.save();
    }

    @Override
    public void onClose() {
        client.openScreen(parent);
    }
}
