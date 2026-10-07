package com.ess.anime.wallpaper.model.helper;

import com.ess.anime.wallpaper.download.image.DownloadBean;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4.class)
public class DownloadQualitySelectorTest {
    private DownloadBean item(int type) {
        return new DownloadBean(type, "https://example.com/" + type + ".jpg", 10,
                "测试", "", "/unused/" + type + ".jpg", false, "");
    }

    @Test public void sparseCandidatesSelectByTypeInsteadOfPosition() {
        DownloadBean large = item(1);
        DownloadBean original = item(2);
        assertSame(large, DownloadQualitySelector.select(Arrays.asList(large, original), 1));
        assertSame(original, DownloadQualitySelector.select(Arrays.asList(large, original), 2));
    }

    @Test public void missingSampleAndOriginalUseExistingLarge() {
        DownloadBean large = item(1);
        assertSame(large, DownloadQualitySelector.select(Collections.singletonList(large), 0));
        assertSame(large, DownloadQualitySelector.select(Collections.singletonList(large), 1));
        assertSame(large, DownloadQualitySelector.select(Collections.singletonList(large), 2));
    }

    @Test public void distinctSampleIsRetainedAndInvalidUrlIsRejected() {
        DownloadBean sample = item(0);
        DownloadBean large = item(1);
        assertSame(sample, DownloadQualitySelector.select(Arrays.asList(sample, large), 0));
        large.downloadUrl = "null";
        assertNull(DownloadQualitySelector.select(Collections.singletonList(large), 1));
        assertNull(DownloadQualitySelector.select(Collections.emptyList(), 2));
    }
}
