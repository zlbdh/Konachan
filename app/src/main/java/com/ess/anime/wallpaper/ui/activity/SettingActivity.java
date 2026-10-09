package com.ess.anime.wallpaper.ui.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.adapter.RecyclerCommonSettingAdapter;
import com.ess.anime.wallpaper.bean.MsgBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.http.FireBase;
import com.ess.anime.wallpaper.http.UpdateCheckController;
import com.ess.anime.wallpaper.model.entity.CommonSettingItem;
import com.ess.anime.wallpaper.model.helper.SoundHelper;
import com.ess.anime.wallpaper.ui.view.CustomDialog;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.ess.anime.wallpaper.utils.SystemUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SettingActivity extends BaseActivity {

    private SharedPreferences mPreferences;
    private RecyclerCommonSettingAdapter mSettingAdapter;
    private boolean mCheckingUpdate;

    private long mCacheSize;
    private CommonSettingItem mClearCacheItem;
    private CommonSettingItem mScreenOrientationItem;
    private ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected int layoutRes() {
        return R.layout.activity_setting;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        initToolBarLayout();
        initRecyclerSetting();
        resetData();
        updateScreenOrientationItemUI();
    }

    private void initToolBarLayout() {
        Toolbar toolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initRecyclerSetting() {
        RecyclerView rvSetting = findViewById(R.id.rv_setting);
        rvSetting.setLayoutManager(new LinearLayoutManager(this));
        mSettingAdapter = new RecyclerCommonSettingAdapter();
        mSettingAdapter.bindToRecyclerView(rvSetting);
    }

    private void resetData() {
        mSettingAdapter.setNewData(getSettingItems());
        getCacheSize();
    }

    private void updateScreenOrientationItemUI() {
        if (mScreenOrientationItem != null) {
            List<Integer> supportList = Arrays.asList(BaseActivity.SUPPORT_SCREEN_ORIENTATIONS);
            int curOrientation = mPreferences.getInt(Constants.SCREEN_ORIENTATION, supportList.get(0));
            int curIndex = supportList.indexOf(curOrientation);
            String[] titleArray = getResources().getStringArray(R.array.screen_orientation_list_item);
            if (curIndex >= 0 & curIndex < titleArray.length) {
                mScreenOrientationItem.setTips(titleArray[curIndex]);
                mSettingAdapter.notifyDataSetChanged(true);
            }
        }
    }

    private void updateCacheItemUI() {
        if (SystemUtils.isActivityActive(this)) {
            if (mClearCacheItem != null) {
                mClearCacheItem.setTips(FileUtils.computeFileSize(mCacheSize));
            }
            mSettingAdapter.notifyDataSetChanged(true);
            hideLoadingSafely();
        }
    }

    private List<CommonSettingItem> getSettingItems() {
        List<CommonSettingItem> items = new ArrayList<>();
        items.add(getLanguageItem());
        items.add(getAllowPlaySoundItem());
        items.add(getPreloadImageOnlyWifiItem());
        items.add(getHelpTagTypeItem());
        items.add(getHelpAdvancedSearchItem());
        items.add(mScreenOrientationItem = getScreenOrientationItem());
        items.add(mClearCacheItem = getClearCacheItem());
        items.add(getGelbooruApiKeyItem());
        items.add(getRule34ApiKeyItem());
        items.add(getDanbooruApiKeyItem());
        items.add(getEHentaiLoginItem());
        items.add(getBatchDownloadQualityItem());
        items.add(getAutoDownloadUpdateItem());
        items.add(getCheckUpdateItem());
        return items;
    }

    private CommonSettingItem getGelbooruApiKeyItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_gelbooru_api_key_title)
                .setDesc(R.string.setting_gelbooru_api_key_desc)
                .setOnClickListener(v -> showGelbooruApiKeyDialog());
    }

    private CommonSettingItem getRule34ApiKeyItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_rule34_api_key_title)
                .setDesc(R.string.setting_rule34_api_key_desc)
                .setOnClickListener(v -> showRule34ApiKeyDialog());
    }

    private CommonSettingItem getDanbooruApiKeyItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_danbooru_api_key_title)
                .setDesc(R.string.setting_danbooru_api_key_desc)
                .setOnClickListener(v -> showDanbooruApiKeyDialog());
    }

    private CommonSettingItem getEHentaiLoginItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_ehentai_login_title)
                .setDesc(R.string.setting_ehentai_login_desc)
                .setOnClickListener(v -> EHentaiLoginActivity.launch(this));
    }

    private void showDanbooruApiKeyDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        android.widget.EditText etLogin = new android.widget.EditText(this);
        etLogin.setHint(R.string.setting_danbooru_login_hint);
        etLogin.setText(mPreferences.getString(Constants.DANBOORU_LOGIN, ""));
        etLogin.setSingleLine(true);
        layout.addView(etLogin);

        android.widget.EditText etApiKey = new android.widget.EditText(this);
        etApiKey.setHint(R.string.setting_danbooru_api_key_hint);
        etApiKey.setText(mPreferences.getString(Constants.DANBOORU_API_KEY, ""));
        etApiKey.setSingleLine(true);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = padding / 2;
        layout.addView(etApiKey, lp);

        new CustomDialog(this)
                .title(R.string.setting_danbooru_api_key_title)
                .customView(layout, false)
                .positiveText(R.string.setting_gelbooru_api_key_save)
                .negativeText(R.string.dialog_download_cancel)
                .onPositive((dialog, which) -> {
                    String login = etLogin.getText().toString().trim();
                    String apiKey = etApiKey.getText().toString().trim();
                    mPreferences.edit()
                            .putString(Constants.DANBOORU_LOGIN, login)
                            .putString(Constants.DANBOORU_API_KEY, apiKey)
                            .apply();
                    Toast.makeText(this, R.string.setting_danbooru_api_key_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showRule34ApiKeyDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        android.widget.EditText etApiKey = new android.widget.EditText(this);
        etApiKey.setHint(R.string.setting_rule34_api_key_hint);
        etApiKey.setText(mPreferences.getString(Constants.RULE34_API_KEY, ""));
        etApiKey.setSingleLine(true);
        layout.addView(etApiKey);

        android.widget.EditText etUserId = new android.widget.EditText(this);
        etUserId.setHint(R.string.setting_rule34_user_id_hint);
        etUserId.setText(mPreferences.getString(Constants.RULE34_USER_ID, ""));
        etUserId.setSingleLine(true);
        etUserId.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = padding / 2;
        layout.addView(etUserId, lp);

        new CustomDialog(this)
                .title(R.string.setting_rule34_api_key_title)
                .customView(layout, false)
                .positiveText(R.string.setting_gelbooru_api_key_save)
                .negativeText(R.string.dialog_download_cancel)
                .onPositive((dialog, which) -> {
                    String apiKey = etApiKey.getText().toString().trim();
                    String userId = etUserId.getText().toString().trim();
                    mPreferences.edit()
                            .putString(Constants.RULE34_API_KEY, apiKey)
                            .putString(Constants.RULE34_USER_ID, userId)
                            .apply();
                    Toast.makeText(this, R.string.setting_rule34_api_key_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showGelbooruApiKeyDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, padding / 2);

        android.widget.EditText etApiKey = new android.widget.EditText(this);
        etApiKey.setHint(R.string.setting_gelbooru_api_key_hint);
        etApiKey.setText(mPreferences.getString(Constants.GELBOORU_API_KEY, ""));
        etApiKey.setSingleLine(true);
        layout.addView(etApiKey);

        android.widget.EditText etUserId = new android.widget.EditText(this);
        etUserId.setHint(R.string.setting_gelbooru_user_id_hint);
        etUserId.setText(mPreferences.getString(Constants.GELBOORU_USER_ID, ""));
        etUserId.setSingleLine(true);
        etUserId.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = padding / 2;
        layout.addView(etUserId, lp);

        new CustomDialog(this)
                .title(R.string.setting_gelbooru_api_key_title)
                .customView(layout, false)
                .positiveText(R.string.setting_gelbooru_api_key_save)
                .negativeText(R.string.dialog_download_cancel)
                .onPositive((dialog, which) -> {
                    String apiKey = etApiKey.getText().toString().trim();
                    String userId = etUserId.getText().toString().trim();
                    mPreferences.edit()
                            .putString(Constants.GELBOORU_API_KEY, apiKey)
                            .putString(Constants.GELBOORU_USER_ID, userId)
                            .apply();
                    Toast.makeText(this, R.string.setting_gelbooru_api_key_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private CommonSettingItem getAllowPlaySoundItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_allow_play_sound)
                .setRippleDelayClick(false)
                .setCheckboxShown(true)
                .setCheckboxChecked(Constants.sAllowPlaySound)
                .setOnCheckedChangeListener((smoothCompoundButton, isChecked) -> {
                    Constants.sAllowPlaySound = isChecked;
                    mPreferences.edit().putBoolean(Constants.ALLOW_PLAY_SOUND, isChecked).apply();
                    if (isChecked) {
                        SoundHelper.getInstance().playSoundEnabled(SettingActivity.this);
                    } else {
                        SoundHelper.getInstance().playSoundDisabled();
                    }
                });
    }

    private CommonSettingItem getLanguageItem() {
        String lang = com.ess.anime.wallpaper.global.LocaleHelper.getSavedLanguage(this);
        String desc = com.ess.anime.wallpaper.global.LocaleHelper.getDisplayName(this, lang);
        return new CommonSettingItem()
                .setTitle(R.string.setting_language)
                .setDesc(desc)
                .setOnClickListener(v -> {
                    CustomDialog.showChangeLanguageDialog(this, new CustomDialog.SimpleDialogActionListener() {
                        @Override
                        public void onPositive() {
                            super.onPositive();
                            // Restart to apply new locale
                            android.widget.Toast.makeText(SettingActivity.this,
                                    R.string.language_changed, android.widget.Toast.LENGTH_SHORT).show();
                            new android.os.Handler().postDelayed(() -> {
                                android.content.Intent intent = getPackageManager()
                                        .getLaunchIntentForPackage(getPackageName());
                                if (intent != null) {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    Runtime.getRuntime().exit(0);
                                }
                            }, 800);
                        }
                    });
                });
    }

    private CommonSettingItem getScreenOrientationItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_screen_orientation_title)
                .setOnClickListener(v -> {
                    CustomDialog.showChangeScreenOrientationDialog(this, new CustomDialog.SimpleDialogActionListener() {
                        @Override
                        public void onPositive() {
                            super.onPositive();
                            updateScreenOrientationItemUI();
                            // Notify all screens
                            EventBus.getDefault().post(new MsgBean(Constants.TOGGLE_SCREEN_ORIENTATION, null));
                        }
                    });
                });
    }

    private CommonSettingItem getPreloadImageOnlyWifiItem() {
        boolean preloadOnlyWifi = mPreferences.getBoolean(Constants.PRELOAD_IMAGE_ONLY_WIFI, false);
        return new CommonSettingItem()
                .setTitle(R.string.setting_preload_image_only_wifi_title)
                .setDesc(R.string.setting_preload_image_only_wifi_desc)
                .setRippleDelayClick(false)
                .setCheckboxShown(true)
                .setCheckboxChecked(preloadOnlyWifi)
                .setOnCheckedChangeListener((smoothCompoundButton, isChecked) -> {
                    mPreferences.edit().putBoolean(Constants.PRELOAD_IMAGE_ONLY_WIFI, isChecked).apply();
                });
    }

    private CommonSettingItem getHelpTagTypeItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_help_tag_type)
                .setOnClickListener(v -> {
                    CustomDialog.showTagTypeHelpDialog(this);
                });
    }

    private CommonSettingItem getHelpAdvancedSearchItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_help_advanced_search)
                .setOnClickListener(v -> {
                    CustomDialog.showAdvancedSearchHelpDialog(this);
                });
    }

    private CommonSettingItem getClearCacheItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_clear_cache_title)
                .setDesc(R.string.setting_clear_cache_desc)
                .setOnClickListener(v -> {
                    if (SystemUtils.isActivityActive(this)) {
                        showLoadingSafely();
                        deleteCacheFile();
                    }
                });
    }

    private void deleteCacheFile() {
        if (!mExecutor.isTerminated() && !mExecutor.isShutdown()) {
            mExecutor.execute(() -> {
                for (File dir : getCacheDirs()) {
                    FileUtils.deleteFile(dir);
                }
                getCacheSize();
            });
        }
    }

    private void getCacheSize() {
        if (!mExecutor.isTerminated() && !mExecutor.isShutdown()) {
            mExecutor.execute(() -> {
                File[] cacheDirs = getCacheDirs();
                long cacheSize = 0;
                for (File dir : cacheDirs) {
                    cacheSize += FileUtils.getFileLength(dir);
                }
                long finalCacheSize = cacheSize;
                runOnUiThread(() -> {
                    mCacheSize = finalCacheSize;
                    updateCacheItemUI();
                });
            });
        }
    }

    private File[] getCacheDirs() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            return new File[]{getCacheDir(), getCodeCacheDir(), getExternalCacheDir()};
        } else {
            return new File[]{getCacheDir(), getExternalCacheDir()};
        }
    }

    private CommonSettingItem getBatchDownloadQualityItem() {
        return new CommonSettingItem()
                .setTitle(R.string.setting_batch_download_quality_title)
                .setDesc(getBatchDownloadQualityDesc())
                .setOnClickListener(v -> showBatchDownloadQualityDialog());
    }

    private String getBatchDownloadQualityDesc() {
        int q = mPreferences.getInt(Constants.BATCH_DOWNLOAD_QUALITY, -1);
        switch (q) {
            case 0: return getString(R.string.batch_download_sample);
            case 1: return getString(R.string.batch_download_large);
            case 2: return getString(R.string.batch_download_original);
            default: return getString(R.string.setting_batch_download_quality_ask);
        }
    }

    private void showBatchDownloadQualityDialog() {
        String[] items = {
                getString(R.string.setting_batch_download_quality_ask),
                getString(R.string.batch_download_sample),
                getString(R.string.batch_download_large),
                getString(R.string.batch_download_original)
        };
        int checked = mPreferences.getInt(Constants.BATCH_DOWNLOAD_QUALITY, -1) + 1;
        new CustomDialog(this)
                .title(R.string.setting_batch_download_quality_title)
                .items(items)
                .itemsCallbackSingleChoice(checked, (dialog, view, which, text) -> true)
                .positiveText(R.string.setting_gelbooru_api_key_save)
                .negativeText(R.string.dialog_download_cancel)
                .onPositive((dialog, which) -> {
                    int selected = dialog.getSelectedIndex() - 1;
                    mPreferences.edit().putInt(Constants.BATCH_DOWNLOAD_QUALITY, selected).apply();
                    recreate();
                })
                .show();
    }

    private CommonSettingItem getAutoDownloadUpdateItem() {
        boolean checked = mPreferences.getBoolean(Constants.AUTO_DOWNLOAD_UPDATE, false);
        return new CommonSettingItem()
                .setTitle(R.string.setting_auto_download_update_title)
                .setDesc(R.string.setting_auto_download_update_desc)
                .setRippleDelayClick(false)
                .setCheckboxShown(true)
                .setCheckboxChecked(checked)
                .setOnCheckedChangeListener((button, isChecked) -> {
                    mPreferences.edit().putBoolean(Constants.AUTO_DOWNLOAD_UPDATE, isChecked).apply();
                });
    }

    private CommonSettingItem getCheckUpdateItem() {
        String version = SystemUtils.getVersionName(this);
        return new CommonSettingItem()
                .setTitle(R.string.setting_check_update)
                .setTips(getString(R.string.setting_current_version, version))
                .setOnClickListener(v -> checkUpdateManually());
    }

    private void checkUpdateManually() {
        if (mCheckingUpdate) return;
        mCheckingUpdate = true;
        Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show();
        FireBase.getInstance().checkUpdate(result -> {
            mCheckingUpdate = false;
            if (!SystemUtils.isActivityActive(this)
                    || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
            if (result.status == UpdateCheckController.Status.AVAILABLE) {
                CustomDialog.showUpdateDialog(this, result.apk);
            } else if (result.status == UpdateCheckController.Status.LATEST) {
                showNoNewVersionToast();
            } else {
                if ("Check canceled".equals(result.message) || result.message.contains("superseded")) return;
                Toast.makeText(this, "Update check failed. Please try again later.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showNoNewVersionToast() {
        Toast.makeText(this, R.string.check_no_new_version, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }
}
