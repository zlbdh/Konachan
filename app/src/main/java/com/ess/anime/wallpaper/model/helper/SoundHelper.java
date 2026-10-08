package com.ess.anime.wallpaper.model.helper;

import android.content.Context;
import android.media.MediaPlayer;

import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.R;

public class SoundHelper {

    private static class SoundHolder {
        private static final SoundHelper instance = new SoundHelper();
    }

    public static SoundHelper getInstance() {
        return SoundHolder.instance;
    }

    private SoundHelper() {
    }

    /*******************************************************************/

    private MediaPlayer mMediaPlayer = new MediaPlayer();

    // Allow audio playback
    public void playSoundEnabled(Context context) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
        }
        mMediaPlayer = MediaPlayer.create(context, R.raw.allow_play_sound);
        mMediaPlayer.start();
    }

    // Disable audio playback
    public void playSoundDisabled() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
        }
    }

    // Play on the splash screen after restarting the app
    public void playSplashWelcomeSound(Context context) {
        if (Constants.sRestart && Constants.sAllowPlaySound) {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
            }
            mMediaPlayer = MediaPlayer.create(context, R.raw.welcome);
            mMediaPlayer.start();
            Constants.sRestart = false;
        }
    }

    // Play when switching to R18 mode
    public void playToggleR18ModeSound(Context context) {
        if (Constants.sAllowPlaySound) {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
            }
            mMediaPlayer = MediaPlayer.create(context, R.raw.toggle_r18_mode);
            mMediaPlayer.start();
        }
    }

    // Play when switching to Safe mode
    public void playToggleSafeModeSound(Context context) {
        if (Constants.sAllowPlaySound) {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
            }
            mMediaPlayer = MediaPlayer.create(context, R.raw.toggle_safe_mode);
            mMediaPlayer.start();
        }
    }

    // Play on a network error
    public void playLoadNoNetworkSound(Context context) {
        if (Constants.sAllowPlaySound && mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mMediaPlayer = MediaPlayer.create(context, R.raw.load_no_network);
            mMediaPlayer.start();
        }
    }

    // Play when a search has no results
    public void playLoadNothingSound(Context context) {
        if (Constants.sAllowPlaySound && mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            mMediaPlayer = MediaPlayer.create(context, R.raw.load_nothing);
            mMediaPlayer.start();
        }
    }

    // Play when the game is won
    public void playGameWinSound(Context context) {
        if (Constants.sAllowPlaySound) {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
            }
            mMediaPlayer = MediaPlayer.create(context, R.raw.game_win);
            mMediaPlayer.start();
        }
    }


    // Release the player when exiting the app
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}
