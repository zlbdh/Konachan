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

/** Operate only on UUID test files in the app cache; cover failure, identical paths, and successful moves. */
@RunWith(AndroidJUnit4.class)
public class FileMoveTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final String token = "file-move-test-" + UUID.randomUUID();
    private final List<File> ownFiles = new ArrayList<>();
    private final List<File> ownDirectories = new ArrayList<>();

    @After public void cleanOwnFixtures() {
        for (File file : ownFiles) {
            if (file.exists()) assertTrue("Clean up only this test UUID file", file.delete());
        }
        for (int i = ownDirectories.size() - 1; i >= 0; i--) {
            File directory = ownDirectories.get(i);
            if (directory.exists()) assertTrue("Clean up only this empty test UUID directory", directory.delete());
        }
    }

    @Test(timeout = 5000) public void failedCopyKeepsSourceWhenTargetParentIsAFile() throws Exception {
        byte[] expected = {1, 3, 5, 7, 9};
        File source = fixture("source", expected);
        File blockingParent = fixture("blocking-parent", new byte[]{42});
        File target = new File(blockingParent, "cannot-create.bin");
        assertFalse("Report failure when the parent path is a regular file", FileUtils.moveFile(source, target));
        assertTrue("A failed copy must not delete the source", source.isFile());
        assertArrayEquals(expected, read(source));
        assertArrayEquals("Failure must not change an existing file at the parent path", new byte[]{42}, read(blockingParent));
        assertFalse(target.exists());
    }

    @Test(timeout = 5000) public void movingToSameCanonicalPathKeepsSourceAndContent() throws Exception {
        byte[] expected = {2, 4, 6, 8};
        File source = fixture("same", expected);
        File samePath = new File(source.getParentFile(), "./" + source.getName());
        assertTrue("Moving to the same canonical path must be a successful no-op", FileUtils.moveFile(source, samePath));
        assertTrue("An identical destination path must not cause source deletion", source.isFile());
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
        assertTrue("A normal move must succeed", FileUtils.moveFile(source, target));
        assertFalse("Remove the source after success", source.exists());
        assertTrue(target.isFile());
        assertEquals(expected.length, target.length());
        assertArrayEquals("Verify all content, not just the length", expected, read(target));
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
