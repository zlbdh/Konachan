package com.ess.anime.wallpaper.ui.activity;

import android.os.Bundle;
import android.widget.ImageView;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.listener.OnTouchScaleListener;
import com.ess.anime.wallpaper.ui.view.GameSurfaceView;

import androidx.appcompat.widget.Toolbar;

public class GameActivity extends BaseActivity {

    private Toolbar mToolbar;
    private GameSurfaceView mGameView;
    private ImageView mIvGame;

    @Override
    protected int layoutRes() {
        return R.layout.activity_game;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        initToolBarLayout();
        initGameSurfaceView();
        initViews();
    }

    private void initToolBarLayout() {
        mToolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initGameSurfaceView() {
        mGameView = findViewById(R.id.surface_view_game);
        mGameView.setOnActionListener(bitmap -> mIvGame.setImageBitmap(bitmap));
    }

    private void initViews() {
        mIvGame = findViewById(R.id.iv_game);
        mIvGame.setImageBitmap(mGameView.getGameBitmap());

        OnTouchScaleListener touchListener = new OnTouchScaleListener(0.9f);
        findViewById(R.id.btn_column_3).setOnTouchListener(touchListener);
        findViewById(R.id.btn_column_4).setOnTouchListener(touchListener);
        findViewById(R.id.btn_column_5).setOnTouchListener(touchListener);
        findViewById(R.id.btn_restart).setOnTouchListener(touchListener);

        findViewById(R.id.btn_column_3).setOnClickListener(view -> {
            mGameView.changeColumn(3);
        });
        findViewById(R.id.btn_column_4).setOnClickListener(view -> {
            mGameView.changeColumn(4);
        });
        findViewById(R.id.btn_column_5).setOnClickListener(view -> {
            mGameView.changeColumn(5);
        });
        findViewById(R.id.btn_restart).setOnClickListener(view -> {
            mGameView.restartGame();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGameView.recycleBitmaps(true);
    }
}
