package dev.lyricsmod;

/** Snapshot of the currently playing track. Position is interpolated between polls. */
public final class TrackInfo {
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final boolean playing;
    private final long positionMs;
    private final long sampledAtMs;

    public TrackInfo(String title, String artist, String album, long durationMs, long positionMs, boolean playing) {
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
        if (playing) {
            p += System.currentTimeMillis() - sampledAtMs;
        }
        if (durationMs > 0 && p > durationMs) {
            p = durationMs;
        }
        return Math.max(0, p);
    }

    public String key() {
        return artist + "\u0000" + title;
    }
}
