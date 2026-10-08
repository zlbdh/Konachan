package com.ess.anime.wallpaper.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import androidx.core.content.FileProvider;

/**
 * Bitmap operations; recycle bitmaps at the appropriate point after use
 *
 * @author Zero
 */
public class BitmapUtils {

    /**
     * Convert a Bitmap to Byte[]
     *
     * @param bitmap Target bitmap
     * @param format Image encoding format
     * @return Byte array
     */
    public static byte[] bitmapToBytes(Bitmap bitmap, CompressFormat format) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            bitmap.compress(format, 100, baos);
            return baos.toByteArray();
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Extract the image from an ImageView
     *
     * @param iv Target ImageView
     * @return Bitmap
     */
    public static Bitmap getBitmapFromImageView(ImageView iv) {
        iv.setDrawingCacheEnabled(true);
        Bitmap bitmap = iv.getDrawingCache();
        iv.setDrawingCacheEnabled(false);
        return bitmap;
    }

    /**
     * Return a resource bitmap scaled to the requested dimensions
     *
     * @param res        Resources
     * @param id         resId
     * @param destWidth  Target width
     * @param destHeight Target height
     * @return Scaled bitmap
     */
    public static Bitmap compressBitmapResource(Resources res, int id, int destWidth, int destHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        BitmapFactory.decodeResource(res, id, opts);
        float imgWidth = opts.outWidth;
        float imgHeight = opts.outHeight;
        int widthRatio = (int) (imgWidth / destWidth);
        int heightRatio = (int) (imgHeight / destHeight);
        opts.inSampleSize = 1;
        if (widthRatio > 1 || heightRatio > 1) {
            if (widthRatio > heightRatio) {
                opts.inSampleSize = widthRatio;
            } else {
                opts.inSampleSize = heightRatio;
            }
        }
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, id, opts);
    }

    /**
     * Save a Bitmap as a local image
     *
     * @param bitmap Bitmap to save
     * @param path   Output path
     * @param format Storage format
     * @return Whether saving succeeded
     */
    public static boolean saveBitmapToLocal(Bitmap bitmap, String path, CompressFormat format) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(path));
            bitmap.compress(format, 100, fos);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Load a local Bitmap and scale it to the target view to avoid out-of-memory errors <br/>
     * For an ImageView, also call getLocalBitmapDegree and rotateBitmap to correct orientation
     *
     * @param context    Context
     * @param path       Local image path
     * @param destWidth  Width to fit the view
     * @param destHeight Height to fit the view
     * @return Bitmap
     */
    public static Bitmap getBitmapFromLocal(Context context, String path, float destWidth, float destHeight) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(path, opts);
        float imgWidth = opts.outWidth;
        float imgHeight = opts.outHeight;
        int widthRatio = (int) (imgWidth / destWidth);
        int heightRatio = (int) (imgHeight / destHeight);
        opts.inSampleSize = 1;
        if (widthRatio > 1 || heightRatio > 1) {
            if (widthRatio > heightRatio) {
                opts.inSampleSize = widthRatio;
            } else {
                opts.inSampleSize = heightRatio;
            }
        }
        opts.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, opts);
    }

    /**
     * Get the local image orientation
     *
     * @param path Local image path
     * @return Image rotation angle
     */
    public static float getLocalBitmapDegree(String path) {
        float degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    /**
     * Rotate the image upright
     *
     * @param bitmap Target bitmap
     * @param degree Rotation angle
     * @return Rotated bitmap
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, float degree) {
        if (bitmap == null)
            return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Setting post rotate to 90
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    /**
     * Flip the image horizontally
     *
     * @param bitmap Target image
     * @return Flipped image
     */
    public static Bitmap flipBitmapHor(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        Camera camera = new Camera();
        camera.save();
        camera.rotateY(180f);
        camera.getMatrix(matrix);
        camera.restore();
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Flip the image vertically
     *
     * @param bitmap Target image
     * @return Flipped image
     */
    public static Bitmap flipBitmapVer(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        Camera camera = new Camera();
        camera.save();
        camera.rotateX(180f);
        camera.getMatrix(matrix);
        camera.restore();
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Join multiple images horizontally
     *
     * @param bitmaps Image array
     * @return Joined image
     */
    public static Bitmap mergeBitmapsHor(Bitmap[] bitmaps) {
        int width = 0;
        int height = 0;
        for (Bitmap bitmap : bitmaps) {
            width += bitmap.getWidth();
            height = Math.max(height, bitmap.getHeight());
        }
        Bitmap mergeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(mergeBitmap);
        int currentWidth = 0;
        for (Bitmap bitmap : bitmaps) {
            canvas.drawBitmap(bitmap, currentWidth, 0, null);
            currentWidth += bitmap.getWidth();
        }
        return mergeBitmap;
    }

    /**
     * Join multiple images vertically
     *
     * @param bitmaps Image array
     * @return Joined image
     */
    public static Bitmap mergeBitmapsVer(Bitmap[] bitmaps) {
        int width = 0;
        int height = 0;
        for (Bitmap bitmap : bitmaps) {
            width = Math.max(width, bitmap.getWidth());
            height += bitmap.getHeight();
        }
        Bitmap mergeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(mergeBitmap);
        int currentHeight = 0;
        for (Bitmap bitmap : bitmaps) {
            canvas.drawBitmap(bitmap, 0, currentHeight, null);
            currentHeight += bitmap.getHeight();
        }
        return mergeBitmap;
    }

    /**
     * Split the image into m × n tiles
     *
     * @param bitmap   Target image
     * @param rowCount Number of rows
     * @param colCount Number of columns
     * @return List of image tiles
     */
    public static ArrayList<Bitmap> splitImage(Bitmap bitmap, int rowCount, int colCount) {
        ArrayList<Bitmap> splitList = new ArrayList<>();
        int splitWidth = bitmap.getWidth() / rowCount;
        int splitHeight = bitmap.getHeight() / colCount;
        for (int row = 0; row < rowCount; row++) {
            for (int col = 0; col < colCount; col++) {
                int x = col * splitWidth;
                int y = row * splitHeight;
                splitList.add(Bitmap.createBitmap(bitmap, x, y, splitWidth, splitHeight));
            }
        }
        return splitList;
    }

    /**
     * Apply Gaussian blur; first scale with Bitmap.createScaledBitmap() and adjust quality with bitmap.compress()
     * to achieve a better blur effect.
     *
     * @param context Context
     * @param bitmap  Image to blur
     * @return Blurred image
     */
    public static Bitmap blurBitmap(Context context, Bitmap bitmap) {
        //Let's create an empty bitmap with the same size of the bitmap we want to blur
        Bitmap outBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_4444);

        //Instantiate a new Renderscript
        RenderScript rs = RenderScript.create(context.getApplicationContext());

        //Create an Intrinsic Blur Script using the Renderscript
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));

            //Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
            Allocation allIn = Allocation.createFromBitmap(rs, bitmap);
            Allocation allOut = Allocation.createFromBitmap(rs, outBitmap);

            //Set the radius of the blur
            if (blurScript != null) {
                blurScript.setRadius(25.0f);
            }

            //Perform the Renderscript
            blurScript.setInput(allIn);

            blurScript.forEach(allOut);
            //Copy the final bitmap created by the out Allocation to the outBitmap
            allOut.copyTo(outBitmap);
            //recycle the original bitmap
            bitmap.recycle();

            //After finishing everything, we destroy the Renderscript.
            rs.destroy();
        }
        return outBitmap;
    }

    /**
     * Add an image to the media library and refresh the gallery
     *
     * @param context Context
     * @param file    Image file
     */
    public static boolean insertToMediaStore(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStorePublisher.publish(context, file) != null;
        }
        if (file == null || !file.isFile() || file.length() == 0) return false;
        try {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Remove an image or video from the media library and refresh the gallery
     *
     * @param context Context
     * @param path    Image or video path
     */
    public static void deleteFromMediaStore(Context context, String path) {
        Uri uri;
        if (FileUtils.isVideoType(path)) {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        context.getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=?", new String[]{path});
    }

    /**
     * Gets the content:// URI from the given corresponding path to a file
     *
     * @param context   Context
     * @param mediaFile Media file
     * @return content Uri
     */
    public static Uri getContentUriFromFile(Context context, File mediaFile) {
        if (mediaFile == null || !mediaFile.isFile()) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri published = MediaStorePublisher.findPublishedUri(context, mediaFile);
            if (published != null) return published;
            try {
                return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", mediaFile);
            } catch (IllegalArgumentException error) {
                return null;
            }
        }
        String filePath = mediaFile.getAbsolutePath();
        boolean isImageType = FileUtils.isImageType(filePath);
        Uri uri = isImageType ? MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String mediaIdField = isImageType ? MediaStore.Images.Media._ID : MediaStore.Video.Media._ID;
        String mediaDataField = isImageType ? MediaStore.Images.Media.DATA : MediaStore.Video.Media.DATA;
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{mediaIdField},
                mediaDataField + "=?", new String[]{filePath}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                return Uri.withAppendedPath(uri, "" + id);
            } else {
                if (mediaFile.exists()) {
                    ContentValues values = new ContentValues();
                    values.put(mediaDataField, filePath);
                    return context.getContentResolver().insert(uri, values);
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get the path for an image URI
     *
     * @param context Context
     * @param uri     Image URI
     * @return Image file path
     */
    public static String getImagePathFromUri(Context context, Uri uri) {
        if (uri == null) {
            return "";
        }
        String scheme = uri.getScheme();
        String data = "";
        if (scheme == null) {
            data = uri.getPath();
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            data = uri.getPath();
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            ContentResolver cr = context.getContentResolver();
            String[] projection = new String[]{MediaStore.Images.ImageColumns.DATA};
            try (Cursor cursor = cr.query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    data = cursor.getString(cursor.getColumnIndex(projection[0]));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

}
