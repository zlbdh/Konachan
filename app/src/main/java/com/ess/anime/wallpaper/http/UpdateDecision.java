package com.ess.anime.wallpaper.http;

/** 版本、用户开关与前台状态共同决定检查后的动作。 */
public final class UpdateDecision {
    private UpdateDecision() { }
    public enum Action { NONE, PROMPT, DOWNLOAD, DEFER }
    public static Action decide(int installed, int candidate, boolean automatic, boolean visible) {
        if (candidate <= installed) return Action.NONE;
        if (!visible) return Action.DEFER;
        return automatic ? Action.DOWNLOAD : Action.PROMPT;
    }
}
