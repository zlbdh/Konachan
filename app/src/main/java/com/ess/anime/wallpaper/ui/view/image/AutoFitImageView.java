package com.ess.anime.wallpaper.ui.view.image;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.util.AttributeSet;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.utils.UIUtils;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Scale XML width and height automatically using a 360dp × 640dp screen as the baseline
 * version 1.0
 * Limitation: currently supports sizing only one child View
 * Attributes: see the style configuration below
 * scaleWidth(boolean): automatically adapt width when layout_width is fixed and this attribute is true
 * scaleHeight(boolean): automatically adapt height when layout_height is fixed and this attribute is true
 * relativeTo(int): requires fixed layout_width and layout_height; choose one dimension as the reference,
 * scale that dimension to the screen, and preserve the original aspect ratio for the other dimension
 * (scaleWidth and scaleHeight are implicitly enabled in this mode)
 * maxRatio(float): maximum scale factor
 * minRatio(float): minimum scale factor
 */

public class AutoFitImageView extends AppCompatImageView {

    private final static int NONE = 0;  // Scale both dimensions relative to the screen
    private final static int RELATIVE_TO_WIDTH = 1;  // Scale height proportionally from width
    private final static int RELATIVE_TO_HEIGHT = 2; // Scale width proportionally from height

    private boolean mScaleWidth;   // Whether to scale width; false by default
    private boolean mScaleHeight;  // Whether to scale height; false by default
    private int mRelative;    // Reference dimension for proportional scaling; NONE by default

    private float mWidthRatio;  // Screen width relative to 360dp
    private float mHeightRatio; // Screen height relative to 640dp
    private float mMaxRatio;    // Maximum scale factor; zero by default
    private float mMinRatio;    // Minimum scale factor; zero by default

    public AutoFitImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Read XML attributes
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.AutoFitImageView);
        mScaleWidth = typedArray.getBoolean(R.styleable.AutoFitImageView_scaleWidth, false);
        mScaleHeight = typedArray.getBoolean(R.styleable.AutoFitImageView_scaleHeight, false);
        mRelative = typedArray.getInt(R.styleable.AutoFitImageView_relativeTo, NONE);
        mMaxRatio = typedArray.getFloat(R.styleable.AutoFitImageView_maxRatio, 0);
        mMinRatio = typedArray.getFloat(R.styleable.AutoFitImageView_minRatio, 0);
        typedArray.recycle();
    }

    private void resetRatio() {
        // Calculate width and height scale factors
        Point point = UIUtils.getAppUsableScreenSize(getContext());
        mWidthRatio = balanceRatio(UIUtils.px2dp(getContext(), Math.min(point.x, point.y)) / 360f);
        mHeightRatio = balanceRatio(UIUtils.px2dp(getContext(), Math.max(point.x, point.y)) / 640f);
    }

    // Apply the minimum and maximum scale factors
    private float balanceRatio(float ratio) {
        if (mMaxRatio > 0 && mMaxRatio >= mMinRatio) {
            ratio = Math.min(ratio, mMaxRatio);
        }
        if (mMinRatio > 0 && mMaxRatio >= mMinRatio) {
            ratio = Math.max(ratio, mMinRatio);
        }
        return ratio;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        resetRatio();
        // UNSPECIFIED: the parent imposes no size restriction; generally an internal measurement state
        // EXACTLY: 100dp match_parent
        // AT_MOST: wrap_content
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        // Get the original width and height
        int selfWidth = MeasureSpec.getSize(widthMeasureSpec);
        int selfHeight = MeasureSpec.getSize(heightMeasureSpec);

        // Adapt to the configured attributes
        if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY
                && mRelative == RELATIVE_TO_WIDTH) {
            // Fixed dimensions: scale width to the screen and height proportionally
            float scale = selfHeight / 1f / selfWidth;
            selfWidth = (int) (selfWidth * mWidthRatio + 0.5f);
            selfHeight = (int) (selfWidth * scale + 0.5f);
        } else if (widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY
                && mRelative == RELATIVE_TO_HEIGHT) {
            // Fixed dimensions: scale height to the screen and width proportionally
            float scale = selfWidth / 1f / selfHeight;
            selfHeight = (int) (selfHeight * mHeightRatio + 0.5f);
            selfWidth = (int) (selfHeight * scale + 0.5f);
        } else {
            if (widthMode == MeasureSpec.EXACTLY && mScaleWidth) {
                // Scale width to the screen independently of height
                selfWidth = (int) (selfWidth * mWidthRatio + 0.5f);
            } else {
                // No scaling: mScaleWidth is unset or layout_width="wrap_content"
                selfWidth = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            }

            if (heightMode == MeasureSpec.EXACTLY && mScaleHeight) {
                // Scale height to the screen independently of width
                selfHeight = (int) (selfHeight * mHeightRatio + 0.5f);
            } else {
                // No scaling: mScaleHeight is unset or layout_height="wrap_content"
                selfHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
            }
        }

        // Save the scaling result
        setMeasuredDimension(selfWidth, selfHeight);
    }
}
