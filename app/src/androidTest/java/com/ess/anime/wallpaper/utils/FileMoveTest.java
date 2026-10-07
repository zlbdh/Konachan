package com.ess.anime.wallpaper.utils;

import android.content.Context;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

/** 只操作应用缓存中的 UUID 测试文件，覆盖失败、同路径和成功移动。 */
@RunWith(AndroidJUnit4.class)
public class FileMoveTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String token = "file-move-test-" + UUID.randomUUID();
    private final List<File> ownFiles = new ArrayList<>();
    private final List<File> ownDirectories = new ArrayList<>();

    @After public void cleanOwnFixtures() {
        for (File file : ownFiles) {
            if (file.exists()) assertTrue("只清理本次 UUID 文件", file.delete());
        }
        for (int i = ownDirectories.size() - 1; i >= 0; i--) {
            File directory = ownDirectories.get(i);
            if (directory.exists()) assertTrue("只清理本次空 UUID 目录", directory.delete());
        }
    }

    @Test(timeout = 5000) public void failedCopyKeepsSourceWhenTargetParentIsAFile() throws Exception {
        byte[] expected = {1, 3, 5, 7, 9};
        File source = fixture("source", expected);
        File blockingParent = fixture("blocking-parent", new byte[]{42});
        File target = new File(blockingParent, "cannot-create.bin");
        assertFalse("父路径是普通文件时必须报告失败", FileUtils.moveFile(source, target));
        assertTrue("复制失败不能删除源文件", source.isFile());
        assertArrayEquals(expected, read(source));
        assertArrayEquals("失败不能改动父路径中的已有文件", new byte[]{42}, read(blockingParent));
        assertFalse(target.exists());
    }

    @Test(timeout = 5000) public void movingToSameCanonicalPathKeepsSourceAndContent() throws Exception {
        byte[] expected = {2, 4, 6, 8};
        File source = fixture("same", expected);
        File samePath = new File(source.getParentFile(), "./" + source.getName());
        assertTrue("同一规范路径应当是成功的空操作", FileUtils.moveFile(source, samePath));
        assertTrue("同路径不能先删掉源文件", source.isFile());
        assertArrayEquals(expected, read(source));
    }

    @Test(timeout = 5000) public void successfulMoveCreatesParentAndPreservesEveryByte() throws Exception {
        byte[] expected = new byte[32769];
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) (i * 31);
        File source = fixture("success-source", expected);
        File parent = new File(context.getCacheDir(), token + "-new-parent");
        ownDirectories.add(parent);
        File target = new File(parent, "moved.bin");
        ownFiles.add(target);
        assertTrue("正常移动应成功", FileUtils.moveFile(source, target));
        assertFalse("成功后源应移除", source.exists());
        assertTrue(target.isFile());
        assertEquals(expected.length, target.length());
        assertArrayEquals("不能只验证长度，必须验证全部内容", expected, read(target));
    }

    private File fixture(String suffix, byte[] bytes) throws Exception {
        File file = new File(context.getCacheDir(), token + "-" + suffix + ".bin");
        ownFiles.add(file);
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        return file;
    }

    private byte[] read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            return output.toByteArray();
        }
    }
}
