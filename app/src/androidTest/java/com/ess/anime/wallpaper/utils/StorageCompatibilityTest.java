package com.ess.anime.wallpaper.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.model.helper.PermissionHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

/** Create only test-owned files to exercise real scoped-storage writes under the app UID. */
@RunWith(AndroidJUnit4.class)
public class StorageCompatibilityTest {

    @Test
    public void ownDownloadAndCacheDirectoriesAreWritable() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        assertWritable(new File(Constants.IMAGE_DIR));
        assertWritable(new File(Constants.IMAGE_TEMP));
        assertWritable(new File(Constants.IMAGE_DONATE));
    }

    @Test
    public void ownStorageDoesNotRequireReadMediaPermission() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Media-read permission must not block app-owned downloads and favorites",
                PermissionHelper.hasStoragePermissions(context));
        assertEquals("App-owned storage must not require external media-read permission", 0,
                PermissionHelper.getStoragePermissions().length);
    }

    @Test
    public void legacyRootCannotCreateFreshScopedStorageDirectory() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        File probe = new File(Environment.getExternalStorageDirectory(),
                "Konachan/konachan-compat-test-" + UUID.randomUUID());
        try {
            assertFalse("An app targeting API 34 must not rely on media-read permission to create a legacy shared root directory", probe.mkdirs());
        } finally {
            if (probe.exists()) {
                assertTrue("If the system allows writing, clean up only this probe directory", probe.delete());
            }
        }
    }

    private void assertWritable(File directory) throws Exception {
        assertTrue("The directory must be creatable under the app UID: " + directory,
                directory.isDirectory() || directory.mkdirs());
        File file = new File(directory, "konachan-storage-test-" + UUID.randomUUID() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(new byte[]{1, 2, 3});
            }
            assertTrue("The test file must be written completely", file.length() == 3);
        } finally {
            if (file.exists()) {
                assertTrue("Clean up only test-owned files", file.delete());
            }
        }
    }
}
