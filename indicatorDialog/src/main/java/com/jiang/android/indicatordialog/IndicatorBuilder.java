package com.jiang.android.indicatordialog;

import android.app.Activity;
import android.graphics.Color;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;
import androidx.annotation.StyleRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by jiang on 2017/5/18.
 */

public class IndicatorBuilder {
    public static final int TOP = 12;
    public static final int BOTTOM = 13;
    public static final int LEFT = 14;
    public static final int RIGHT = 15;

    public static final int GRAVITY_LEFT = 688;
    public static final int GRAVITY_RIGHT = 689;
    public static final int GRAVITY_CENTER = 670;
    protected int width;
    protected int height;
    protected int radius = 8;
    protected int bgColor = Color.WHITE;
    protected int mArrowWidth;
    protected float arrowercentage; //Arrow position
    protected int arrowdirection = TOP;
    private Activity mContext;
    protected RecyclerView.LayoutManager mLayoutManager;
    protected RecyclerView.Adapter mAdapter;
    protected int gravity = GRAVITY_LEFT;
    protected int animator;
    protected BaseDrawable mArrowDrawable;
    protected boolean dimEnabled = true;

    public IndicatorBuilder(Activity context) {
        this.mContext = context;
    }

    /**
     * Dialog width
     *
     * @param width px
     * @return
     */
    public IndicatorBuilder width(int width) {
        this.width = width;
        return this;
    }

    /**
     * Dialog height: -1 sizes to content; otherwise cap the actual content height at the specified value
     *
     * @param height Height in pixels
     * @return
     */
    public IndicatorBuilder height(int height) {
        this.height = height;
        return this;
    }

    /**
     * Dialog background color
     *
     * @param color
     * @return
     */
    public IndicatorBuilder bgColor(int color) {
        this.bgColor = color;
        return this;

    }

    /**
     * Dialog corner radius, which must be >= 0
     *
     * @param radius Corner radius
     * @return
     */
    public IndicatorBuilder radius(int radius) {
        if (radius < 0) {
            new Exception("radius must >=0");
        }
        this.radius = radius;
        return this;

    }

    /**
     * Add dialog enter and exit animations
     *
     * @param animator
     * @return
     */
    public IndicatorBuilder animator(@StyleRes int animator) {
        this.animator = animator;
        return this;
    }

    /**
     * Arrow width and height; the arrow bounds are square.
     * If omitted, use {@IndicatorDialog.ARROW_RECTAGE} and calculate the width as mBuilder.width * ARROW_RECTAGE
     * Measured in pixels
     * You can change {@IndicatorDialog.ARROW_RECTAGE}, but it is static and therefore affects all instances.
     *
     * @param width Arrow width and height
     * @return
     */
    public IndicatorBuilder arrowWidth(int width) {
        this.mArrowWidth = width;
        return this;
    }

    /**
     * Arrow drawable; extend BaseDrawable for a custom shape. By default, offset toward the view by half the arrow height.
     * Use the default triangular arrow when omitted
     *
     * @param drawable
     * @return
     */
    public IndicatorBuilder arrowDrawable(BaseDrawable drawable) {
        this.mArrowDrawable = drawable;
        return this;
    }

    /**
     * Arrow position: width * rectage for top/bottom, or {@IndicatorDialog.mResultHeight} * rectage for left/right
     *
     * @param rectage
     * @return
     */
    public IndicatorBuilder ArrowRectage(float rectage) {
        if (rectage > 1 || rectage < 0) {
            new Exception("rectage must be 0 <= rectage <= 1");
        }
        this.arrowercentage = rectage;
        return this;
    }

    /**
     * Arrow direction
     *
     * @param direction
     * @return
     */
    public IndicatorBuilder ArrowDirection(@ARROWDIRECTION int direction) {
        this.arrowdirection = direction;
        return this;
    }


    /**
     * Background dimming; true by default
     *
     * @param enable true by default
     * @return
     */
    public IndicatorBuilder dimEnabled(boolean enable) {
        this.dimEnabled = enable;
        return this;
    }

    public IndicatorBuilder layoutManager(RecyclerView.LayoutManager layoutManager) {
        this.mLayoutManager = layoutManager;
        return this;
    }

    public IndicatorBuilder adapter(RecyclerView.Adapter adapter) {
        this.mAdapter = adapter;
        return this;
    }

    public IndicatorBuilder gravity(@GRAVITY int gravity) {
        this.gravity = gravity;
        return this;
    }

    public IndicatorDialog create() {

        if (width <= 0)
            throw new NullPointerException("width can not be 0");

        if (arrowercentage < 0)
            throw new NullPointerException("arrowercentage can not < 0");


        if (mAdapter == null)
            throw new NullPointerException("adapter can not be null");

        if (mLayoutManager == null) {
            mLayoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.VERTICAL, false);
        }

        return IndicatorDialog.newInstance(mContext, this);
    }

    @IntDef({TOP, BOTTOM, LEFT, RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ARROWDIRECTION {
    }

    @IntDef({GRAVITY_LEFT, GRAVITY_RIGHT, GRAVITY_CENTER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface GRAVITY {

    }
}
