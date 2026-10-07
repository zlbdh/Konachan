package com.ess.anime.wallpaper.utils;

import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** 使用生成的测试媒体，不读取或删除用户媒体。 */
@RunWith(AndroidJUnit4.class)
public class MediaPublishingTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<File> testFiles = new ArrayList<>();
    private final List<Uri> testUris = new ArrayList<>();

    @After
    public void cleanOwnFixtures() {
        for (Uri uri : testUris) {
            context.getContentResolver().delete(uri, null, null);
        }
        for (File file : testFiles) {
            removeOwnPublishedItems(file.getName(), false);
            removeOwnPublishedItems(file.getName(), true);
            if (file.exists()) {
                assertTrue("仅清理测试生成的文件", file.delete());
            }
        }
    }

    @Test
    public void privateImagePublishesReadableCompletedMediaStoreItem() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        byte[] expected = imageBytes();
        File source = createFixture(".png", expected);

        assertTrue(BitmapUtils.insertToMediaStore(context, source));

        Uri uri = findOwnPublishedItem(source.getName(), false);
        assertNotNull("公开发布后必须得到真实媒体项", uri);
        testUris.add(uri);
        assertArrayEquals(expected, readUri(uri));
        assertEquals("公开发布不能删除本地收藏文件", expected.length, source.length());
    }

    @Test
    public void privateVideoPublishesReadableVideoCollectionItem() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        byte[] expected;
        try (InputStream asset = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("konachan-test-black.mp4")) {
            expected = readBytes(asset);
        }
        File source = createFixture(".mp4", expected);
        assertTrue(BitmapUtils.insertToMediaStore(context, source));
        Uri uri = findOwnPublishedItem(source.getName(), true);
        testUris.add(uri);
        assertArrayEquals(expected, readUri(uri));
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, uri);
            assertEquals("16", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("16", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } finally {
            metadata.release();
        }
    }

    @Test
    public void sameCompletedFileDoesNotPublishDuplicateItems() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        File source = createFixture(".png", imageBytes());
        Uri first = MediaStorePublisher.publish(context, source);
        assertNotNull(first);
        testUris.add(first);
        assertEquals(first, MediaStorePublisher.publish(context, source));
        assertEquals(first, BitmapUtils.getContentUriFromFile(context, source));
        assertEquals(first, findOwnPublishedItem(source.getName(), false));
    }

    @Test
    public void missingOrEmptySourceCannotReportPublicationSuccess() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        File missing = new File(context.getCacheDir(), "konachan-media-test-" + UUID.randomUUID() + ".png");
        assertNull(MediaStorePublisher.publish(context, missing));
        assertFalse(BitmapUtils.insertToMediaStore(context, missing));
        File empty = createFixture(".png", new byte[0]);
        assertFalse(BitmapUtils.insertToMediaStore(context, empty));
        assertTrue("失败不能删除本地源文件", empty.isFile());
    }

    @Test
    public void publicationRecordWriteFailureRollsBackOnlyNewMediaItem() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        File source = createFixture(".png", imageBytes());
        assertFalse(BitmapUtils.insertToMediaStore(rejectPublicationRecordWrites(), source));
        assertTrue("发布失败必须保留本地完整源文件", source.isFile() && source.length() > 0);
        assertEquals("本次发布失败不应留下 pending 或完成项", 0, ownPublishedCount(source.getName(), false));
    }

    @Test
    public void unPublishedPrivateFileHasReadableFileProviderUri() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        byte[] expected = imageBytes();
        File source = createFixture(context.getFilesDir(), ".png", expected);
        Uri uri = BitmapUtils.getContentUriFromFile(context, source);
        assertNotNull(uri);
        assertEquals("content", uri.getScheme());
        assertEquals(context.getPackageName() + ".fileprovider", uri.getAuthority());
        assertArrayEquals(expected, readUri(uri));
    }

    private byte[] imageBytes() {
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, image));
        bitmap.recycle();
        return image.toByteArray();
    }

    private File createFixture(String extension, byte[] bytes) throws Exception {
        return createFixture(context.getCacheDir(), extension, bytes);
    }

    private File createFixture(File directory, String extension, byte[] bytes) throws Exception {
        File file = new File(directory, "konachan-media-test-" + UUID.randomUUID() + extension);
        testFiles.add(file);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        return file;
    }

    private Uri findOwnPublishedItem(String name, boolean video) {
        Uri collection = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String folder = (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES)
                + "/Konachan/";
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.MediaColumns.OWNER_PACKAGE_NAME + "=?";
        String[] args = {name, folder, context.getPackageName()};
        String[] columns = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_PENDING};
        try (Cursor cursor = context.getContentResolver().query(collection, columns, selection, args, null)) {
            assertNotNull(cursor);
            assertEquals("测试自己的媒体应只发布一次", 1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("文件完整复制后才应解除 pending", 0, cursor.getInt(1));
            return ContentUris.withAppendedId(collection, cursor.getLong(0));
        }
    }

    private int ownPublishedCount(String name, boolean video) {
        try (Cursor cursor = queryOwnPublishedItems(name, video)) {
            assertNotNull(cursor);
            return cursor.getCount();
        }
    }

    private void removeOwnPublishedItems(String name, boolean video) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        Uri collection = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        List<Uri> owned = new ArrayList<>();
        try (Cursor cursor = queryOwnPublishedItems(name, video)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    owned.add(ContentUris.withAppendedId(collection, cursor.getLong(0)));
                }
            }
        }
        for (Uri uri : owned) context.getContentResolver().delete(uri, null, null);
    }

    private Cursor queryOwnPublishedItems(String name, boolean video) {
        Uri collection = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String folder = (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/Konachan/";
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=? AND "
                + MediaStore.MediaColumns.OWNER_PACKAGE_NAME + "=?";
        return context.getContentResolver().query(collection, new String[]{MediaStore.MediaColumns._ID},
                selection, new String[]{name, folder, context.getPackageName()}, null);
    }

    /** 只注入发布记录提交失败；媒体写入和回滚仍使用模拟器的真实 ContentResolver。 */
    private Context rejectPublicationRecordWrites() {
        return new ContextWrapper(context) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                SharedPreferences delegate = super.getSharedPreferences(name, mode);
                return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                        new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                            if (!"edit".equals(method.getName())) return method.invoke(delegate, args);
                            SharedPreferences.Editor editor = delegate.edit();
                            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                    new Class<?>[]{SharedPreferences.Editor.class}, (editorProxy, editorMethod, editorArgs) -> {
                                        if ("commit".equals(editorMethod.getName())) return false;
                                        if ("apply".equals(editorMethod.getName())) return null;
                                        Object result = editorMethod.invoke(editor, editorArgs);
                                        return result instanceof SharedPreferences.Editor ? editorProxy : result;
                                    });
                        });
            }
        };
    }

    private byte[] readUri(Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            assertNotNull(input);
            return readBytes(input);
        }
    }

    private byte[] readBytes(InputStream input) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
