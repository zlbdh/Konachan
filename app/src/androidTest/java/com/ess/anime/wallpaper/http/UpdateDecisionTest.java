package com.ess.anime.wallpaper.http;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Pure version and foreground-state decisions, testable on the JVM and with Android instrumentation. */
public class UpdateDecisionTest {
    @Test public void sameOrOlderVersionDoesNothingInEitherMode() {
        for (boolean automatic : new boolean[]{false, true}) {
            assertEquals(UpdateDecision.Action.NONE, UpdateDecision.decide(35, 35, automatic, true));
            assertEquals(UpdateDecision.Action.NONE, UpdateDecision.decide(35, 34, automatic, true));
        }
    }

    @Test public void newerVersionPromptsWhenAutomaticDownloadIsOff() {
        assertEquals(UpdateDecision.Action.PROMPT, UpdateDecision.decide(34, 35, false, true));
    }

    @Test public void newerVersionDownloadsWhenAutomaticDownloadIsOnAndVisible() {
        assertEquals(UpdateDecision.Action.DOWNLOAD, UpdateDecision.decide(34, 35, true, true));
    }

    @Test public void backgroundOnlyKeepsCacheInEitherMode() {
        assertEquals(UpdateDecision.Action.DEFER, UpdateDecision.decide(34, 35, true, false));
        assertEquals(UpdateDecision.Action.DEFER, UpdateDecision.decide(34, 35, false, false));
    }
}
