package dev.lyricsmod;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tracks the current song, loads its lyrics in the background and answers "which line is active?". */
public final class LyricsManager {
    private final MediaProvider provider;
    private final ExecutorService fetcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lyrics-Fetch");
        t.setDaemon(true);
        return t;
    });

    private volatile List<LyricLine> lyrics = Collections.emptyList();
    private volatile boolean loading;
    private volatile String currentKey = "";
    private volatile long toastUntil;

    public LyricsManager(MediaProvider provider) {
        this.provider = provider;
    }

    /** Call every client tick. */
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

    public TrackInfo track() {
        return provider == null ? null : provider.current();
    }

    public List<LyricLine> lyrics() {
        return lyrics;
    }

    public boolean isLoading() {
        return loading;
    }

    /** Index of the active line, or -1 before the first one. */
    public int currentIndex(long positionMs) {
        List<LyricLine> l = lyrics;
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).timeMs <= positionMs) idx = i;
            else break;
        }
        return idx;
    }

    /**
     * Lines to show around the active one. The active line is at index count / 2.
     * Entries may be null (nothing to show). Falls back to title/artist when there are no lyrics.
     */
    public String[] visibleLines(TrackInfo t, int count, long offsetMs) {
        String[] texts = new String[count];
        int cur = count / 2;
        List<LyricLine> ls = lyrics;
        if (ls.isEmpty()) {
            if (loading) {
                texts[cur] = "\u266A ...";
            } else {
                texts[cur] = "\u266A " + t.title + (t.artist.isEmpty() ? "" : " - " + t.artist);
            }
            return texts;
        }
        int idx = currentIndex(t.currentPositionMs() + offsetMs);
        for (int i = 0; i < count; i++) {
            int li = idx - cur + i;
            if (li >= 0 && li < ls.size()) texts[i] = ls.get(li).text;
        }
        return texts;
    }

    public void showToast() {
        toastUntil = System.currentTimeMillis() + 6000;
    }

    public boolean toastActive() {
        return System.currentTimeMillis() < toastUntil;
    }

    public void control(String action) {
        if (provider != null) provider.control(action);
    }
}
