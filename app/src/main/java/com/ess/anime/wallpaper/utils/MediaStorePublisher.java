package com.ess.anime.wallpaper.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** 将自有媒体完整复制到公开相册；失败只回滚本次新媒体项，保留本地源文件。 */
public final class MediaStorePublisher {

    private static final String TAG = "MediaStorePublisher";
    private static final String RECORDS = "konachan_published_media";

    private MediaStorePublisher() {
    }

    /** API 29+ 发布成功才返回可读取的 content URI，失败返回 null。应在 IO 线程调用。 */
    public static Uri publish(Context context, File source) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isReadableMedia(source)) {
            return null;
        }
        Uri existing = findPublishedUri(context, source);
        if (existing != null) {
            return existing;
        }

        boolean video = FileUtils.isVideoType(source.getName());
        Uri collection = video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, source.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType(source, video));
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/Konachan/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();
        Uri created = null;
        long expectedSize = source.length();
        String expectedSignature = signature(source);
        try {
            created = resolver.insert(collection, values);
            if (created == null) {
                throw new IOException("媒体库未创建目标文件");
            }
            long copied = copyToUri(resolver, source, created);
            if (copied != expectedSize || !expectedSignature.equals(signature(source))) {
                throw new IOException("媒体文件未完整复制或源文件在复制中改变");
            }
            try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(created, "r")) {
                if (descriptor == null || descriptor.getStatSize() != expectedSize) {
                    throw new IOException("媒体库文件大小校验失败");
                }
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(created, completed, null, null) != 1) {
                throw new IOException("媒体库未确认发布完成");
            }
            String key = source.getAbsolutePath();
            if (!records(context).edit().putString(key, created.toString())
                    .putString(key + "|signature", expectedSignature).commit()) {
                throw new IOException("媒体发布记录未能保存");
            }
            return created;
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "媒体发布失败，保留本地源文件", error);
            if (created != null) {
                try {
                    resolver.delete(created, null, null);
                } catch (RuntimeException cleanupError) {
                    Log.e(TAG, "本次未完成媒体项清理失败", cleanupError);
                }
            }
            return null;
        }
    }

    /** 只使用本应用发布记录中的 URI，不遍历或查询用户其他媒体。 */
    public static Uri findPublishedUri(Context context, File source) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isReadableMedia(source)) {
            return null;
        }
        String key = source.getAbsolutePath();
        SharedPreferences preferences = records(context);
        if (!signature(source).equals(preferences.getString(key + "|signature", null))) {
            return null;
        }
        String saved = preferences.getString(key, null);
        if (saved == null) {
            return null;
        }
        try {
            Uri uri = Uri.parse(saved);
            if (!"content".equals(uri.getScheme()) || !"media".equals(uri.getAuthority())) {
                return null;
            }
            try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
                return descriptor != null && descriptor.getStatSize() == source.length() ? uri : null;
            }
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static long copyToUri(ContentResolver resolver, File source, Uri target) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = resolver.openOutputStream(target, "w")) {
            if (output == null) {
                throw new IOException("媒体库未提供输出流");
            }
            byte[] buffer = new byte[8192];
            long copied = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                copied += count;
            }
            output.flush();
            return copied;
        }
    }

    private static boolean isReadableMedia(File file) {
        return file != null && file.isFile() && file.canRead() && file.length() > 0
                && FileUtils.isMediaType(file.getName());
    }

    private static String signature(File file) {
        return file.length() + ":" + file.lastModified();
    }

    private static SharedPreferences records(Context context) {
        return context.getSharedPreferences(RECORDS, Context.MODE_PRIVATE);
    }

    private static String mimeType(File source, boolean video) {
        String extension = FileUtils.getFileExtension(source.getName()).toLowerCase(Locale.ROOT);
        String known = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (known != null) {
            return known;
        }
        if ("avif".equals(extension)) {
            return "image/avif";
        }
        return video ? "video/mp4" : "image/jpeg";
    }
}
