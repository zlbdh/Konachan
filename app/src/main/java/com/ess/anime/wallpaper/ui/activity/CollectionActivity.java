package com.ess.anime.wallpaper.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.adapter.RecyclerCollectionAdapter;
import com.ess.anime.wallpaper.bean.CollectionBean;
import com.ess.anime.wallpaper.bean.MsgBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.listener.DoubleTapEffector;
import com.ess.anime.wallpaper.listener.LocalCollectionsListener;
import com.ess.anime.wallpaper.model.helper.PermissionHelper;
import com.ess.anime.wallpaper.model.holder.ImageDataHolder;
import com.ess.anime.wallpaper.ui.view.CustomDialog;
import com.ess.anime.wallpaper.ui.view.GeneralRecyclerView;
import com.ess.anime.wallpaper.ui.view.GridDividerItemDecoration;
import com.ess.anime.wallpaper.utils.BitmapUtils;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.ess.anime.wallpaper.utils.UIUtils;
import com.ess.anime.wallpaper.utils.VibratorUtils;
import com.mixiaoxiao.smoothcompoundbutton.SmoothCheckBox;
import com.qmuiteam.qmui.util.QMUIDeviceHelper;
import com.yanzhenjie.permission.runtime.Permission;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;

public class CollectionActivity extends BaseActivity implements LocalCollectionsListener.OnFilesChangedListener {

    private Toolbar mToolbar;
    private TextView mTvEdit;
    private ViewGroup mLayoutEditing;
    private TextView mTvChooseCount;
    private SmoothCheckBox mCbChooseAll;
    private GeneralRecyclerView mRvCollection;

    private GridLayoutManager mLayoutManager;
    private RecyclerCollectionAdapter mCollectionAdapter;

    private LocalCollectionsListener mFilesListener;

    @Override
    protected int layoutRes() {
        return R.layout.activity_collection;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        initViewByIds();
        initViewClickListeners();
        initToolBarLayout();
        initRecyclerView();
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
        mTvEdit = findViewById(R.id.tv_edit);
        mLayoutEditing = findViewById(R.id.layout_editing);
        mTvChooseCount = findViewById(R.id.tv_choose_count);
        mCbChooseAll = findViewById(R.id.cb_choose_all);
        mRvCollection = findViewById(R.id.rv_collection);
    }

    private void initViewClickListeners() {
        findViewById(R.id.layout_choose_all).setOnClickListener(view -> {
            mCbChooseAll.toggle();
        });
        findViewById(R.id.cb_choose_all).setOnClickListener(view -> {
            if (mCbChooseAll.isChecked()) {
                mCollectionAdapter.selectAll();
            } else {
                mCollectionAdapter.deselectAll();
            }
        });
        findViewById(R.id.tv_edit).setOnClickListener(view -> {
            enterEditMode();
        });
        findViewById(R.id.tv_share).setOnClickListener(view -> {
            shareImages();
            exitEditMode(true);
        });
        findViewById(R.id.tv_delete).setOnClickListener(view -> {
            List<CollectionBean> deleteList = new ArrayList<>(mCollectionAdapter.getSelectList());
            showDeleteCollectionDialog(deleteList);
        });
    }

    private void initWhenPermissionGranted() {
        mCollectionAdapter.setNewData(CollectionBean.getCollectionImages());
        mFilesListener = new LocalCollectionsListener(this);
        mFilesListener.startWatching();
    }

    private void initToolBarLayout() {
        mToolbar.setTitle(R.string.nav_collection);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mToolbar.setNavigationIcon(R.drawable.ic_back);
        mToolbar.setNavigationOnClickListener(v -> finish());
        DoubleTapEffector.addDoubleTapEffect(mToolbar, this::scrollToTop);
    }

    private void initRecyclerView() {
        mLayoutManager = new GridLayoutManager(this, 1);
        mRvCollection.setLayoutManager(mLayoutManager);
        mCollectionAdapter = new RecyclerCollectionAdapter();
        mCollectionAdapter.bindToRecyclerView(mRvCollection);

        // 长按进入编辑模式监听器
        mCollectionAdapter.setOnItemChildLongClickListener((adapter, view, position) -> {
            if (view.getId() == R.id.iv_collection && !mCollectionAdapter.isEditMode()) {
                CollectionBean collectionBean = mCollectionAdapter.getItem(position);
                mCollectionAdapter.select(collectionBean);
                mCollectionAdapter.enterEditMode();
                VibratorUtils.Vibrate(view.getContext(), 12);
                toggleEditView(true);
            }
            return false;
        });

        // 切换选中/非选中监听器
        mCollectionAdapter.setOnSelectChangedListener((selectCount, allSelected) -> {
            mTvChooseCount.setText(String.valueOf(selectCount));
            mCbChooseAll.setChecked(allSelected);
        });
    }

    private void updateRecyclerViewSpanCount() {
        if (mLayoutManager != null && mRvCollection != null) {
            int span;
            if (UIUtils.isLandscape(this)) {
                span = QMUIDeviceHelper.isTablet(this) ? 5 : 4;
            } else {
                span = QMUIDeviceHelper.isTablet(this) ? 4 : 3;
            }
            mLayoutManager.setSpanCount(span);

            int spanHor = UIUtils.dp2px(this, 0.75f);
            int spanVer = UIUtils.dp2px(this, 1.5f);
            mRvCollection.clearItemDecorations();
            mRvCollection.addItemDecoration(new GridDividerItemDecoration(
                    span, GridDividerItemDecoration.VERTICAL, spanHor, spanVer, true));

            mCollectionAdapter.updateItemSize();
        }
    }

    private void scrollToTop() {
        int smoothPos = 8 * mLayoutManager.getSpanCount();
        if (mLayoutManager.findLastVisibleItemPosition() > smoothPos) {
            mRvCollection.scrollToPosition(smoothPos);
        }
        mRvCollection.smoothScrollToPosition(0);
    }

    private void showDeleteCollectionDialog(List<CollectionBean> deleteList) {
        CustomDialog.showDeleteCollectionDialog(this, deleteList.size(), new CustomDialog.SimpleDialogActionListener() {
            @Override
            public void onPositive() {
                exitEditMode(false);
                mCollectionAdapter.removeDatas(deleteList);
                for (CollectionBean collectionBean : deleteList) {
                    String path = collectionBean.url.replace("file://", "");
                    FileUtils.deleteFile(path);
                    // 从媒体库删除图片（刷新相册）
                    BitmapUtils.deleteFromMediaStore(CollectionActivity.this, path);
                }
            }
        });
    }

    private void toggleEditView(boolean editing) {
        mCbChooseAll.setChecked(false, false, false);
        if (editing) {
            mTvEdit.setVisibility(View.GONE);
            mLayoutEditing.setVisibility(View.VISIBLE);
            mToolbar.setTitle("");
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        } else {
            mTvEdit.setVisibility(View.VISIBLE);
            mLayoutEditing.setVisibility(View.GONE);
            initToolBarLayout();
        }
    }

    private void enterEditMode() {
        toggleEditView(true);
        mCollectionAdapter.enterEditMode();
    }

    private void exitEditMode(boolean notify) {
        toggleEditView(false);
        mCollectionAdapter.exitEditMode(notify);
    }

    private void shareImages() {
        ArrayList<Uri> uriList = new ArrayList<>();
        for (CollectionBean collectionBean : mCollectionAdapter.getSelectList()) {
            Uri uri = BitmapUtils.getContentUriFromFile(this, new File(collectionBean.filePath));
            if (uri != null) uriList.add(uri);
        }
        if (uriList.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("*/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        android.content.ClipData clip = android.content.ClipData.newRawUri("images", uriList.get(0));
        for (int index = 1; index < uriList.size(); index++) {
            clip.addItem(new android.content.ClipData.Item(uriList.get(index)));
        }
        intent.setClipData(clip);
        startActivity(Intent.createChooser(intent, getString(R.string.share_title)));
    }

    @Override
    public void onFileAdded(final File file) {
        if (mCollectionAdapter.isEditMode()) {
            exitEditMode(false);
        }
        CollectionBean collectionBean = CollectionBean.createCollectionFromFile(file);
        if (!mCollectionAdapter.getData().contains(collectionBean)) {
            mCollectionAdapter.addData(0, collectionBean);
        }
        mRvCollection.scrollToPosition(0);
        // 发送通知到FullscreenActivity
        EventBus.getDefault().post(new MsgBean(Constants.LOCAL_FILES_CHANGED, null));
    }

    @Override
    public void onFileRemoved(final File file) {
        if (mCollectionAdapter.isEditMode()) {
            exitEditMode(false);
        }
        CollectionBean collectionBean = CollectionBean.createCollectionFromFile(file);
        mCollectionAdapter.removeData(collectionBean);
        // 发送通知到FullscreenActivity
        EventBus.getDefault().post(new MsgBean(Constants.LOCAL_FILES_CHANGED, null));
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
        } else if (resultCode == Constants.FULLSCREEN_CODE && data != null) {
            // 退出全屏回调
            int position = ImageDataHolder.getCollectionCurrentItem();
            mRvCollection.scrollToPosition(position);
        }
    }

    @Override
    public void onBackPressed() {
        if (mCollectionAdapter.isEditMode()) {
            exitEditMode(true);
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFilesListener != null) {
            mFilesListener.stopWatching();
        }
    }

}
