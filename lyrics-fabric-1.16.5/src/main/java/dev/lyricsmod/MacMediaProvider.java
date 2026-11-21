package dev.lyricsmod;

/** macOS: Spotify and Apple Music via AppleScript. */
public final class MacMediaProvider extends PollingProvider {
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

    public MacMediaProvider() {
        start();
    }

    @Override
    protected TrackInfo fetch() throws Exception {
        String out = run("osascript", "-e", POLL);
        if (out == null || out.isEmpty()) return null;
        String[] p = out.split("\\|\\|\\|", -1);
        if (p.length < 6 || p[1].trim().isEmpty()) return null;
        boolean playing = p[5].trim().equalsIgnoreCase("playing");
        return new TrackInfo(p[1].trim(), p[0].trim(), p[2].trim(), parseLong(p[3]), parseLong(p[4]), playing);
    }

    @Override
    protected String[] controlCommand(String action) {
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
