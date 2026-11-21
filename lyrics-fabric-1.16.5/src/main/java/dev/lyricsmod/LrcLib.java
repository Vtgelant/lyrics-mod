package dev.lyricsmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches synced lyrics (LRC) from https://lrclib.net and parses them. */
final class LrcLib {
    private static final String BASE = "https://lrclib.net/api/";
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LrcLib() {
    }

    static List<LyricLine> fetch(TrackInfo t) {
        try {
            String artist = t.artist.replace(" - Topic", "").trim();
            long durSec = Math.round(t.durationMs / 1000.0);
            String q = "track_name=" + enc(t.title) + "&artist_name=" + enc(artist);

            String synced = null;
            String exact = http(BASE + "get?" + q
                    + (empty(t.album) ? "" : "&album_name=" + enc(t.album))
                    + (durSec > 0 ? "&duration=" + durSec : ""));
            if (exact != null) {
                synced = str(new JsonParser().parse(exact).getAsJsonObject(), "syncedLyrics");
            }
            if (empty(synced)) {
                String res = http(BASE + "search?" + q);
                if (res != null) {
                    for (JsonElement el : new JsonParser().parse(res).getAsJsonArray()) {
                        JsonObject o = el.getAsJsonObject();
                        String s = str(o, "syncedLyrics");
                        if (empty(s)) continue;
                        double d = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsDouble() : 0;
                        if (durSec <= 0 || d <= 0 || Math.abs(d - durSec) <= 5) {
                            synced = s;
                            break;
                        }
                    }
                }
            }
            if (empty(synced)) return Collections.<LyricLine>emptyList();
            return parse(synced);
        } catch (Exception e) {
            LyricsMod.LOGGER.warn("Lyrics fetch failed: {}", e.toString());
            return Collections.<LyricLine>emptyList();
        }
    }

    static List<LyricLine> parse(String lrc) {
        List<LyricLine> out = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                String f = m.group(3);
                if (f != null) {
                    ms = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
                }
                times.add(min * 60000 + sec * 1000 + ms);
                end = m.end();
            }
            if (times.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long time : times) {
                out.add(new LyricLine(time, text));
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(a.timeMs, b.timeMs));
        return out;
    }

    private static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "LyricsFabricMod/1.0 (Minecraft 1.16.5)");
        try {
            if (c.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
