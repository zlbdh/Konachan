package com.ess.anime.wallpaper.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.adapter.RecyclerPixivGifDlAdapter;
import com.ess.anime.wallpaper.listener.OnTouchScaleListener;
import com.ess.anime.wallpaper.model.helper.PermissionHelper;
import com.ess.anime.wallpaper.pixiv.gif.PixivGifBean;
import com.ess.anime.wallpaper.pixiv.gif.PixivGifDlManager;
import com.ess.anime.wallpaper.pixiv.login.IPixivLoginListener;
import com.ess.anime.wallpaper.pixiv.login.PixivLoginManager;
import com.ess.anime.wallpaper.ui.view.CustomDialog;
import com.ess.anime.wallpaper.ui.view.GeneralRecyclerView;
import com.ess.anime.wallpaper.ui.view.GridDividerItemDecoration;
import com.ess.anime.wallpaper.utils.UIUtils;
import com.yanzhenjie.permission.runtime.Permission;

import java.util.Collections;
import java.util.List;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;

public class PixivGifActivity extends BaseActivity implements IPixivLoginListener {

    public final static String TAG = PixivGifActivity.class.getName();

    private Toolbar mToolbar;
    private ImageView mIvLoginState;
    private TextView mTvLoginState;
    private EditText mEtId;
    private GeneralRecyclerView mRvPixivGif;

    private GridLayoutManager mLayoutManager;
    private RecyclerPixivGifDlAdapter mAdapter;

    @Override
    protected int layoutRes() {
        return R.layout.activity_pixiv_gif;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        initViewByIds();
        initViewClickListeners();
        initToolBarLayout();
        initRecyclerPixivGif();

        PermissionHelper.checkStoragePermissions(this, new PermissionHelper.RequestListener() {
            @Override
            public void onGranted() {
                initWhenPermissionGranted();
            }

            @Override
            public void onDenied() {
                finish();
            }
        });
    }

    @Override
    protected void updateUI() {
        super.updateUI();
        updateRecyclerViewSpanCount();
    }

    private void initViewByIds() {
        mToolbar = findViewById(R.id.tool_bar);
        mIvLoginState = findViewById(R.id.iv_login_state);
        mTvLoginState = findViewById(R.id.tv_login_state);
        mEtId = findViewById(R.id.et_id);
        mRvPixivGif = findViewById(R.id.rv_pixiv_gif);
    }

    private void initViewClickListeners() {
        findViewById(R.id.iv_clear_all).setOnClickListener(view -> {
            clearAllFinished();
        });
        findViewById(R.id.iv_goto_collection).setOnClickListener(view -> {
            gotoCollection();
        });
        findViewById(R.id.btn_download).setOnClickListener(view -> {
            startDownload();
        });
    }

    private void initToolBarLayout() {
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void clearAllFinished() {
        CustomDialog.showClearAllDownloadFinishedDialog(this, new CustomDialog.SimpleDialogActionListener() {
            @Override
            public void onPositive() {
                super.onPositive();
                PixivGifDlManager.getInstance().clearAllFinished();
            }
        });
    }

    private void gotoCollection() {
        startActivity(new Intent(this, CollectionActivity.class));
    }

    private void initWhenPermissionGranted() {
        initViews();
        resetDownloadData();
        PixivLoginManager.getInstance().registerLoginListener(this);
    }

    private void initViews() {
        findViewById(R.id.btn_download).setOnTouchListener(OnTouchScaleListener.DEFAULT);

        mEtId.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                startDownload();
                return true;
            }
            return false;
        });

        findViewById(R.id.layout_login_state).setVisibility(View.VISIBLE);
        findViewById(R.id.layout_login_state).setOnClickListener(v -> {
            if (PixivLoginManager.getInstance().isLogin()) {
                CustomDialog.showPixivLoginStateDialog(this);
            } else {
                PixivLoginManager.getInstance().login(this);
            }
        });
        updatePixivLoginState();
    }

    private void updatePixivLoginState() {
        if (PixivLoginManager.getInstance().isLogin()) {
            if (PixivLoginManager.getInstance().isCookieExpired()) {
                mIvLoginState.setImageResource(R.drawable.ic_piviv_login_state_login_expired);
                mTvLoginState.setText(R.string.piviv_login_state_login_expired);
            } else {
                mIvLoginState.setImageResource(R.drawable.ic_pixiv_login_state_already_logged);
                mTvLoginState.setText(R.string.piviv_login_state_already_logged);
            }
        } else {
            mIvLoginState.setImageResource(R.drawable.ic_pixiv_login_state_not_logged);
            mTvLoginState.setText(R.string.piviv_login_state_not_logged);
        }
    }

    @Override
    public void onLoginStateChanged() {
        updatePixivLoginState();
    }

    private void initRecyclerPixivGif() {
        mLayoutManager = new GridLayoutManager(this, 1);
        mRvPixivGif.setLayoutManager(mLayoutManager);

        mAdapter = new RecyclerPixivGifDlAdapter();
        mAdapter.bindToRecyclerView(mRvPixivGif);
    }

    private void updateRecyclerViewSpanCount() {
        if (mLayoutManager != null && mRvPixivGif != null) {
            int span = UIUtils.isLandscape(this) ? 2 : 1;
            mLayoutManager.setSpanCount(span);

            int spaceHor = UIUtils.dp2px(this, 5);
            int spaceVer = UIUtils.dp2px(this, 10);
            mRvPixivGif.clearItemDecorations();
            mRvPixivGif.addItemDecoration(new GridDividerItemDecoration(
                    span, GridDividerItemDecoration.VERTICAL, spaceHor, spaceVer, true));
        }
    }

    private void resetDownloadData() {
        List<PixivGifBean> downloadList = PixivGifDlManager.getInstance().getDownloadList();
        Collections.reverse(downloadList);
        mAdapter.setNewData(downloadList);
    }

    private void startDownload() {
        String pixivId = mEtId.getText().toString();
        if (!TextUtils.isEmpty(pixivId)) {
            PixivGifDlManager.getInstance().execute(pixivId);
            mEtId.setText(null);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing()) {
            release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
    }

    private void release() {
        mRvPixivGif.setAdapter(null);
        PixivLoginManager.getInstance().unregisterLoginListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PermissionHelper.REQ_CODE_PERMISSION) {
            // 进入系统设置界面请求权限后的回调
            if (PermissionHelper.hasStoragePermissions(this)) {
                initWhenPermissionGranted();
            } else {
                finish();
            }
        }
    }

}
