package com.ess.anime.wallpaper.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.adapter.RecyclerPostAdapter;
import com.ess.anime.wallpaper.bean.ImageBean;
import com.ess.anime.wallpaper.bean.MsgBean;
import com.ess.anime.wallpaper.bean.ThumbBean;
import com.ess.anime.wallpaper.glide.MyGlideModule;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.HandlerFuture;
import com.ess.anime.wallpaper.http.OkHttp;
import com.ess.anime.wallpaper.listener.DoubleTapEffector;
import com.ess.anime.wallpaper.model.helper.SoundHelper;
import com.ess.anime.wallpaper.model.helper.BatchDownloadController;
import com.ess.anime.wallpaper.model.helper.PermissionHelper;
import com.ess.anime.wallpaper.ui.activity.MainActivity;
import com.ess.anime.wallpaper.ui.activity.PopularActivity;
import com.ess.anime.wallpaper.ui.activity.SearchActivity;
import com.ess.anime.wallpaper.ui.view.CustomDialog;
import com.ess.anime.wallpaper.ui.view.CustomLoadMoreView;
import com.ess.anime.wallpaper.ui.view.GeneralRecyclerView;
import com.ess.anime.wallpaper.ui.view.GridDividerItemDecoration;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.ess.anime.wallpaper.utils.SystemUtils;
import com.ess.anime.wallpaper.utils.UIUtils;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.ess.anime.wallpaper.website.WebsiteManager;
import com.ess.anime.wallpaper.website.parser.HtmlParser;
import com.github.clans.fab.FloatingActionMenu;
import com.qmuiteam.qmui.util.QMUIDeviceHelper;
import com.zyyoona7.popup.EasyPopup;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jsoup.Jsoup;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class PostFragment extends BaseFragment implements
        WebsiteManager.OnWebsiteChangeListener,
        BaseQuickAdapter.RequestLoadMoreListener {

    public final static String TAG = PostFragment.class.getName();

    private Toolbar mToolbar;
    private FloatingActionMenu mFloatingMenu;
    private SwipeRefreshLayout mSwipeRefresh;
    private GeneralRecyclerView mRvPosts;

    private MainActivity mActivity;
    private StaggeredGridLayoutManager mLayoutManager;
    private RecyclerPostAdapter mPostAdapter;
    private BatchDownloadController mBatchDownloadController;

    private EasyPopup mPopupPage;
    private TextView mTvFrom;
    private TextView mTvTo;
    private EditText mEtGoto;
    private int mCurrentPage;  // Current page number
    private int mGoToPage;  // Starting page to jump to
    private String mCurrentTag;   // Tag currently being searched
    private List<String> mCurrentTagList;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (MainActivity) context;
    }

    @Override
    public void onDetach() {
        mActivity = null;
        super.onDetach();
    }

    @Override
    int layoutRes() {
        return R.layout.fragment_post;
    }

    @Override
    void init(Bundle savedInstanceState) {
        mBatchDownloadController = new BatchDownloadController(mActivity);
        initViewByIds();
        initViewClickListeners();
        initToolBarLayout();
        initPopupPage();
        initSwipeRefreshLayout();
        initRecyclerView();
        mCurrentPage = 1;
        mGoToPage = 1;
        mCurrentTag = "";
        mCurrentTagList = new ArrayList<>();
        getNewPosts(mCurrentPage);
        changeFromPage(mCurrentPage);
        changeToPage(mCurrentPage);
        WebsiteManager.getInstance().registerWebsiteChangeListener(this);
    }

    @Override
    void updateUI() {
        super.updateUI();
        dismissPopupPage();
        updateRecyclerViewSpanCount();
    }

    @Override
    public void onDestroyView() {
        if (mBatchDownloadController != null) mBatchDownloadController.dispose();
        super.onDestroyView();
        OkHttp.cancel(TAG);
        WebsiteManager.getInstance().unregisterWebsiteChangeListener(this);
    }

    private void initViewByIds() {
        mToolbar = mRootView.findViewById(R.id.tool_bar);
        mFloatingMenu = mRootView.findViewById(R.id.floating_action_menu);
        mSwipeRefresh = mRootView.findViewById(R.id.swipe_refresh_layout);
        mRvPosts = mRootView.findViewById(R.id.rv_post);
    }

    private void initViewClickListeners() {
        mRootView.findViewById(R.id.iv_format).setOnClickListener(view -> {
            toggleImageShownFormat();
        });
        mRootView.findViewById(R.id.iv_page).setOnClickListener(view -> {
            gotoPage(view);
        });
        mRootView.findViewById(R.id.iv_search).setOnClickListener(view -> {
            openSearch();
        });
        mRootView.findViewById(R.id.iv_batch_download).setOnClickListener(view -> {
            showBatchDownloadDialog();
        });
        mRootView.findViewById(R.id.fab_home).setOnClickListener(view -> {
            searchHome();
        });
        mRootView.findViewById(R.id.fab_random).setOnClickListener(view -> {
            searchRandom();
        });
        mRootView.findViewById(R.id.fab_popular).setOnClickListener(view -> {
            searchPopular();
        });
    }

    private void initToolBarLayout() {
        mActivity.setSupportActionBar(mToolbar);
        DrawerLayout drawerLayout = mActivity.getDrawerLayout();
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                mActivity,
                drawerLayout,
                mToolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        mToolbar.setNavigationIcon(WebsiteManager.getInstance().getWebsiteConfig().getWebsiteLogoRes());

        //Double tap to scroll to the top
        DoubleTapEffector.addDoubleTapEffect(mToolbar, () -> {
            scrollToTop();
            mFloatingMenu.close(true);
        });

        // Long press the icon to select a site
        try {
            Field mNavButtonViewField = mToolbar.getClass().getDeclaredField("mNavButtonView");
            mNavButtonViewField.setAccessible(true);
            View mNavButtonView = (View) mNavButtonViewField.get(mToolbar);
            mNavButtonView.setOnLongClickListener(v -> {
                CustomDialog.showChangeBaseUrlDialog(mActivity, null);
                return true;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Switch image display mode (grid or waterfall)
    private void toggleImageShownFormat() {
        boolean newFormat = !isPostImageShownRectangular();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.edit().putBoolean(Constants.IS_POST_IMAGE_SHOWN_RECTANGULAR, newFormat).apply();
        mPostAdapter.changeImageShownFormat(newFormat);
        scrollToTop();
        mFloatingMenu.close(true);
    }

    private boolean isPostImageShownRectangular() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return preferences.getBoolean(Constants.IS_POST_IMAGE_SHOWN_RECTANGULAR, true);
    }

    // Show the page navigation dialog
    private void gotoPage(View view) {
        mPopupPage.showAsDropDown(view);
        mEtGoto.selectAll();
        mEtGoto.post(() -> UIUtils.showSoftInput(mActivity, mEtGoto));
    }

    //Search
    private void openSearch() {
        mFloatingMenu.close(true);
        Intent searchIntent = new Intent(mActivity, SearchActivity.class);
        startActivityForResult(searchIntent, Constants.SEARCH_CODE);
    }

    private void showBatchDownloadDialog() {
        List<ThumbBean> data = mPostAdapter.getData();
        mFloatingMenu.close(true);
        mBatchDownloadController.show(data);
    }

    private void initPopupPage() {
        mPopupPage = EasyPopup.create()
                .setContentView(mActivity, R.layout.layout_popup_goto_page)
                .setFocusAndOutsideEnable(true)
                .setBackgroundDimEnable(true)
                .setDimValue(0.4f)
                .apply();

        // First and last pages currently displayed
        mTvFrom = mPopupPage.findViewById(R.id.tv_from);
        mTvTo = mPopupPage.findViewById(R.id.tv_to);

        // Page navigation
        mEtGoto = mPopupPage.findViewById(R.id.et_goto);
        mEtGoto.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String num = mEtGoto.getText().toString();
                if (!TextUtils.isEmpty(num)) {
                    try {
                        int newPage = Integer.parseInt(num);
                        if (newPage > 0) {
                            resetAll(newPage);
                            getNewPosts(mCurrentPage);
                            changeFromPage(mCurrentPage);
                            changeToPage(mCurrentPage);
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid or out-of-range input
                    }
                }
                mPopupPage.dismiss();
            }
            return false;
        });
    }

    private void dismissPopupPage() {
        if (mPopupPage != null) {
            mPopupPage.dismiss();
        }
    }

    private void initSwipeRefreshLayout() {
        mSwipeRefresh.setRefreshing(true);
        //Pull to refresh
        mSwipeRefresh.setOnRefreshListener(() -> {
            getNewPosts(mGoToPage);
            if (mPostAdapter.getData().isEmpty()) {
                mPostAdapter.setEmptyView(R.layout.layout_loading_cirno, mRvPosts);
            }
        });
    }

    private void initRecyclerView() {
        mLayoutManager = new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL);
        mRvPosts.setLayoutManager(mLayoutManager);

        mPostAdapter = new RecyclerPostAdapter(TAG);
        mPostAdapter.bindToRecyclerView(mRvPosts);
        mPostAdapter.setOnItemClickListener(() -> mFloatingMenu.close(true));
        mPostAdapter.setOnLoadMoreListener(this, mRvPosts);
        mPostAdapter.setLoadMoreView(new CustomLoadMoreView());
        mPostAdapter.setEmptyView(R.layout.layout_loading_cirno, mRvPosts);
        mPostAdapter.changeImageShownFormat(isPostImageShownRectangular());

        // Hide the FAB while scrolling
        mRvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (!mPostAdapter.getData().isEmpty() && mFloatingMenu != null) {
                    switch (newState) {
                        case RecyclerView.SCROLL_STATE_IDLE:
                            mFloatingMenu.showMenu(true);
                            break;
                        case RecyclerView.SCROLL_STATE_DRAGGING:
                            mFloatingMenu.hideMenu(true);
                            break;
                    }
                }
            }
        });
    }

    private void updateRecyclerViewSpanCount() {
        if (mActivity != null && mLayoutManager != null && mRvPosts != null) {
            int span;
            if (UIUtils.isLandscape(mActivity)) {
                span = 4;
            } else {
                span = QMUIDeviceHelper.isTablet(mActivity) ? 3 : 2;
            }
            mLayoutManager.setSpanCount(span);

            int spaceHor = UIUtils.dp2px(mActivity, 5);
            int spaceVer = UIUtils.dp2px(mActivity, 10);
            mRvPosts.clearItemDecorations();
            mRvPosts.addItemDecoration(new GridDividerItemDecoration(
                    span, GridDividerItemDecoration.VERTICAL, spaceHor, spaceVer, true));

            mPostAdapter.setPreLoadNumber(span * 5);
        }
    }

    // Scroll to load more
    @Override
    public void onLoadMoreRequested() {
        String url = WebsiteManager.getInstance().getWebsiteConfig().getPostUrl(++mCurrentPage, mCurrentTagList);
        Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
        OkHttp.connect(url, TAG, headerMap, new OkHttp.OkHttpCallback() {
            @Override
            public void onFailure(int errorCode, String errorMessage) {
                if (errorCode == 404) {
                    // Treat 404 as success so the UI shows no results rather than an access error
                    onSuccessful(errorMessage);
                } else {
                    checkNetwork(errorCode, errorMessage);
                }
            }

            @Override
            public void onSuccessful(String body) {
                HandlerFuture.ofWork(body)
                        .applyThen(body1 -> {
                            return WebsiteManager.getInstance()
                                    .getWebsiteConfig()
                                    .getHtmlParser()
                                    .getThumbList(Jsoup.parse(body1));
                        })
                        .runOn(HandlerFuture.IO.UI)
                        .applyThen(thumbList -> {
                            addMoreThumbList(thumbList);
                        });

            }
        }, Request.Priority.IMMEDIATE);
    }

    //Refresh the screen after loading more results
    private void addMoreThumbList(final List<ThumbBean> newList) {
        if (!mPostAdapter.isLoading()) {
            return;
        }

        mPostAdapter.setEmptyView(R.layout.layout_load_nothing, mRvPosts);
        if (mPostAdapter.loadMoreDatas(newList)) {
            mPostAdapter.loadMoreComplete();
            changeToPage(mCurrentPage);
        } else {
            mPostAdapter.loadMoreEnd(true);
        }
    }

    private void searchHome() {
        mFloatingMenu.close(true);
        Intent intent = new Intent();
        intent.putExtra(Constants.SEARCH_MODE, Constants.SEARCH_MODE_HOME);
        onActivityResult(Constants.SEARCH_CODE, Constants.SEARCH_CODE, intent);
    }

    private void searchRandom() {
        mFloatingMenu.close(true);
        if (WebsiteManager.getInstance().getWebsiteConfig().isSupportRandomPost()) {
            Intent intent = new Intent();
            intent.putExtra(Constants.SEARCH_TAG, "order:random");
            intent.putExtra(Constants.SEARCH_MODE, Constants.SEARCH_MODE_RANDOM);
            onActivityResult(Constants.SEARCH_CODE, Constants.SEARCH_CODE, intent);
        } else {
            Toast.makeText(mActivity, R.string.cannot_search_random, Toast.LENGTH_SHORT).show();
        }
    }

    private void searchPopular() {
        mFloatingMenu.close(true);
        Intent intent = new Intent(mActivity, PopularActivity.class);
        startActivity(intent);
    }

    private void changeFromPage(int page) {
        mTvFrom.setText(String.valueOf(page));
    }

    private void changeToPage(int page) {
        mTvTo.setText(String.valueOf(page));
    }

    private void scrollToTop() {
        int smoothPos = 7 * mLayoutManager.getSpanCount();
        int lastVisiblePos = mLayoutManager.findLastVisibleItemPositions(null)[0];
        if (lastVisiblePos > smoothPos) {
            mRvPosts.scrollToPosition(smoothPos);
        }
        mRvPosts.smoothScrollToPosition(0);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.SEARCH_CODE) {
            if (data != null) {
                resetAll(1);
                mCurrentTagList.clear();

                String searchTag = data.getStringExtra(Constants.SEARCH_TAG);
                mCurrentTag = "#" + searchTag;
                switch (data.getIntExtra(Constants.SEARCH_MODE, Constants.SEARCH_MODE_TAGS)) {
                    case Constants.SEARCH_MODE_TAGS:
                        mCurrentTagList.addAll(Arrays.asList(searchTag.split("[,，]")));
                        getNewPosts(mCurrentPage);
                        changeFromPage(mCurrentPage);
                        changeToPage(mCurrentPage);
                        break;
                    case Constants.SEARCH_MODE_ID:
                        mCurrentTag = "#id:" + searchTag;
                        mCurrentTagList.add("id:" + searchTag);
                        getNewPosts(mCurrentPage);
                        changeFromPage(mCurrentPage);
                        changeToPage(mCurrentPage);
                        break;
                    case Constants.SEARCH_MODE_CHINESE:
                        getNameFromBaidu(searchTag);
                        break;
                    case Constants.SEARCH_MODE_ADVANCED:
                        String[] tags = searchTag.split(" ");
                        mCurrentTagList.addAll(Arrays.asList(tags));
                        getNewPosts(mCurrentPage);
                        changeFromPage(mCurrentPage);
                        changeToPage(mCurrentPage);
                        break;
                    case Constants.SEARCH_MODE_HOME:
                        mCurrentTag = "";
                        getNewPosts(mCurrentPage);
                        changeFromPage(mCurrentPage);
                        changeToPage(mCurrentPage);
                        break;
                    case Constants.SEARCH_MODE_RANDOM:
                        mCurrentTagList.add(searchTag);
                        getNewPosts(mCurrentPage);
                        changeFromPage(mCurrentPage);
                        changeToPage(mCurrentPage);
                        break;
                }
            }
        }
    }


    /**
     * Reset data and clear the adapter before loading new content
     *
     * @param startPage Starting page to load
     */
    private void resetAll(int startPage) {
        OkHttp.cancel(TAG);
        mPostAdapter.setEmptyView(R.layout.layout_loading_cirno, mRvPosts);
        mPostAdapter.setNewData(null);
        mSwipeRefresh.setRefreshing(true);
        mCurrentPage = startPage;
        mGoToPage = startPage;
    }

    private void getNewPosts(int page) {
        String url = WebsiteManager.getInstance().getWebsiteConfig().getPostUrl(page, mCurrentTagList);
        Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
        OkHttp.connect(url, TAG, headerMap, new OkHttp.OkHttpCallback() {
            @Override
            public void onFailure(int errorCode, String errorMessage) {
                if (errorCode == 404) {
                    // Treat 404 as success so the UI shows no results rather than an access error
                    onSuccessful(errorMessage);
                } else {
                    checkNetwork(errorCode, errorMessage);
                }
            }

            @Override
            public void onSuccessful(String body) {
                HandlerFuture.ofWork(body)
                        .applyThen(body1 -> {
                            return WebsiteManager.getInstance()
                                    .getWebsiteConfig()
                                    .getHtmlParser()
                                    .getThumbList(Jsoup.parse(body1));
                        })
                        .runOn(HandlerFuture.IO.UI)
                        .applyThen(thumbList -> {
                            refreshThumbList(thumbList);
                        });
            }
        }, Request.Priority.IMMEDIATE);
    }

    //Refresh the screen after a new search or pull-to-refresh finishes
    private void refreshThumbList(final List<ThumbBean> newList) {
        if (!mSwipeRefresh.isRefreshing()) {
            return;
        }

        mPostAdapter.setEmptyView(R.layout.layout_load_nothing, mRvPosts);
        if (mPostAdapter.refreshDatas(newList)) {
            scrollToTop();
        } else if (mPostAdapter.getData().isEmpty()) {
            loadNothing();
        }
        mSwipeRefresh.setRefreshing(false);
    }

    // Look up the romanized name on Baidu Baike using the Chinese name
    private void getNameFromBaidu(String searchTag) {
        String url = WebsiteConfig.BASE_URL_BAIDU + searchTag;
        connectBaidu(url);
    }

    private void connectBaidu(String url) {
        OkHttp.connect(url, TAG, new OkHttp.OkHttpCallback() {
            @Override
            public void onFailure(int errorCode, String errorMessage) {
                if (errorCode == 404) {
                    // Treat 404 as success so the UI shows no results rather than an access error
                    onSuccessful(errorMessage);
                } else {
                    checkNetwork(errorCode, errorMessage);
                }
            }

            @Override
            public void onSuccessful(String body) {
                String alternateUrl = HtmlParser.checkBaiduAlternateUrl(url, body);
                if (!TextUtils.isEmpty(alternateUrl)) {
                    connectBaidu(alternateUrl);
                } else {
                    String name = HtmlParser.getNameFromBaidu(body);
                    if (!TextUtils.isEmpty(name)) {
                        if (WebsiteManager.getInstance().getWebsiteConfig().isSupportAdvancedSearch()) {
                            String tag1 = "~" + name;
                            mCurrentTagList.add(tag1);

                            String[] split = name.split("_");
                            StringBuilder tag2 = new StringBuilder("~");
                            for (int i = split.length - 1; i >= 0; i--) {
                                tag2.append(split[i]).append("_");
                            }
                            tag2.replace(tag2.length() - 1, tag2.length(), "");
                            mCurrentTagList.add(tag2.toString());
                        } else {
                            mCurrentTagList.add(name);
                        }
                        getNewPosts(mCurrentPage);
                        changeFromPage(mCurrentPage);
                        changeToPage(mCurrentPage);
                    } else {
                        loadNothing();
                    }
                }
            }
        }, Request.Priority.IMMEDIATE);
    }

    @Override
    public void onWebsiteChanged(String baseUrl) {
        if (mBatchDownloadController != null) mBatchDownloadController.cancel();
        mToolbar.setNavigationIcon(WebsiteManager.getInstance().getWebsiteConfig().getWebsiteLogoRes());
        resetAll(1);
        getNewPosts(mCurrentPage);
        changeFromPage(mCurrentPage);
        changeToPage(mCurrentPage);
    }

    //Notification after receiving image details; obj is JSON (String)
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void setImageBean(MsgBean msgBean) {
        if (msgBean.msg.equals(Constants.GET_IMAGE_DETAIL)) {
            String json = (String) msgBean.obj;
            ImageBean imageBean = ImageBean.getImageDetailFromJson(json);
            List<ThumbBean> thumbList = mPostAdapter.getData();
            Map<String, String> headerMap = WebsiteManager.getInstance().getRequestHeaders();
            for (ThumbBean thumbBean : thumbList) {
                if (thumbBean.checkImageBelongs(imageBean)) {
                    if (thumbBean.imageBean == null) {
                        thumbBean.imageBean = imageBean;
                        thumbBean.checkToReplacePostData();
                        String url = imageBean.posts[0].getMinSizeImageUrl();
                        if (FileUtils.isImageType(url) && SystemUtils.isActivityActive(mActivity)) {
                            MyGlideModule.preloadImage(mActivity, url, headerMap);
                        }
                    }
                    break;
                }
            }
        }
    }

    //No results from Baidu or Konachan
    private void loadNothing() {
        mPostAdapter.setEmptyView(R.layout.layout_load_nothing, mRvPosts);
        mPostAdapter.setNewData(null);
        mSwipeRefresh.setRefreshing(false);
        SoundHelper.getInstance().playLoadNothingSound(getActivity());
    }

    //Network access failed
    private void checkNetwork() {
        checkNetwork(-1, null);
    }

    private void checkNetwork(int errorCode, String errorMessage) {
        mSwipeRefresh.setRefreshing(false);
        if (mPostAdapter.getData().isEmpty()) {
            mPostAdapter.setEmptyView(R.layout.layout_load_no_network, mRvPosts);
            // Provide a more specific message for the error code
            try {
                android.view.View emptyView = mPostAdapter.getEmptyView();
                if (emptyView != null) {
                    android.widget.TextView tv = emptyView.findViewById(R.id.tv_load_no_network_tip);
                    if (tv != null) {
                        if (errorCode == 403) {
                            tv.setText("The server denied access (403). Try another network or VPN endpoint.");
                        } else if (errorCode == 503) {
                            tv.setText("The server is temporarily unavailable (503). Pull to refresh later.");
                        } else if (errorCode == -1) {
                            tv.setText("Connection timed out. Check your network and pull to refresh.");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            SoundHelper.getInstance().playLoadNoNetworkSound(getActivity());
        } else {
            mPostAdapter.loadMoreFail();
        }
    }

    public static PostFragment newInstance() {
        PostFragment fragment = new PostFragment();
        return fragment;
    }


}
