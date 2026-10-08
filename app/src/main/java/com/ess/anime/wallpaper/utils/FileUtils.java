package com.ess.anime.wallpaper.utils;

import android.text.TextUtils;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Common IO stream and File operations <br/><br/>
 * <b>Required permission:</b><br/>
 * &emsp;&lt;uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /&gt;
 *
 * @author Zero
 */
public class FileUtils {

    /**
     * Compute the MD5 hash of a UTF-8 string
     *
     * @param info String to encode
     * @return Encoded string, or an empty string on error
     */
    public static String encodeMD5String(String info) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(info.getBytes("UTF-8"));
            byte[] digest = md5.digest();

            StringBuilder strBuilder = new StringBuilder();
            for (byte aDigest : digest) {
                String s = Integer.toHexString(0xff & aDigest);
                if (s.length() == 1) {
                    strBuilder.append("0");
                }
                strBuilder.append(s);
            }
            return strBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    /**
     * XOR-encode a string
     *
     * @param info          String to encode
     * @param cryptographic Key
     * @return Encoded string
     */
    public static String encodeXorString(String info, String cryptographic) {
        char[] infoArray = info.toCharArray();
        char[] keyArray = cryptographic.toCharArray();
        int j = 0;
        for (int i = 0; i < infoArray.length; i++) {
            infoArray[i] = (char) (infoArray[i] ^ keyArray[j]);
            if (++j == keyArray.length) {
                j = 0;
            }
        }
        return String.valueOf(infoArray);
    }

    /**
     * XOR-decode a string
     *
     * @param info          String to decode
     * @param cryptographic Key
     * @return Decoded string
     */
    public static String decodeXorString(String info, String cryptographic) {
        char[] infoArray = info.toCharArray();
        char[] keyArray = cryptographic.toCharArray();
        int j = 0;
        for (int i = 0; i < infoArray.length; i++) {
            infoArray[i] = (char) (infoArray[i] ^ keyArray[j]);
            if (++j == keyArray.length) {
                j = 0;
            }
        }
        return String.valueOf(infoArray);
    }

    /**
     * Base64-encode a UTF-8 string
     *
     * @param info String to encode
     * @return Encoded string, or an empty string on error
     */
    public static String encodeBase64String(String info) {
        try {
            return Base64.encodeToString(info.getBytes("UTF-8"), Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    /**
     * Base64-decode a UTF-8 string
     *
     * @param info String to decode
     * @return Decoded string
     */
    public static String decodeBase64String(String info) {
        return new String(Base64.decode(info, Base64.DEFAULT));
    }

    /**
     * Base64-encode a file
     *
     * @param file File to encode
     * @return Base64 file content, or an empty string on error
     */
    public static String encodeBase64File(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            return Base64.encodeToString(buffer, Base64.DEFAULT);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Base64-decode file content
     *
     * @param encodeStr Encoded file content
     * @param file      Output file for decoded content
     * @return Whether the file was saved successfully
     */
    public static boolean decodeBase64File(String encodeStr, File file) {
        FileOutputStream fos;
        try {
            byte[] buffer = Base64.decode(encodeStr, Base64.DEFAULT);
            fos = new FileOutputStream(file);
            fos.write(buffer);
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convert a stream to a UTF-8 string
     *
     * @param is Input stream
     * @return Decoded string, or null on error
     */
    public static String streamToString(InputStream is) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int temp;
        try {
            while ((temp = is.read(buffer)) != -1) {
                baos.write(buffer, 0, temp);
            }
            return baos.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                baos.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    /**
     * Save a stream to a file
     *
     * @param is   Input stream
     * @param file Destination file
     * @return true if saving succeeded, otherwise false
     */
    public static boolean streamToFile(InputStream is, File file) {
        BufferedInputStream bis = new BufferedInputStream(is);
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file));
            int temp;
            while ((temp = bis.read()) != -1) {
                bos.write(temp);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
                bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Save a string to a file
     *
     * @param str  Input string
     * @param file Destination file
     * @return true if saving succeeded, otherwise false
     */
    public static boolean stringToFile(String str, File file) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(str.getBytes());
            fos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Read a file as a string
     *
     * @param file Target file
     * @return File content as a string
     */
    public static String fileToString(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            return streamToString(fis);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Move a file, for example from a/img.jpg to b/img.jpg
     *
     * @param fromFile File to move
     * @param toFile   Destination file
     * @return true if the move succeeded, otherwise false
     */
    public static boolean moveFile(File fromFile, File toFile) {
        if (!fromFile.exists() || !fromFile.isFile()) {
            return false;
        }

        try {
            if (fromFile.getCanonicalFile().equals(toFile.getCanonicalFile())) {
                return true;
            }
            if (toFile.exists()) {
                toFile.delete();
            }

            File parentFile = toFile.getParentFile();
            if (parentFile != null && !parentFile.exists()) {
                if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                    return false;
                }
            }

            boolean renameToSuccess = false;
            try {
                renameToSuccess = fromFile.renameTo(toFile);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (!renameToSuccess) {
                //renameTo fails across filesystems; copy and then delete the original instead
                if (!copyFile(fromFile, toFile) || toFile.length() != fromFile.length()) {
                    return false;
                }
                if (!fromFile.delete()) {
                    return false;
                }
            }
            return toFile.exists();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Copy a file, for example from a/img.jpg to b/img.jpg
     *
     * @param fromFile File to copy
     * @param toFile   Destination file
     * @return true if copying succeeded, otherwise false
     */
    public static boolean copyFile(File fromFile, File toFile) {
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            if (fromFile.exists()) {
                bis = new BufferedInputStream(new FileInputStream(fromFile));
                bos = new BufferedOutputStream(new FileOutputStream(toFile));
                int temp;
                while ((temp = bis.read()) != -1) {
                    bos.write(temp);
                }
            }
            return fromFile.exists();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bos != null) {
                    bos.close();
                }
                if (bis != null) {
                    bis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Copy a directory, for example root/a into root/b, producing root/b/a
     *
     * @param fromPath Directory to copy
     * @param toPath   Destination directory
     * @return true if copying succeeded, otherwise false
     */
    public static boolean copyFolder(String fromPath, String toPath) {
        File fromFile = new File(fromPath);
        toPath = toPath + "/" + fromFile.getName();
        File toFile = new File(toPath);
        if (fromFile.exists()) {
            if (fromFile.isDirectory()) {
                if (!toFile.exists()) {
                    toFile.mkdirs();
                }
                for (File childFile : fromFile.listFiles()) {
                    String tempPath = fromPath + "/" + childFile.getName();
                    if (!copyFolder(tempPath, toPath)) {
                        return false;
                    }
                }
                return true;
            } else {
                return copyFile(fromFile, toFile);
            }
        }
        return false;
    }

    /**
     * Delete a file or directory, including all nested files and directories
     *
     * @param path File or directory path to delete
     * @return true if deletion succeeded, otherwise false
     */
    public static boolean deleteFile(String path) {
        return deleteFile(new File(path));
    }

    /**
     * Delete a file or directory, including all nested files and directories
     *
     * @param file File or directory to delete
     * @return true if deletion succeeded, otherwise false
     */
    public static boolean deleteFile(File file) {
        try {
            if (file.exists()) {
                if (file.isDirectory()) {
                    for (File childFile : file.listFiles()) {
                        String tempPath = file.getAbsolutePath() + "/" + childFile.getName();
                        if (!deleteFile(tempPath)) {
                            return false;
                        }
                    }
                }
                return file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get file or directory size, or zero if missing. For a single file, file.length() can be used directly.
     *
     * @param path Directory path
     * @return Directory size in bytes
     */
    public static long getFileLength(String path) {
        return getFileLength(new File(path));
    }

    /**
     * Get file or directory size, or zero if missing. For a single file, file.length() can be used directly.
     *
     * @param file Directory
     * @return Directory size in bytes
     */
    public static long getFileLength(File file) {
        long length = 0;
        try {
            if (file.exists()) {
                if (file.isDirectory()) {
                    for (File f : file.listFiles()) {
                        length += getFileLength(f.getAbsolutePath());
                    }
                } else {
                    length += file.length();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return length;
    }

    private final static long KB = 1024;
    private final static long MB = KB * 1024;
    private final static long GB = MB * 1024;

    /**
     * Format a file size as B, KB, MB, or GB
     *
     * @param b File size in bytes
     * @return B and KB as integers; MB and GB rounded to two decimal places
     */
    public static String computeFileSize(long b) {
        float size;
        BigDecimal decimal;

        if (b / GB >= 1) {
            size = b / (float) GB;
            decimal = new BigDecimal(size);
            return decimal.setScale(2, RoundingMode.HALF_UP).floatValue() + "G";
        } else if (b / MB >= 1) {
            size = b / (float) MB;
            decimal = new BigDecimal(size);
            return decimal.setScale(2, RoundingMode.HALF_UP).floatValue() + "M";
        } else if (b / KB >= 1) {
            size = b / (float) KB;
            decimal = new BigDecimal(size);
            return decimal.setScale(0, RoundingMode.HALF_UP).intValue() + "K";
        } else {
            return b + "B";
        }
    }

    /**
     * Parse a B, KB, MB, or GB size string into a long
     *
     * @param fileSize Formatted file size
     * @return File size
     */
    public static long parseFileSize(String fileSize) {
        try {
            double size = Double.parseDouble(fileSize.replaceAll("[^-*\\d+(\\.)?]", "")); // Extract the number
            if (fileSize.toUpperCase().contains("G")) {
                size *= 1024 * 1024 * 1024;
            } else if (fileSize.toUpperCase().contains("M")) {
                size *= 1024 * 1024;
            } else if (fileSize.toUpperCase().contains("K")) {
                size *= 1024;
            }
            return (long) size;
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    /**
     * Get the file extension without the dot
     *
     * @param path File path
     * @return File extension
     */
    public static String getFileExtension(String path) {
        Pattern pattern = Pattern.compile("\\.(\\w+)(\\?|$)");
        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "";
        }
    }

    /**
     * Get the file extension with the dot
     *
     * @param path File path
     * @return File extension
     */
    public static String getFileExtensionWithDot(String path) {
        String extension = getFileExtension(path);
        if (TextUtils.isEmpty(extension)) {
            return "";
        } else {
            return "." + extension;
        }
    }

    /**
     * Check whether the file is an image
     *
     * @return boolean
     */
    public static boolean isImageType(String filePath) {
        try {
            if (!TextUtils.isEmpty(filePath)) {
                String extension = getFileExtension(filePath).toLowerCase();
                return extension.equals("bmp") || extension.equals("jpg") || extension.equals("jpeg")
                        || extension.equals("png") || extension.equals("gif") || extension.equals("webp")
                        || extension.equals("avif");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check whether the file is a video
     *
     * @return boolean
     */
    public static boolean isVideoType(String filePath) {
        try {
            if (!TextUtils.isEmpty(filePath)) {
                String extension = getFileExtension(filePath).toLowerCase();
                return extension.equals("avi") || extension.equals("wmv") || extension.equals("mp4")
                        || extension.equals("webm") || extension.equals("mpg") || extension.equals("mpeg")
                        || extension.equals("3gp") || extension.equals("mov") || extension.equals("flv");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check whether the file is media
     *
     * @return boolean
     */
    public static boolean isMediaType(String filePath) {
        return isImageType(filePath) || isVideoType(filePath);
    }

}
