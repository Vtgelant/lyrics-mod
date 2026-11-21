package dev.lyricsmod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Windows: reads the system media session (SMTC) through a long-running hidden PowerShell process.
 * Works with Spotify, Apple Music, YouTube Music and browsers (Chrome/Edge/Firefox/Brave).
 */
public final class WindowsMediaProvider implements MediaProvider {
    private volatile TrackInfo current;
    private volatile boolean running = true;
    private volatile Process process;
    private final Path script;
    private final Path cmdFile;

    public WindowsMediaProvider() throws IOException {
        script = Files.createTempFile("lyrics_media", ".ps1");
        cmdFile = script.resolveSibling(script.getFileName().toString() + ".cmd");
        script.toFile().deleteOnExit();
        try (InputStream in = WindowsMediaProvider.class.getResourceAsStream("/lyrics_media.ps1")) {
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        Thread t = new Thread(this::loop, "Lyrics-Media");
        t.setDaemon(true);
        t.start();
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
                        if (i >= 0) {
                            try {
                                current = parse(line.substring(i));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LyricsMod.LOGGER.warn("Media process error: {}", e.toString());
            }
            current = null;
            if (running) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
            }
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

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static long num(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
    }

    @Override
    public TrackInfo current() {
        return current;
    }

    @Override
    public void control(String action) {
        try {
            Files.write(cmdFile, action.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LyricsMod.LOGGER.warn("Could not send media command: {}", e.toString());
        }
    }

    @Override
    public void shutdown() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
        try {
            Files.deleteIfExists(cmdFile);
            Files.deleteIfExists(script);
        } catch (IOException ignored) {
        }
    }
}
