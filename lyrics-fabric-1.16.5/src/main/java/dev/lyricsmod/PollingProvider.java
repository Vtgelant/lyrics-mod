package dev.lyricsmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Base class for providers that poll an external command once per second. */
abstract class PollingProvider implements MediaProvider {
    private volatile TrackInfo current;
    private boolean warned;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Lyrics-Poll");
        t.setDaemon(true);
        return t;
    });

    protected final void start() {
        executor.scheduleWithFixedDelay(() -> {
            try {
                current = fetch();
            } catch (Exception e) {
                current = null;
                if (!warned) {
                    warned = true;
                    LyricsMod.LOGGER.warn("Media polling failed: {}", e.toString());
                }
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    protected abstract TrackInfo fetch() throws Exception;

    protected abstract String[] controlCommand(String action);

    @Override
    public TrackInfo current() {
        return current;
    }

    @Override
    public void control(String action) {
        String[] cmd = controlCommand(action);
        if (cmd == null) return;
        executor.submit(() -> {
            try {
                run(cmd);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }

    protected static String run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getOutputStream().close();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        int code = p.waitFor();
        return code == 0 ? sb.toString().trim() : null;
    }

    protected static long parseLong(String s) {
        try {
            return (long) Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0;
        }
    }
}
