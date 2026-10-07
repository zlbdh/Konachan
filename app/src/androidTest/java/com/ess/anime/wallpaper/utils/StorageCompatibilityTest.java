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

/** 只创建测试自己的文件，覆盖 scoped-storage 的真实应用 UID 写入。 */
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
        assertTrue("自有下载和收藏不应被媒体读取权限卡住",
                PermissionHelper.hasStoragePermissions(context));
        assertEquals("自有存储不应要求外部媒体读取权限", 0,
                PermissionHelper.getStoragePermissions().length);
    }

    @Test
    public void legacyRootCannotCreateFreshScopedStorageDirectory() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        File probe = new File(Environment.getExternalStorageDirectory(),
                "Konachan/konachan-compat-test-" + UUID.randomUUID());
        try {
            assertFalse("target 34 应用不能靠读媒体权限创建旧共享根目录", probe.mkdirs());
        } finally {
            if (probe.exists()) {
                assertTrue("若系统允许写入，仅清理本次探测目录", probe.delete());
            }
        }
    }

    private void assertWritable(File directory) throws Exception {
        assertTrue("目录应能在应用 UID 下创建: " + directory,
                directory.isDirectory() || directory.mkdirs());
        File file = new File(directory, "konachan-storage-test-" + UUID.randomUUID() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(new byte[]{1, 2, 3});
            }
            assertTrue("测试文件应完整写入", file.length() == 3);
        } finally {
            if (file.exists()) {
                assertTrue("应只清理测试自己的文件", file.delete());
            }
        }
    }
}
