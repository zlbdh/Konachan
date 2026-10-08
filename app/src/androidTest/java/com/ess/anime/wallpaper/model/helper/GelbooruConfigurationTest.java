package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.website.GelbooruConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import static org.junit.Assert.assertTrue;

/** Use only invalid test strings to verify parameter construction; do not call external APIs that require an account. */
@RunWith(AndroidJUnit4.class)
public class GelbooruConfigurationTest {
    private SharedPreferences preferences;
    private String previousKey, previousUser;
    @Before public void remember() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        previousKey = preferences.getString(Constants.GELBOORU_API_KEY, null);
        previousUser = preferences.getString(Constants.GELBOORU_USER_ID, null);
    }
    @After public void restore() {
        SharedPreferences.Editor editor = preferences.edit();
        if (previousKey == null) editor.remove(Constants.GELBOORU_API_KEY);
        else editor.putString(Constants.GELBOORU_API_KEY, previousKey);
        if (previousUser == null) editor.remove(Constants.GELBOORU_USER_ID);
        else editor.putString(Constants.GELBOORU_USER_ID, previousUser);
        assertTrue(editor.commit());
    }
    @Test public void sameConfigReadsSavedCredentialsOnNextRequest() {
        GelbooruConfig config = new GelbooruConfig();
        save("VERIFY_ONLY_INVALID_KEY", "0");
        Uri first = Uri.parse(config.getPostUrl(1, Collections.emptyList()));
        assertTrue("The next request must use the saved test key", "VERIFY_ONLY_INVALID_KEY".equals(first.getQueryParameter("api_key")));
        assertTrue("The next request must use the saved user_id", "0".equals(first.getQueryParameter("user_id")));
        save("SECOND_INVALID_TEST_KEY", "12345");
        Uri second = Uri.parse(config.getPostUrl(1, Collections.emptyList()));
        assertTrue("Do not keep using the old value after saving", "SECOND_INVALID_TEST_KEY".equals(second.getQueryParameter("api_key")));
        assertTrue("Saving must also update user_id", "12345".equals(second.getQueryParameter("user_id")));
    }
    @Test public void incompleteCustomPairUsesFallbackWithoutLeakingIt() {
        GelbooruConfig config = new GelbooruConfig();
        save("", "");
        Uri fallback = Uri.parse(config.getPostUrl(1, Collections.emptyList()));
        save("VERIFY_ONLY_INVALID_KEY", "");
        Uri incomplete = Uri.parse(config.getPostUrl(1, Collections.emptyList()));
        assertTrue("Incomplete configuration must retain the same fallback parameters", fallback.getQueryParameter("api_key").equals(incomplete.getQueryParameter("api_key")));
        assertTrue("Incomplete configuration must not insert a missing user_id", fallback.getQueryParameter("user_id").equals(incomplete.getQueryParameter("user_id")));
    }
    private void save(String key, String user) {
        assertTrue(preferences.edit().putString(Constants.GELBOORU_API_KEY, key)
                .putString(Constants.GELBOORU_USER_ID, user).commit());
    }
}
