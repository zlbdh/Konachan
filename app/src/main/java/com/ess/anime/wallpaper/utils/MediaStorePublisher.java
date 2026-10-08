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

/** Copy app-owned media completely to the public gallery; on failure, roll back only the new item and preserve the local source. */
public final class MediaStorePublisher {

    private static final String TAG = "MediaStorePublisher";
    private static final String RECORDS = "konachan_published_media";

    private MediaStorePublisher() {
    }

    /** On API 29+, return a readable content URI only after successful publication, or null on failure. Call on an IO thread. */
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
                throw new IOException("The media library did not create a destination file");
            }
            long copied = copyToUri(resolver, source, created);
            if (copied != expectedSize || !expectedSignature.equals(signature(source))) {
                throw new IOException("The media file was not fully copied or the source changed during copying");
            }
            try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(created, "r")) {
                if (descriptor == null || descriptor.getStatSize() != expectedSize) {
                    throw new IOException("Media library file-size verification failed");
                }
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(created, completed, null, null) != 1) {
                throw new IOException("The media library did not confirm publication");
            }
            String key = source.getAbsolutePath();
            if (!records(context).edit().putString(key, created.toString())
                    .putString(key + "|signature", expectedSignature).commit()) {
                throw new IOException("Unable to save the media publication record");
            }
            return created;
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "Media publication failed; preserving the local source file", error);
            if (created != null) {
                try {
                    resolver.delete(created, null, null);
                } catch (RuntimeException cleanupError) {
                    Log.e(TAG, "Unable to clean up this incomplete media item", cleanupError);
                }
            }
            return null;
        }
    }

    /** Use only URIs in the app's publication records; do not enumerate or query other user media. */
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
                throw new IOException("The media library did not provide an output stream");
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
