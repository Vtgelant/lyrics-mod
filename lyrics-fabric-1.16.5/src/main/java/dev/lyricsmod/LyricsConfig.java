package dev.lyricsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LyricsConfig {
    public enum Style {
        BORDERLESS, BORDERED, COMPACT;

        public Style next() {
            Style[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public enum Position {
        BOTTOM, TOP;

        public Position next() {
            return this == BOTTOM ? TOP : BOTTOM;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean showToast = true;
    public Style style = Style.BORDERLESS;
    public Position position = Position.BOTTOM;
    /** Visible lyric lines (1-5). Ignored by COMPACT, which shows one line. */
    public int lines = 3;
    /** Positive = lyrics appear earlier. Adjust with Ctrl+Left / Ctrl+Right. */
    public long offsetMs = 0;
    public float scale = 1.0f;
    /** Render lyrics as floating text in the world instead of the HUD. */
    public boolean lyrics3d = false;
    /** While gliding with an elytra, place 3D lyrics far ahead of the player. */
    public boolean elytraMode = true;

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
            LyricsMod.LOGGER.warn("Could not read config, using defaults: {}", e.toString());
        }
        LyricsConfig c = new LyricsConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.write(path(), GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LyricsMod.LOGGER.warn("Could not save config: {}", e.toString());
        }
    }
}
