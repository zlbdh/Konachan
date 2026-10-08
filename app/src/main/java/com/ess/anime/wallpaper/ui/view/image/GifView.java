package com.ess.anime.wallpaper.ui.view.image;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.ess.anime.wallpaper.R;

import java.io.InputStream;

public class GifView extends View {
    private Resources resources;

    /**
     * GIF playback view utility
     */
    private Movie mMovie;

    /**
     * 0 = playing, 1 = paused
     */
    private int playStatus;
    /**
     * GIF animation start time
     */
    private long mMovieStart;
    /**
     * Current GIF playback position
     */
    private int relTime;
    /**
     * Time offset for resuming paused or frame-based playback
     */
    private int offsetTime;

    /**
     * Width scale factor (view width / GIF width)
     */
    private float ratioWidth;
    /**
     * Height scale factor (view height / GIF height)
     */
    private float ratioHeight;

    public GifView(Context context) {
        this(context, null);
    }

    public GifView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GifView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        setFocusable(true);

        // Android 3.0+ enables hardware acceleration automatically; disable it for correct GIF playback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        resources = context.getResources();

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GifView);
        int drawableResId = ta.getResourceId(R.styleable.GifView_src, -1);
        setGifResource(drawableResId);

        ta.recycle();
    }

    /**
     * Set a GIF resource for local drawable images
     *
     * @param resourceId GIF image resource ID
     */
    public void setGifResource(int resourceId) {
        if (resourceId == -1) {
            return;
        }
        InputStream is = resources.openRawResource(resourceId);
        mMovie = Movie.decodeStream(is);
        requestLayout();
    }

    /**
     * Set a GIF input stream for network images
     *
     * @param is GIF image input stream
     */
    public void setGifInputStream(InputStream is) {
        mMovie = Movie.decodeStream(is);
        requestLayout();
    }

    /**
     * Pause playback
     */
    public void pause() {
        playStatus = 1;
        // Set the start time to zero so onDraw refreshes it
        mMovieStart = 0;
        // Record the playback position at pause time to compensate when resuming
        offsetTime = relTime;

        invalidate();
    }

    /**
     * Resume playback
     */
    public void resume() {
        playStatus = 0;

        invalidate();
    }

    /**
     * Restart playback from the beginning
     */
    public void restart() {
        playStatus = 0;
        // Set the start time to zero so onDraw refreshes it
        mMovieStart = 0;

        invalidate();
    }

    /**
     * Whether playback is paused
     *
     * @return true when paused, false when playing
     */
    public boolean isPaused() {
        // Both paused and single-frame playback count as paused
        return playStatus != 0;
    }

    /**
     * Seek to a specific playback position
     *
     * @param progress Target position; values below zero or beyond the GIF duration are ignored
     */
    public void seekTo(int progress) {
        if (mMovie == null) {
            return;
        }

        if (progress >= 0 && progress < mMovie.duration()) {
            // Set the start time to zero; onDraw will reset it
            mMovieStart = 0;
            // Record the selected position to compensate when playback resumes
            offsetTime = progress;
            invalidate();
        }
    }

    /**
     * Get the current GIF playback position
     *
     * @return Playback position, or -1 if no animation is loaded
     */
    public int getProgress() {
        if (mMovie == null) {
            return -1;
        }
        return relTime;
    }

    /**
     * Get the total GIF duration
     *
     * @return Total duration, or -1 if no animation is loaded
     */
    public int getDuration() {
        if (mMovie == null) {
            return -1;
        }
        return mMovie.duration();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate dimensions when a GIF is loaded, following ImageView.onMeasure
        if (mMovie != null) {
            int w;
            int h;

            // Get GIF width and height
            w = mMovie.width();
            h = mMovie.height();
            if (w <= 0) w = 1;
            if (h <= 0) h = 1;

            int pleft = getPaddingLeft();
            int pright = getPaddingRight();
            int ptop = getPaddingTop();
            int pbottom = getPaddingBottom();

            int widthSize;
            int heightSize;

            /* We are either don't want to preserve the drawables aspect ratio,
               or we are not allowed to change view dimensions. Just measure in
               the normal way.
            */
            w += pleft + pright;
            h += ptop + pbottom;

            w = Math.max(w, getSuggestedMinimumWidth());
            h = Math.max(h, getSuggestedMinimumHeight());

            // Calculate the desired view size from the width and height MeasureSpecs
            widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
            heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);

            // Calculate view-to-GIF scale factors so onDraw fits the image to the view
            ratioWidth = (float) widthSize / w;
            ratioHeight = (float) heightSize / h;

            // Set the view width and height
            setMeasuredDimension(widthSize, heightSize);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();

        if (mMovie != null) {
            // Get the GIF animation duration
            int dur = mMovie.duration();
            if (dur == 0) {
                dur = 1000;
            }

            switch (playStatus) {
                case 0: // Play
                    // Get a new start time when playback restarts
                    if (mMovieStart == 0) {
                        mMovieStart = now;
                    }

                    // Compute playback position as elapsed time modulo animation duration
                    // Subtract offsetTime to account for pauses and single-frame playback
                    relTime = (int) ((now - mMovieStart + offsetTime) % dur);

                    // Set the playback progress listener
                    if (onGifPlayingListener != null) {
                        onGifPlayingListener.onProgress(relTime);
                    }
                    break;
                case 1: // Pause
                    // Keep relTime unchanged to display the paused frame
                    relTime = (int) offsetTime;
                    break;
                default:
                    break;
            }

            // Display the animation at a specified playback position
            mMovie.setTime(relTime);

            // Set scale factors
            canvas.scale(Math.min(ratioWidth, ratioHeight),
                    Math.min(ratioWidth, ratioHeight));

            // Draw
            mMovie.draw(canvas, 0, 0);

            invalidate();
        }
    }

    private OnGifPlayingListener onGifPlayingListener;

    public void setOnGifPlayingListener(OnGifPlayingListener onGifPlayingListener) {
        this.onGifPlayingListener = onGifPlayingListener;
    }

    public interface OnGifPlayingListener {
        void onProgress(int time);
    }

}
