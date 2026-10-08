package com.ess.anime.wallpaper.http;

/** Version, user preferences, and foreground state determine the action after checking. */
public final class UpdateDecision {
    private UpdateDecision() { }
    public enum Action { NONE, PROMPT, DOWNLOAD, DEFER }
    public static Action decide(int installed, int candidate, boolean automatic, boolean visible) {
        if (candidate <= installed) return Action.NONE;
        if (!visible) return Action.DEFER;
        return automatic ? Action.DOWNLOAD : Action.PROMPT;
    }
}
