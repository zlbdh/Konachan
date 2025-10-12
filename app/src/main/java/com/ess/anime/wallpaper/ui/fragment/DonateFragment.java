package com.ess.anime.wallpaper.ui.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.listener.OnTouchScaleListener;
import com.ess.anime.wallpaper.model.helper.DonateHelper;
import com.ess.anime.wallpaper.ui.view.image.MyImageSwitcher;
import com.ess.anime.wallpaper.utils.SystemUtils;
import com.ess.anime.wallpaper.utils.UIUtils;

import java.util.Random;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class DonateFragment extends DialogFragment {

    private MyImageSwitcher mSwitcherTitle;
    private MyImageSwitcher mSwitcherImage;
    private ViewGroup mLayoutDonate;
    private ImageView mIvAlipay;
    private ImageView mIvWechat;
    private ImageView mIvClose;

    private boolean mHasClickedDonateButton;
    private boolean mHasDonated;
    private boolean mHasFlipped;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_donate, container, false);

        initViewByIds(rootView);
        initViewClickListeners(rootView);
        initViews();
        rootView.post(this::startAnim);
        return rootView;
    }

    private void initViewByIds(View rootView) {
        mSwitcherTitle = rootView.findViewById(R.id.switcher_title);
        mSwitcherImage = rootView.findViewById(R.id.switcher_image);
        mLayoutDonate = rootView.findViewById(R.id.layout_donate);
        mIvAlipay = rootView.findViewById(R.id.iv_alipay);
        mIvWechat = rootView.findViewById(R.id.iv_wechat);
        mIvClose = rootView.findViewById(R.id.iv_close);
    }

    private void initViewClickListeners(View rootView) {
        rootView.findViewById(R.id.iv_alipay).setOnClickListener(view -> {
            donateViaAlipay();
        });
        rootView.findViewById(R.id.iv_wechat).setOnClickListener(view -> {
            donateViaWechat();
        });
        rootView.findViewById(R.id.iv_close).setOnClickListener(view -> {
            dismissAllowingStateLoss();
        });
    }

    private void initViews() {
        OnTouchScaleListener listener = OnTouchScaleListener.DEFAULT;
        mIvAlipay.setOnTouchListener(listener);
        mIvWechat.setOnTouchListener(listener);

        int index = new Random().nextInt(2);
        int titleA = getResources().getIdentifier("ic_donate_title_a_" + index,
                "drawable", getContext().getPackageName());
        int titleB = getResources().getIdentifier("ic_donate_title_b_" + index,
                "drawable", getContext().getPackageName());
        mSwitcherTitle.loadImage(titleA, titleB);

        int imgA = getResources().getIdentifier("img_donate_a_" + index,
                "drawable", getContext().getPackageName());
        int imgB = getResources().getIdentifier("img_donate_b_" + index,
                "drawable", getContext().getPackageName());
        mSwitcherImage.loadImage(imgA, imgB);
    }

    private void startAnim() {
        mSwitcherImage.setScaleX(0);
        mSwitcherImage.setScaleY(0);
        mSwitcherImage.animate()
                .scaleX(1)
                .scaleY(1)
                .setInterpolator(new BounceInterpolator())
                .setDuration(1000)
                .start();

        float transY = UIUtils.getScreenHeight(getContext()) - mLayoutDonate.getY();
        mIvAlipay.setTranslationY(transY);
        mIvAlipay.animate()
                .translationY(0)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(500)
                .setStartDelay(1000)
                .start();

        mIvWechat.setTranslationY(transY);
        mIvWechat.animate()
                .translationY(0)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(500)
                .setStartDelay(1300)
                .start();

        transY = -mSwitcherTitle.getY() - mSwitcherTitle.getHeight() - UIUtils.getStatusBarHeight(getContext());
        mSwitcherTitle.setTranslationY(transY);
        mSwitcherTitle.animate()
                .translationY(0)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(800)
                .setStartDelay(1000)
                .start();

        mIvClose.setAlpha(0f);
        mIvClose.animate()
                .alpha(1)
                .setDuration(500)
                .setStartDelay(1800)
                .start();
    }


    private void donateViaAlipay() {
        if (SystemUtils.isActivityActive(getActivity())) {
            mHasClickedDonateButton = true;
            DonateHelper.donateViaAlipay(getActivity());
        }
    }

    private void donateViaWechat() {
        if (SystemUtils.isActivityActive(getActivity())) {
            mHasClickedDonateButton = true;
            DonateHelper.donateViaWechat(getActivity());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.setCanceledOnTouchOutside(false);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mHasDonated && !mHasFlipped) {
            mHasFlipped = true;
            mIvAlipay.setVisibility(View.GONE);
            mIvWechat.setVisibility(View.GONE);
            mSwitcherTitle.flipImage();
            mSwitcherImage.flipImage();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mHasClickedDonateButton) {
            mHasDonated = true;
        }
    }

    @Override
    public void dismissAllowingStateLoss() {
        super.dismissAllowingStateLoss();
    }

    /*************** 保持/恢复屏幕旋转 ***************/

    private int originalScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) {
            originalScreenOrientation = ((Activity) context).getRequestedOrientation();
        }
        keepScreenOrientation();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        freeScreenOrientation();
    }

    private void keepScreenOrientation() {
        Activity activity = getActivity();
        if (activity != null) {
            if (UIUtils.isLandscape(activity)) {
                if (UIUtils.isLandscapeReverse(activity)) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                } else {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }
            } else {
                if (UIUtils.isPortraitReverse(activity)) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                } else {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
        }
    }

    private void freeScreenOrientation() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.setRequestedOrientation(originalScreenOrientation);
        }
    }
}
