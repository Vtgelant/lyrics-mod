package dev.lyricsmod;

public interface MediaProvider {
    /** Latest known track, or null when nothing is playing. Never blocks. */
    TrackInfo current();

    /** One of: "next", "prev", "toggle". */
    void control(String action);

    void shutdown();
}
