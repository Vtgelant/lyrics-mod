package dev.lyricsmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.client.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

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

        kToggle = register("toggle", GLFW.GLFW_KEY_UP);
        kSettings = register("settings", GLFW.GLFW_KEY_DOWN);
        kPrev = register("prev", GLFW.GLFW_KEY_LEFT);
        kNext = register("next", GLFW.GLFW_KEY_RIGHT);
        kPlay = register("play", GLFW.GLFW_KEY_END);

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
            LOGGER.error("Could not start media provider", e);
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
            msg(mc, "Lyrics: " + (config.enabled ? "ON" : "OFF"));
        }
        while (kSettings.wasPressed()) {
            mc.openScreen(new LyricsSettingsScreen(null, config));
        }
        while (kPrev.wasPressed()) {
            if (Screen.hasControlDown()) {
                config.offsetMs -= 250;
                config.save();
                msg(mc, "Lyrics offset: " + config.offsetMs + " ms");
            } else {
                manager.control("prev");
            }
        }
        while (kNext.wasPressed()) {
            if (Screen.hasControlDown()) {
                config.offsetMs += 250;
                config.save();
                msg(mc, "Lyrics offset: " + config.offsetMs + " ms");
            } else {
                manager.control("next");
            }
        }
        while (kPlay.wasPressed()) {
            manager.control("toggle");
        }
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(new LiteralText(s), false);
        }
    }
}
