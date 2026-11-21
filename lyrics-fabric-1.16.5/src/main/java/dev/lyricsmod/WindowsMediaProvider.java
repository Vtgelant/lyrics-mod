// ============================================================================
//  LYRICS MOD — ALL-IN-ONE (1.16.5 Fabric)
//  Пакет: dev.lyricsmod
//  Замени все старые .java-файлы этим одним файлом.
// ============================================================================
package dev.lyricsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.client.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ============================================================================
//  ГЛАВНЫЙ КЛАСС
// ============================================================================
public class LyricsMod implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("Lyrics");
    private static final String CATEGORY = "key.categories.lyrics";

    private LyricsConfig config;
    private LyricsManager manager;
    private KeyBinding kToggle, kSettings, kPrev, kNext, kPlay;

    @Override
    public void onInitializeClient() {
        config = LyricsConfig.load();
        MediaProvider provider = createProvider();
        if (provider != null) {
            Runtime.getRuntime().addShutdownHook(new Thread(provider::shutdown, "Lyrics-Shutdown"));
        }
        manager = new LyricsManager(provider);

        kToggle   = register("toggle",   GLFW.GLFW_KEY_UP);
        kSettings = register("settings", GLFW.GLFW_KEY_DOWN);
        kPrev     = register("prev",     GLFW.GLFW_KEY_LEFT);
        kNext     = register("next",     GLFW.GLFW_KEY_RIGHT);
        kPlay     = register("play",     GLFW.GLFW_KEY_END);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(new LyricsHud(config, manager));
        new Lyrics3D(config, manager).register();
    }

    private static MediaProvider createProvider() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) return new WindowsMediaProvider();
            if (os.contains("mac")) return new MacMediaProvider();
            return new LinuxMediaProvider();
        } catch (Exception e) {
            LOGGER.error("Не удалось запустить медиапровайдер", e);
            return null;
        }
    }

    private static KeyBinding register(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.lyrics." + id, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    private void onTick(MinecraftClient mc) {
        manager.tick();
        while (kToggle.wasPressed()) {
            config.enabled = !config.enabled;
            config.save();
            if (config.enabled) manager.showToast();
            msg(mc, "Текст песен: " + (config.enabled ? "ВКЛ" : "ВЫКЛ"));
        }
        while (kSettings.wasPressed()) {
            mc.openScreen(new LyricsSettingsScreen(null, config));
        }
        while (kPrev.wasPressed()) {
            if (Screen.hasControlDown()) {
                config.offsetMs -= 250; config.save();
                msg(mc, "Синхронизация: " + config.offsetMs + " мс");
            } else manager.control("prev");
        }
        while (kNext.wasPressed()) {
            if (Screen.hasControlDown()) {
                config.offsetMs += 250; config.save();
                msg(mc, "Синхронизация: " + config.offsetMs + " мс");
            } else manager.control("next");
        }
        while (kPlay.wasPressed()) manager.control("toggle");
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(new LiteralText(s), false);
    }
}

// ============================================================================
//  КОНФИГ
// ============================================================================
final class LyricsConfig {
    public enum Style { BORDERLESS, BORDERED, COMPACT;
        public Style next() { Style[] v = values(); return v[(ordinal()+1)%v.length]; }
    }
    public enum Position { BOTTOM, TOP;
        public Position next() { return this == BOTTOM ? TOP : BOTTOM; }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean showToast = true;
    public Style style = Style.BORDERLESS;
    public Position position = Position.BOTTOM;
    public int lines = 3;
    public long offsetMs = 0;
    public float scale = 1.0f;
    public boolean lyrics3d = false;
    public boolean elytraMode = true;

    // ==== НОВЫЕ НАСТРОЙКИ 3D-ТЕКСТА ====
    public float depth = 2.0f;
    public int depthColor = 0x000000;
    public float depthAlpha = 0.8f;

    public boolean outlineEnabled = true;
    public int outlineColor = 0x000000;
    public float outlineThickness = 1.0f;

    public int baseColor = 0xFFFFFF;

    public boolean wordByWord = false;
    public float appearSpeed = 0.15f;
    public boolean highlightActive = true;
    public float highlightScale = 1.15f;
    public float highlightSaturationBoost = 0.4f;
    public int highlightColor = 0xFFEE55;
    public float highlightHoldTime = 0.6f;

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lyrics.json");
    }

    public static LyricsConfig load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                LyricsConfig c = GSON.fromJson(new String(Files.readAllBytes(p), StandardCharsets.UTF_8), LyricsConfig.class);
                if (c != null) {
                    if (c.style == null) c.style = Style.BORDERLESS;
                    if (c.position == null) c.position = Position.BOTTOM;
                    return c;
                }
            }
        } catch (Exception e) {
            LyricsMod.LOGGER.warn("Не удалось прочитать конфиг: {}", e.toString());
        }
        LyricsConfig c = new LyricsConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.write(path(), GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LyricsMod.LOGGER.warn("Не удалось сохранить конфиг: {}", e.toString());
        }
    }
}

// ============================================================================
//  МЕНЕДЖЕР
// ============================================================================
final class LyricsManager {
    private final MediaProvider provider;
    private final ExecutorService fetcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lyrics-Fetch"); t.setDaemon(true); return t;
    });
    private volatile List<LyricLine> lyrics = Collections.emptyList();
    private volatile boolean loading;
    private volatile String currentKey = "";
    private volatile long toastUntil;

    LyricsManager(MediaProvider provider) { this.provider = provider; }

    public void tick() {
        if (provider == null) return;
        TrackInfo t = provider.current();
        String key = t == null ? "" : t.key();
        if (key.equals(currentKey)) return;
        currentKey = key;
        lyrics = Collections.emptyList();
        loading = false;
        if (t == null) return;
        showToast();
        loading = true;
        fetcher.submit(() -> {
            List<LyricLine> result = LrcLib.fetch(t);
            if (key.equals(currentKey)) {
                lyrics = result;
                loading = false;
            }
        });
    }

    public TrackInfo track() { return provider == null ? null : provider.current(); }
    public List<LyricLine> lyrics() { return lyrics; }
    public boolean isLoading() { return loading; }

    public int currentIndex(long positionMs) {
        List<LyricLine> l = lyrics;
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).timeMs <= positionMs) idx = i; else break;
        }
        return idx;
    }

    public String[] visibleLines(TrackInfo t, int count, long offsetMs) {
        String[] texts = new String[count];
        int cur = count / 2;
        List<LyricLine> ls = lyrics;
        if (ls.isEmpty()) {
            texts[cur] = loading ? "\u266A ..."
                    : "\u266A " + t.title + (t.artist.isEmpty() ? "" : " - " + t.artist);
            return texts;
        }
        int idx = currentIndex(t.currentPositionMs() + offsetMs);
        for (int i = 0; i < count; i++) {
            int li = idx - cur + i;
            if (li >= 0 && li < ls.size()) texts[i] = ls.get(li).text;
        }
        return texts;
    }

    public void showToast() { toastUntil = System.currentTimeMillis() + 6000; }
    public boolean toastActive() { return System.currentTimeMillis() < toastUntil; }
    public void control(String action) { if (provider != null) provider.control(action); }
}

// ============================================================================
//  HUD
// ============================================================================
final class LyricsHud implements HudRenderCallback {
    private static final int LINE_H = 12;
    private final LyricsConfig cfg;
    private final LyricsManager mgr;

    LyricsHud(LyricsConfig cfg, LyricsManager mgr) { this.cfg = cfg; this.mgr = mgr; }

    @Override
    public void onHudRender(MatrixStack m, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.enabled || mc.options.hudHidden || mc.player == null) return;
        TrackInfo t = mgr.track();
        if (t == null) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (cfg.showToast && mgr.toastActive()) drawToast(m, tr, t, sw);
        if (!cfg.lyrics3d) drawLyrics(m, tr, t, sw, sh);
    }

    private void drawLyrics(MatrixStack m, TextRenderer tr, TrackInfo t, int sw, int sh) {
        boolean compact = cfg.style == LyricsConfig.Style.COMPACT;
        int count = compact ? 1 : Math.max(1, Math.min(5, cfg.lines));
        int cur = count / 2;
        String[] texts = mgr.visibleLines(t, count, cfg.offsetMs);
        float scale = (compact ? 0.85f : 1.0f) * cfg.scale;
        int maxW = (int)(sw * 0.6f / scale);
        int totalH = count * LINE_H;
        int baseY = cfg.position == LyricsConfig.Position.BOTTOM
                ? sh - 78 - (int)(totalH * scale) : 22;

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
            int x1 = -widest/2 - 6, x2 = widest/2 + 6, y1 = -3, y2 = totalH;
            DrawableHelper.fill(m, x1, y1, x2, y2, 0x90000000);
            int bc = 0xCCFFFFFF;
            DrawableHelper.fill(m, x1, y1, x2, y1+1, bc);
            DrawableHelper.fill(m, x1, y2-1, x2, y2, bc);
            DrawableHelper.fill(m, x1, y1, x1+1, y2, bc);
            DrawableHelper.fill(m, x2-1, y1, x2, y2, bc);
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String s = texts[i];
            if (s == null || s.isEmpty()) continue;
            int dist = Math.abs(i - cur);
            boolean isCurrent = dist == 0;

            if (isCurrent && cfg.highlightActive && !cfg.lyrics3d) {
                // Эффект "по слову" + пульс насыщенности/размера
                drawWordByWord(m, tr, s, i * LINE_H, now);
            } else {
                int color = isCurrent ? 0xFFFFFFFF : dist == 1 ? 0xFF9A9A9A : 0xFF666666;
                if (cfg.depth > 0.1f) {
                    int layers = Math.max(1, (int)cfg.depth);
                    for (int d = layers; d >= 1; d--)
                        tr.drawWithShadow(m, s, -tr.getWidth(s)/2f + d, i*LINE_H + d,
                                withAlpha(cfg.depthColor, (int)(cfg.depthAlpha*255)), false);
                }
                if (cfg.outlineEnabled) {
                    float t2 = cfg.outlineThickness;
                    int oc = withAlpha(cfg.outlineColor, 255);
                    float[] dirs = {-t2,0, t2,0, 0,-t2, 0,t2, -t2,-t2, t2,-t2, -t2,t2, t2,t2};
                    for (int k = 0; k < dirs.length; k += 2)
                        tr.draw(m, s, -tr.getWidth(s)/2f + dirs[k], i*LINE_H + dirs[k+1], oc);
                }
                tr.drawWithShadow(m, s, -tr.getWidth(s)/2f, i*LINE_H, color);
            }
        }
        m.pop();
    }

    private void drawWordByWord(MatrixStack m, TextRenderer tr, String line, float y, long now) {
        String[] words = line.split(" ");
        float totalW = tr.getWidth(line);
        float x = -totalW / 2f;
        long cycle = (now % 6000L);
        int activeIdx = (int)(cycle / Math.max(200, (long)(cfg.appearSpeed * 1000)));

        for (int w = 0; w < words.length; w++) {
            String word = words[w] + (w < words.length - 1 ? " " : "");
            float ww = tr.getWidth(word);
            boolean active = (w == activeIdx) && cfg.highlightActive;
            float sc = active ? cfg.highlightScale : 1.0f;
            int color = active
                    ? boostSaturation(cfg.highlightColor, cfg.highlightSaturationBoost)
                    : cfg.baseColor;

            m.push();
            m.translate(x + ww/2f, y + tr.fontHeight/2f, 0);
            m.scale(sc, sc, 1f);
            m.translate(-(x + ww/2f), -(y + tr.fontHeight/2f), 0);

            if (cfg.depth > 0.1f) {
                int layers = Math.max(1, (int)cfg.depth);
                for (int d = layers; d >= 1; d--)
                    tr.draw(m, word, x + d, y + d,
                            withAlpha(cfg.depthColor, (int)(cfg.depthAlpha*255)));
            }
            if (cfg.outlineEnabled) {
                float t2 = cfg.outlineThickness;
                int oc = withAlpha(cfg.outlineColor, 255);
                float[] dirs = {-t2,0, t2,0, 0,-t2, 0,t2, -t2,-t2, t2,-t2, -t2,t2, t2,t2};
                for (int k = 0; k < dirs.length; k += 2)
                    tr.draw(m, word, x + dirs[k], y + dirs[k+1], oc);
            }
            tr.drawWithShadow(m, word, x, y, color);
            m.pop();
            x += ww;
        }
    }

    private static int withAlpha(int rgb, int a) {
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int boostSaturation(int rgb, float amount) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb>>16)&0xFF, (rgb>>8)&0xFF, rgb&0xFF, null);
        hsb[1] = Math.min(1f, hsb[1] + amount);
        int c = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return c & 0xFFFFFF;
    }

    private void drawToast(MatrixStack m, TextRenderer tr, TrackInfo t, int sw) {
        int w = 160, h = 34, x = sw - w - 6, y = 6;
        DrawableHelper.fill(m, x, y, x+w, y+h, 0xB0101010);
        int bc = 0x66FFFFFF;
        DrawableHelper.fill(m, x, y, x+w, y+1, bc);
        DrawableHelper.fill(m, x, y+h-1, x+w, y+h, bc);
        DrawableHelper.fill(m, x, y, x+1, y+h, bc);
        DrawableHelper.fill(m, x+w-1, y, x+w, y+h, bc);
        tr.drawWithShadow(m, tr.trimToWidth(t.title, w-12), x+6, y+5, 0xFFFFFFFF);
        tr.drawWithShadow(m, tr.trimToWidth(t.artist, w-12), x+6, y+16, 0xFFAAAAAA);
        float p = t.durationMs > 0 ? Math.min(1f, (float) t.currentPositionMs() / t.durationMs) : 0f;
        int bx1 = x+6, bx2 = x+w-6, by = y+h-6;
        DrawableHelper.fill(m, bx1, by, bx2, by+2, 0xFF444444);
        DrawableHelper.fill(m, bx1, by, bx1 + (int)((bx2-bx1)*p), by+2, t.playing ? 0xFF55FF55 : 0xFFFFAA00);
    }
}

// ============================================================================
//  3D-ТЕКСТ В МИРЕ
// ============================================================================
final class Lyrics3D {
    private static final int FULL_BRIGHT = 0xF000F0;
    private final LyricsConfig cfg;
    private final LyricsManager mgr;
    private Vec3d anchor;

    Lyrics3D(LyricsConfig cfg, LyricsManager mgr) { this.cfg = cfg; this.mgr = mgr; }

    public void register() { WorldRenderEvents.AFTER_ENTITIES.register(this::render); }

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
        if (elytra) { desired = eye.add(look.multiply(16.0)); smoothing = 0.3; }
        else {
            Vec3d flat = new Vec3d(look.x, 0, look.z);
            flat = flat.lengthSquared() < 1.0E-4 ? (anchor == null ? new Vec3d(0,0,1) : flat) : flat.normalize();
            if (flat.lengthSquared() < 1.0E-4) return;
            desired = eye.add(flat.multiply(5.0)).add(0, 0.3, 0);
            smoothing = 0.08;
        }
        if (anchor == null || anchor.squaredDistanceTo(desired) > 900) anchor = desired;
        else anchor = anchor.add(desired.subtract(anchor).multiply(smoothing));

        int count = cfg.style == LyricsConfig.Style.COMPACT ? 1 : Math.max(1, Math.min(5, cfg.lines));
        int cur = count / 2;
        String[] texts = mgr.visibleLines(t, count, cfg.offsetMs);
        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        float s = (float)(anchor.distanceTo(eye) * 0.006 * cfg.scale);
        TextRenderer tr = mc.textRenderer;

        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(anchor.x - cp.x, anchor.y - cp.y, anchor.z - cp.z);
        ms.multiply(cam.getRotation());
        ms.scale(-s, -s, s);
        Matrix4f mat = ms.peek().getModel();

        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String line = texts[i];
            if (line == null || line.isEmpty()) continue;
            int dist = Math.abs(i - cur);
            boolean isCurrent = dist == 0;
            int color = isCurrent ? cfg.baseColor : dist == 1 ? 0xB0B0B0 : 0x808080;
            float x = -tr.getWidth(line) / 2f;
            float y = (i - cur) * 12f - 4f;

            if (isCurrent && cfg.highlightActive && cfg.wordByWord) {
                String[] words = line.split(" ");
                float totalW = tr.getWidth(line);
                float wx = -totalW / 2f;
                long cycle = now % 6000L;
                int activeIdx = (int)(cycle / Math.max(200, (long)(cfg.appearSpeed * 1000)));
                for (int w = 0; w < words.length; w++) {
                    String word = words[w] + (w < words.length - 1 ? " " : "");
                    float ww = tr.getWidth(word);
                    boolean active = (w == activeIdx);
                    float sc = active ? cfg.highlightScale : 1.0f;
                    int col = active ? boostSaturation(cfg.highlightColor, cfg.highlightSaturationBoost) : color;
                    ms.push();
                    ms.translate(wx + ww/2f, y + tr.fontHeight/2f, 0);
                    ms.scale(sc, sc, 1f);
                    ms.translate(-(wx + ww/2f), -(y + tr.fontHeight/2f), 0);
                    Matrix4f mw = ms.peek().getModel();
                    tr.draw(word, wx, y, withAlpha(col, 255), false, mw, consumers, false, 0x40000000, FULL_BRIGHT);
                    ms.pop();
                    wx += ww;
                }
            } else {
                if (cfg.depth > 0.1f) {
                    int layers = Math.max(1, (int)cfg.depth);
                    for (int d = layers; d >= 1; d--)
                        tr.draw(line, x + d, y + d,
                                withAlpha(cfg.depthColor, (int)(cfg.depthAlpha*255)),
                                false, mat, consumers, false, 0, FULL_BRIGHT);
                }
                if (cfg.outlineEnabled) {
                    float t2 = cfg.outlineThickness;
                    int oc = withAlpha(cfg.outlineColor, 255);
                    float[] dirs = {-t2,0, t2,0, 0,-t2, 0,t2, -t2,-t2, t2,-t2, -t2,t2, t2,t2};
                    for (int k = 0; k < dirs.length; k += 2)
                        tr.draw(line, x + dirs[k], y + dirs[k+1], oc,
                                false, mat, consumers, false, 0, FULL_BRIGHT);
                }
                tr.draw(line, x, y, withAlpha(color, 255), false, mat, consumers, false, 0x40000000, FULL_BRIGHT);
            }
        }
        ms.pop();
        if (consumers instanceof VertexConsumerProvider.Immediate) {
            ((VertexConsumerProvider.Immediate) consumers).draw();
        }
    }

    private static int withAlpha(int rgb, int a) {
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int boostSaturation(int rgb, float amount) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb>>16)&0xFF, (rgb>>8)&0xFF, rgb&0xFF, null);
        hsb[1] = Math.min(1f, hsb[1] + amount);
        return java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) & 0xFFFFFF;
    }
}

// ============================================================================
//  РУССКОЕ МЕНЮ НАСТРОЕК
// ============================================================================
final class LyricsSettingsScreen extends Screen {
    private final Screen parent;
    private final LyricsConfig cfg;
    private int infoY;

    LyricsSettingsScreen(Screen parent, LyricsConfig cfg) {
        super(new LiteralText("Настройки текста песен"));
        this.parent = parent;
        this.cfg = cfg;
    }

    private static Text onOff(String name, boolean v) { return new LiteralText(name + ": " + (v ? "ВКЛ" : "ВЫКЛ")); }
    private static Text val(String name, Object v) { return new LiteralText(name + ": " + v); }
    private static String f2(double v) { return String.format("%.2f", v); }

    @Override
    protected void init() {
        int w = 150, h = 20, gap = 24;
        int left = width/2 - w - 5, right = width/2 + 5;
        int y = 30;

        // --- Основное ---
        addButton(new ButtonWidget(left, y, w, h, onOff("Текст песен", cfg.enabled), b -> {
            cfg.enabled = !cfg.enabled; b.setMessage(onOff("Текст песен", cfg.enabled));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("Уведомление о треке", cfg.showToast), b -> {
            cfg.showToast = !cfg.showToast; b.setMessage(onOff("Уведомление о треке", cfg.showToast));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Стиль", cfg.style), b -> {
            cfg.style = cfg.style.next(); b.setMessage(val("Стиль", cfg.style));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("3D-текст", cfg.lyrics3d), b -> {
            cfg.lyrics3d = !cfg.lyrics3d; b.setMessage(onOff("3D-текст", cfg.lyrics3d));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Позиция", cfg.position == LyricsConfig.Position.BOTTOM ? "СНИЗУ" : "СВЕРХУ"), b -> {
            cfg.position = cfg.position.next();
            b.setMessage(val("Позиция", cfg.position == LyricsConfig.Position.BOTTOM ? "СНИЗУ" : "СВЕРХУ"));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("Режим элитр", cfg.elytraMode), b -> {
            cfg.elytraMode = !cfg.elytraMode; b.setMessage(onOff("Режим элитр", cfg.elytraMode));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Строк", cfg.lines), b -> {
            cfg.lines = cfg.lines >= 5 ? 1 : cfg.lines + 1; b.setMessage(val("Строк", cfg.lines));
        }));
        addButton(new ButtonWidget(right, y, w, h, val("Размер", f2(cfg.scale) + "x"), b -> {
            float[] S = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
            int n = 0;
            for (int i = 0; i < S.length; i++) if (Math.abs(S[i]-cfg.scale) < 0.01f) { n = (i+1)%S.length; break; }
            cfg.scale = S[n]; b.setMessage(val("Размер", f2(cfg.scale) + "x"));
        }));

        // --- 3D-текст ---
        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Глубина 3D", f2(cfg.depth)), b -> {
            cfg.depth = (cfg.depth + 0.5f) % 6f; b.setMessage(val("Глубина 3D", f2(cfg.depth)));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("Обводка", cfg.outlineEnabled), b -> {
            cfg.outlineEnabled = !cfg.outlineEnabled; b.setMessage(onOff("Обводка", cfg.outlineEnabled));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Толщина обводки", f2(cfg.outlineThickness)), b -> {
            cfg.outlineThickness = (cfg.outlineThickness + 0.5f) % 5f; b.setMessage(val("Толщина обводки", f2(cfg.outlineThickness)));
        }));
        addButton(new ButtonWidget(right, y, w, h, val("Цвет обводки", Integer.toHexString(cfg.outlineColor).toUpperCase()), b -> {
            cfg.outlineColor = nextColor(cfg.outlineColor); b.setMessage(val("Цвет обводки", Integer.toHexString(cfg.outlineColor).toUpperCase()));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Цвет текста", Integer.toHexString(cfg.baseColor).toUpperCase()), b -> {
            cfg.baseColor = nextColor(cfg.baseColor); b.setMessage(val("Цвет текста", Integer.toHexString(cfg.baseColor).toUpperCase()));
        }));
        addButton(new ButtonWidget(right, y, w, h, onOff("По слову", cfg.wordByWord), b -> {
            cfg.wordByWord = !cfg.wordByWord; b.setMessage(onOff("По слову", cfg.wordByWord));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, onOff("Подсветка слова", cfg.highlightActive), b -> {
            cfg.highlightActive = !cfg.highlightActive; b.setMessage(onOff("Подсветка слова", cfg.highlightActive));
        }));
        addButton(new ButtonWidget(right, y, w, h, val("Увеличение", "x" + f2(cfg.highlightScale)), b -> {
            cfg.highlightScale = 1.0f + ((cfg.highlightScale - 1.0f + 0.05f) % 0.9f); b.setMessage(val("Увеличение", "x" + f2(cfg.highlightScale)));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Насыщенность", "+" + f2(cfg.highlightSaturationBoost)), b -> {
            cfg.highlightSaturationBoost = (cfg.highlightSaturationBoost + 0.1f) % 1.1f; b.setMessage(val("Насыщенность", "+" + f2(cfg.highlightSaturationBoost)));
        }));
        addButton(new ButtonWidget(right, y, w, h, val("Скорость", f2(cfg.appearSpeed) + "с"), b -> {
            cfg.appearSpeed = (cfg.appearSpeed + 0.05f) % 1.05f; b.setMessage(val("Скорость", f2(cfg.appearSpeed) + "с"));
        }));

        y += gap;
        addButton(new ButtonWidget(left, y, w, h, val("Цвет подсветки", Integer.toHexString(cfg.highlightColor).toUpperCase()), b -> {
            cfg.highlightColor = nextColor(cfg.highlightColor); b.setMessage(val("Цвет подсветки", Integer.toHexString(cfg.highlightColor).toUpperCase()));
        }));
        addButton(new ButtonWidget(right, y, w, h, new LiteralText("Текст раньше (-250 мс)"), b -> cfg.offsetMs -= 250));
        infoY = y + gap;
        addButton(new ButtonWidget(width/2 - 100, infoY + 22, 200, h, new LiteralText("Готово"), b -> onClose()));
    }

    private static int nextColor(int c) {
        int[] palette = {0x000000, 0xFFFFFF, 0xFF5555, 0x55FF55, 0x5555FF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFAA00, 0xAA00FF};
        for (int i = 0; i < palette.length; i++) if (palette[i] == c) return palette[(i+1)%palette.length];
        return palette[0];
    }

    @Override
    public void render(MatrixStack m, int mouseX, int mouseY, float delta) {
        renderBackground(m);
        drawCenteredText(m, textRenderer, title, width/2, 12, 0xFFFFFF);
        drawCenteredText(m, textRenderer, new LiteralText("Синхронизация: " + cfg.offsetMs + " мс"), width/2, infoY + 4, 0xAAAAAA);
        super.render(m, mouseX, mouseY, delta);
    }

    @Override public void removed() { cfg.save(); }
    @Override public void onClose() { client.openScreen(parent); }
}

// ============================================================================
//  ИСТОЧНИКИ ДАННЫХ
// ============================================================================
interface MediaProvider {
    TrackInfo current();
    void control(String action);
    void shutdown();
}

final class TrackInfo {
    public final String title, artist, album;
    public final long durationMs;
    public final boolean playing;
    private final long positionMs, sampledAtMs;

    TrackInfo(String title, String artist, String album, long durationMs, long positionMs, boolean playing) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = Math.max(0, durationMs);
        this.positionMs = Math.max(0, positionMs);
        this.playing = playing;
        this.sampledAtMs = System.currentTimeMillis();
    }
    public long currentPositionMs() {
        long p = positionMs;
        if (playing) p += System.currentTimeMillis() - sampledAtMs;
        if (durationMs > 0 && p > durationMs) p = durationMs;
        return Math.max(0, p);
    }
    public String key() { return artist + "\u0000" + title; }
}

final class LyricLine {
    public final long timeMs;
    public final String text;
    LyricLine(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
}

// ============================================================================
//  LRC-ПАРСЕР
// ============================================================================
final class LrcLib {
    private static final String BASE = "https://lrclib.net/api/";
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private LrcLib() {}

    static List<LyricLine> fetch(TrackInfo t) {
        try {
            String artist = t.artist.replace(" - Topic", "").trim();
            long durSec = Math.round(t.durationMs / 1000.0);
            String q = "track_name=" + enc(t.title) + "&artist_name=" + enc(artist);
            String synced = null;
            String exact = http(BASE + "get?" + q
                    + (empty(t.album) ? "" : "&album_name=" + enc(t.album))
                    + (durSec > 0 ? "&duration=" + durSec : ""));
            if (exact != null) synced = str(new JsonParser().parse(exact).getAsJsonObject(), "syncedLyrics");
            if (empty(synced)) {
                String res = http(BASE + "search?" + q);
                if (res != null) {
                    for (JsonElement el : new JsonParser().parse(res).getAsJsonArray()) {
                        JsonObject o = el.getAsJsonObject();
                        String s = str(o, "syncedLyrics");
                        if (empty(s)) continue;
                        double d = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
                        if (durSec <= 0 || d <= 0 || Math.abs(d - durSec) <= 5) { synced = s; break; }
                    }
                }
            }
            if (empty(synced)) return Collections.emptyList();
            return parse(synced);
        } catch (Exception e) {
            LyricsMod.LOGGER.warn("Ошибка загрузки текста: {}", e.toString());
            return Collections.emptyList();
        }
    }

    static List<LyricLine> parse(String lrc) {
        List<LyricLine> out = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2)), ms = 0;
                String f = m.group(3);
                if (f != null) ms = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
                times.add(min*60000 + sec*1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long time : times) out.add(new LyricLine(time, text));
        }
        Collections.sort(out, (a,b) -> Long.compare(a.timeMs, b.timeMs));
        return out;
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "LyricsFabricMod/1.0 (Minecraft 1.16.5)");
        try {
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } finally { c.disconnect(); }
    }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    static boolean empty(String s) { return s == null || s.trim().isEmpty(); }
}

// ============================================================================
//  ПРОВАЙДЕРЫ
// ============================================================================
abstract class PollingProvider implements MediaProvider {
    private volatile TrackInfo current;
    private boolean warned;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Lyrics-Poll"); t.setDaemon(true); return t;
    });
    protected final void start() {
        executor.scheduleWithFixedDelay(() -> {
            try { current = fetch(); }
            catch (Exception e) {
                current = null;
                if (!warned) { warned = true; LyricsMod.LOGGER.warn("Ошибка опроса: {}", e.toString()); }
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }
    protected abstract TrackInfo fetch() throws Exception;
    protected abstract String[] controlCommand(String action);
    @Override public TrackInfo current() { return current; }
    @Override public void control(String action) {
        String[] cmd = controlCommand(action);
        if (cmd == null) return;
        executor.submit(() -> { try { run(cmd); } catch (Exception ignored) {} });
    }
    @Override public void shutdown() { executor.shutdownNow(); }
    protected static String run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        int code = p.waitFor();
        return code == 0 ? sb.toString().trim() : null;
    }
    protected static long parseLong(String s) {
        try { return (long) Double.parseDouble(s.trim().replace(',', '.')); }
        catch (Exception e) { return 0; }
    }
}

final class LinuxMediaProvider extends PollingProvider {
    public LinuxMediaProvider() { start(); }
    @Override protected TrackInfo fetch() throws Exception {
        String out = run("playerctl", "metadata", "--format",
                "{{artist}}|||{{title}}|||{{album}}|||{{mpris:length}}|||{{position}}|||{{status}}");
        if (out == null || out.isEmpty()) return null;
        String[] p = out.split("\\|\\|\\|", -1);
        if (p.length < 6 || p[1].trim().isEmpty()) return null;
        boolean playing = p[5].trim().equalsIgnoreCase("Playing");
        return new TrackInfo(p[1].trim(), p[0].trim(), p[2].trim(),
                parseLong(p[3])/1000, parseLong(p[4])/1000, playing);
    }
    @Override protected String[] controlCommand(String action) {
        switch (action) {
            case "next": return new String[]{"playerctl", "next"};
            case "prev": return new String[]{"playerctl", "previous"};
            case "toggle": return new String[]{"playerctl", "play-pause"};
            default: return null;
        }
    }
}

final class MacMediaProvider extends PollingProvider {
    private static final String POLL =
            "set sep to \"|||\"\n"
            + "if application \"Spotify\" is running then\n"
            + "  tell application \"Spotify\"\n"
            + "    if player state is not stopped then\n"
            + "      return (artist of current track) & sep & (name of current track) & sep & (album of current track) & sep & (duration of current track) & sep & (((player position * 1000)) as integer) & sep & (player state as string)\n"
            + "    end if\n"
            + "  end tell\n"
            + "end if\n"
            + "if application \"Music\" is running then\n"
            + "  tell application \"Music\"\n"
            + "    if player state is not stopped then\n"
            + "      return (artist of current track) & sep & (name of current track) & sep & (album of current track) & sep & (((duration of current track) * 1000) as integer) & sep & (((player position * 1000)) as integer) & sep & (player state as string)\n"
            + "    end if\n"
            + "  end tell\n"
            + "end if\n"
            + "return \"\"";
    public MacMediaProvider() { start(); }
    @Override protected TrackInfo fetch() throws Exception {
        String out = run("osascript", "-e", POLL);
        if (out == null || out.isEmpty()) return null;
        String[] p = out.split("\\|\\|\\|", -1);
        if (p.length < 6 || p[1].trim().isEmpty()) return null;
        boolean playing = p[5].trim().equalsIgnoreCase("playing");
        return new TrackInfo(p[1].trim(), p[0].trim(), p[2].trim(), parseLong(p[3]), parseLong(p[4]), playing);
    }
    @Override protected String[] controlCommand(String action) {
        String cmd;
        switch (action) {
            case "next": cmd = "next track"; break;
            case "prev": cmd = "previous track"; break;
            case "toggle": cmd = "playpause"; break;
            default: return null;
        }
        String script = "if application \"Spotify\" is running then\n"
                + "tell application \"Spotify\" to " + cmd + "\n"
                + "else if application \"Music\" is running then\n"
                + "tell application \"Music\" to " + cmd + "\n"
                + "end if";
        return new String[]{"osascript", "-e", script};
    }
}

final class WindowsMediaProvider implements MediaProvider {
    private volatile TrackInfo current;
    private volatile boolean running = true;
    private volatile Process process;
    private final Path script, cmdFile;

    public WindowsMediaProvider() throws IOException {
        script = Files.createTempFile("lyrics_media", ".ps1");
        cmdFile = script.resolveSibling(script.getFileName().toString() + ".cmd");
        script.toFile().deleteOnExit();
        try (InputStream in = WindowsMediaProvider.class.getResourceAsStream("/lyrics_media.ps1")) {
            Files.copy(in, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Thread t = new Thread(this::loop, "Lyrics-Media"); t.setDaemon(true); t.start();
    }
    private void loop() {
        while (running) {
            try {
                process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                        "-File", script.toString(), "-CmdFile", cmdFile.toString())
                        .redirectErrorStream(true).start();
                process.getOutputStream().close();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) {
                        int i = line.indexOf('{');
                        if (i >= 0) { try { current = parse(line.substring(i)); } catch (Exception ignored) {} }
                    }
                }
            } catch (Exception e) {
                LyricsMod.LOGGER.warn("Ошибка медиапроцесса: {}", e.toString());
            }
            current = null;
            if (running) try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
        }
    }
    private static TrackInfo parse(String json) {
        JsonObject o = new JsonParser().parse(json).getAsJsonObject();
        if (o.has("none")) return null;
        String title = str(o, "title");
        if (title.isEmpty()) return null;
        boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
        return new TrackInfo(title, str(o, "artist"), str(o, "album"), num(o, "dur"), num(o, "pos"), playing);
    }
    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static long num(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0; }
    @Override public TrackInfo current() { return current; }
    @Override public void control(String action) {
        try { Files.write(cmdFile, action.getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { LyricsMod.LOGGER.warn("Не удалось отправить команду: {}", e.toString()); }
    }
    @Override public void shutdown() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
        try { Files.deleteIfExists(cmdFile); Files.deleteIfExists(script); } catch (IOException ignored) {}
    }
}