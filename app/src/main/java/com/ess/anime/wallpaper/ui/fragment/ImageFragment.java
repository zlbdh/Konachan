package com.ess.anime.wallpaper.ui.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.MsgBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.listener.FlingEffector;
import com.ess.anime.wallpaper.model.helper.ImageDataHelper;
import com.ess.anime.wallpaper.ui.activity.ImageDetailActivity;
import com.ess.anime.wallpaper.ui.view.MultipleMediaLayout;
import com.ess.anime.wallpaper.utils.SystemUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Iterator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ImageFragment extends BaseFragment {

    private View mTouchView;
    private SwipeRefreshLayout mSwipeRefresh;
    private MultipleMediaLayout mMediaLayout;

    // Multi-page gallery navigation (e.g. nhentai)
    private View mLayoutPageNav;
    private TextView mTvPageIndicator;
    private ImageView mIvPagePrev;
    private ImageView mIvPageNext;

    private ImageDetailActivity mActivity;
    private ThumbBean mThumbBean;
    private ImageBean mImageBean;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (ImageDetailActivity) context;
    }

    @Override
    int layoutRes() {
        return R.layout.fragment_image;
    }

    @Override
    void init(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mThumbBean = savedInstanceState.getParcelable(Constants.THUMB_BEAN);
            mImageBean = savedInstanceState.getParcelable(Constants.IMAGE_BEAN);
        } else {
            mThumbBean = mActivity.getThumbBean();
            mImageBean = mActivity.getImageBean();
        }
        initView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Prevent a null pointer when returning after the system reclaims an app left in the background
        outState.putParcelable(Constants.THUMB_BEAN, mThumbBean);
        outState.putParcelable(Constants.IMAGE_BEAN, mImageBean);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Notify MultipleMediaLayout
        EventBus.getDefault().post(new MsgBean(Constants.RESUME_VIDEO, mMediaLayout.getMediaPath()));
    }

    @Override
    public void onPause() {
        super.onPause();
        // Notify MultipleMediaLayout
        EventBus.getDefault().post(new MsgBean(Constants.PAUSE_VIDEO, mMediaLayout.getMediaPath()));
    }

    @Override
    public void onDestroyView() {
        mMediaLayout.reset();
        super.onDestroyView();
    }

    private void initView() {
        mTouchView = mRootView.findViewById(R.id.view_touch);
        FlingEffector.addFlingEffect(mTouchView, (e1, e2, velocityX, velocityY) -> {
            if (SystemUtils.isActivityActive(mActivity)) {
                mActivity.flingToQuickSwitch(velocityX, velocityY);
            }
        });

        mSwipeRefresh = mRootView.findViewById(R.id.swipe_refresh_layout);
        mSwipeRefresh.setEnabled(false);
        mSwipeRefresh.setOnRefreshListener(this::loadMedia);

        mMediaLayout = mRootView.findViewById(R.id.layout_multiple_media);
        if (mImageBean != null) {
            loadMedia();
            mTouchView.setVisibility(View.GONE);
        } else {
            mSwipeRefresh.setRefreshing(true);
            mSwipeRefresh.getChildAt(0).setVisibility(View.GONE);
            mTouchView.setVisibility(View.VISIBLE);
        }

        mMediaLayout.setOnLongClickListener(v -> {
            downloadImage();
            return true;
        });

        mMediaLayout.getPhotoView().setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            downloadImage();
            return true;
        });
        mMediaLayout.getPhotoView().setOnSingleFlingListener((e1, e2, velocityX, velocityY) -> {
            if (SystemUtils.isActivityActive(mActivity)) {
                mActivity.flingToQuickSwitch(velocityX, velocityY);
            }
            return false;
        });

        FlingEffector.addFlingEffect(mMediaLayout, (e1, e2, velocityX, velocityY) -> {
            if (SystemUtils.isActivityActive(mActivity)) {
                mActivity.flingToQuickSwitch(velocityX, velocityY);
            }
        });

        mMediaLayout.setBackgroundColor(Color.TRANSPARENT);

        initPageNav();
    }

    /** Multi-page gallery navigation bar; hidden for single-image sites. */
    private void initPageNav() {
        mLayoutPageNav = mRootView.findViewById(R.id.layout_page_nav);
        mTvPageIndicator = mRootView.findViewById(R.id.tv_page_indicator);
        mIvPagePrev = mRootView.findViewById(R.id.iv_page_prev);
        mIvPageNext = mRootView.findViewById(R.id.iv_page_next);
        mIvPagePrev.setOnClickListener(v -> goToPage(mImageBean.currentPage - 1));
        mIvPageNext.setOnClickListener(v -> goToPage(mImageBean.currentPage + 1));
        updatePageNav();
    }

    private void updatePageNav() {
        if (mLayoutPageNav == null) {
            return;
        }
        boolean multi = mImageBean != null && mImageBean.hasMultiPages();
        mLayoutPageNav.setVisibility(multi ? View.VISIBLE : View.GONE);
        if (multi) {
            int total = mImageBean.pageUrls.size();
            int cur = Math.max(0, Math.min(mImageBean.currentPage, total - 1));
            mTvPageIndicator.setText((cur + 1) + "/" + total);
            mIvPagePrev.setAlpha(cur > 0 ? 0.9f : 0.3f);
            mIvPageNext.setAlpha(cur < total - 1 ? 0.9f : 0.3f);
        }
    }

    private void goToPage(int page) {
        if (mImageBean == null || !mImageBean.hasMultiPages()) {
            return;
        }
        int total = mImageBean.pageUrls.size();
        if (page < 0 || page >= total || page == mImageBean.currentPage) {
            return;
        }
        mImageBean.currentPage = page;
        if (mThumbBean != null) {
            mThumbBean.imageBean = mImageBean;
        }
        mMediaLayout.setMediaPath(mImageBean.pageUrls.get(page));
        updatePageNav();
    }

    private void loadMedia() {
        if (SystemUtils.isActivityActive(mActivity)) {
            // Multi-page gallery (e.g. nhentai): load the current page directly
            if (mImageBean != null && mImageBean.hasMultiPages()) {
                int total = mImageBean.pageUrls.size();
                int page = Math.max(0, Math.min(mImageBean.currentPage, total - 1));
                mImageBean.currentPage = page;
                // Prefer the local file if this page was already downloaded
                String path = mImageBean.pageUrls.get(page);
                List<DownloadBean> chosenList = ImageDataHelper.makeDownloadChosenList(mActivity, mThumbBean, mImageBean);
                for (DownloadBean bean : chosenList) {
                    if (bean.fileExists) {
                        path = bean.savePath;
                        break;
                    }
                }
                mMediaLayout.setMediaPath(path);
                updatePageNav();
                return;
            }
            List<DownloadBean> chosenList = ImageDataHelper.makeDownloadChosenList(mActivity, mThumbBean, mImageBean);
            Iterator<DownloadBean> iterator = chosenList.iterator();
            while (iterator.hasNext()) {
                DownloadBean downloadBean = iterator.next();
                if (!downloadBean.fileExists) {
                    iterator.remove();
                }
            }
            if (chosenList.isEmpty()) {
                mMediaLayout.setMediaPath(mImageBean.posts[0].getMinSizeImageUrl());
            } else {
                DownloadBean downloadBean = chosenList.get(chosenList.size() - 1);
                mMediaLayout.setMediaPath(downloadBean.savePath);
            }
        }
    }

    private void downloadImage() {
        if (SystemUtils.isActivityActive(mActivity)) {
            mActivity.showChooseToDownloadDialog();
        }
    }

    //Notification after receiving image details; obj is JSON (String)
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getImageDetail(MsgBean msgBean) {
        if (msgBean.msg.equals(Constants.GET_IMAGE_DETAIL)) {
            String json = (String) msgBean.obj;
            ImageBean imageBean = ImageBean.getImageDetailFromJson(json);
            if (mThumbBean.checkImageBelongs(imageBean) && imageBean.hasPostBean()) {
                setImageDetail(imageBean);
            }
        }
    }

    // Notification after PoolPostFragment requests tempPost by ID following imageBean; obj is thumbBean
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void reloadDetailById(MsgBean msgBean) {
        if (msgBean.msg.equals(Constants.RELOAD_DETAIL_BY_ID)) {
            ThumbBean thumbBean = (ThumbBean) msgBean.obj;
            if (TextUtils.equals(mThumbBean.linkToShow, thumbBean.linkToShow)) {
                mThumbBean = thumbBean;
                setImageDetail(thumbBean.imageBean);
            }
        }
    }

    private void setImageDetail(ImageBean imageBean) {
        mImageBean = imageBean;
        mThumbBean.imageBean = imageBean;
        mThumbBean.checkToReplacePostData();
        loadMedia();
        updatePageNav();
        mSwipeRefresh.setRefreshing(false);
        mSwipeRefresh.getChildAt(0).setVisibility(View.VISIBLE);
        mTouchView.setVisibility(View.GONE);
        mActivity.setId(imageBean);
        mActivity.setImageBean(imageBean);
    }

}
