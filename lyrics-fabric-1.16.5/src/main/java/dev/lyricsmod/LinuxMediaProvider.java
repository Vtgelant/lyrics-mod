package dev.lyricsmod;

/** Linux (MPRIS) via the `playerctl` command-line tool. */
public final class LinuxMediaProvider extends PollingProvider {
    public LinuxMediaProvider() {
        start();
    }

    @Override
    protected TrackInfo fetch() throws Exception {
        String out = run("playerctl", "metadata", "--format",
                "{{artist}}|||{{title}}|||{{album}}|||{{mpris:length}}|||{{position}}|||{{status}}");
        if (out == null || out.isEmpty()) return null;
        String[] p = out.split("\\|\\|\\|", -1);
        if (p.length < 6 || p[1].trim().isEmpty()) return null;
        boolean playing = p[5].trim().equalsIgnoreCase("Playing");
        return new TrackInfo(p[1].trim(), p[0].trim(), p[2].trim(),
                parseLong(p[3]) / 1000, parseLong(p[4]) / 1000, playing);
    }

    @Override
    protected String[] controlCommand(String action) {
        switch (action) {
            case "next": return new String[]{"playerctl", "next"};
            case "prev": return new String[]{"playerctl", "previous"};
            case "toggle": return new String[]{"playerctl", "play-pause"};
            default: return null;
        }
    }
}
