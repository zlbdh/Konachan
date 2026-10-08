package com.ess.anime.wallpaper.ui.view;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ess.anime.wallpaper.R;
import com.ess.anime.wallpaper.adapter.RecyclerSingleChoiceAdapter;
import com.ess.anime.wallpaper.database.FavoriteTagBean;
import com.ess.anime.wallpaper.database.GreenDaoUtils;
import com.ess.anime.wallpaper.download.apk.ApkBean;
import com.ess.anime.wallpaper.download.apk.DownloadApkService;
import com.ess.anime.wallpaper.download.image.DownloadBean;
import com.ess.anime.wallpaper.global.Constants;
import com.ess.anime.wallpaper.model.helper.DocDataHelper;
import com.ess.anime.wallpaper.model.helper.TagOperationHelper;
import com.ess.anime.wallpaper.pixiv.login.PixivLoginManager;
import com.ess.anime.wallpaper.ui.activity.BaseActivity;
import com.ess.anime.wallpaper.ui.activity.SettingActivity;
import com.ess.anime.wallpaper.ui.activity.web.HyperlinkActivity;
import com.ess.anime.wallpaper.utils.FileUtils;
import com.ess.anime.wallpaper.utils.NetworkUtils;
import com.ess.anime.wallpaper.utils.SystemUtils;
import com.ess.anime.wallpaper.website.WebsiteConfig;
import com.ess.anime.wallpaper.website.WebsiteManager;
import com.qmuiteam.qmui.util.QMUIDeviceHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class CustomDialog extends MaterialDialog.Builder {

    public CustomDialog(@NonNull Context context) {
        super(context);
        this.titleColorRes(R.color.color_dialog_button)
                .contentColorRes(R.color.color_dialog_text)
                .positiveColorRes(R.color.color_dialog_button)
                .negativeColorRes(R.color.color_dialog_button)
                .neutralColorRes(R.color.color_dialog_button)
                .backgroundColorRes(R.color.color_dialog_bg)
                .itemsColorRes(R.color.color_dialog_text)
                .choiceWidgetColor(context.getResources().getColorStateList(R.color.dialog_widget_seletor));
    }

    /**
     * The image already exists; ask whether to download it again
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showPromptToReloadImage(Context context, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(R.string.dialog_reload_msg)
                .negativeText(R.string.dialog_reload_no)
                .positiveText(R.string.dialog_reload_yes)
                .onPositive((dialog1, which) -> listener.onPositive())
                .show();
    }

    /**
     * Delete favorite images
     *
     * @param context     Context
     * @param deleteCount Number of images to delete
     * @param listener    Event listener
     */
    public static void showDeleteCollectionDialog(Context context, int deleteCount, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(context.getString(R.string.dialog_delete_collection_msg, deleteCount))
                .negativeText(R.string.dialog_delete_cancel)
                .positiveText(R.string.dialog_delete_sure)
                .onPositive((dialog1, which) -> listener.onPositive())
                .show();
    }

    /**
     * Delete a download list item
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showDeleteWhenDownloadingItemDialog(Context context, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(R.string.dialog_delete_downloading_item_msg)
                .negativeText(R.string.dialog_delete_cancel)
                .positiveText(R.string.dialog_delete_sure)
                .onPositive((dialog1, which) -> listener.onPositive())
                .show();
    }

    /**
     * Clear all completed downloads
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showClearAllDownloadFinishedDialog(Context context, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(R.string.dialog_clear_all_download_finished_item_msg)
                .negativeText(R.string.dialog_clear_all_cancel)
                .positiveText(R.string.dialog_clear_all_sure)
                .onPositive((dialog1, which) -> listener.onPositive())
                .show();
    }

    /**
     * Clear all search history
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showClearAllSearchHistoryDialog(Context context, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(R.string.dialog_clear_all_search_history_msg)
                .negativeText(R.string.dialog_clear_all_cancel)
                .positiveText(R.string.dialog_clear_all_sure)
                .onPositive((dialog1, which) -> listener.onPositive())
                .show();
    }

    /**
     * Show or edit a tag note
     *
     * @param context  Context
     * @param tag      Tag
     * @param listener Event listener
     */
    public static void showEditTagAnnotationDialog(Context context, String tag, boolean edit, OnDialogActionListener listener) {
        TagAnnotationEditLayout editLayout = (TagAnnotationEditLayout) View.inflate(context, R.layout.layout_dialog_tag_annotation, null);
        MaterialDialog dialog = new CustomDialog(context)
                .title(tag)
                .customView(editLayout, false)
                .build();

        FavoriteTagBean tagBean = GreenDaoUtils.queryFavoriteTag(tag);
        editLayout.setAnnotation(tagBean.getAnnotation());
        editLayout.setOnEditModeChangeListener(isEditing -> {
            if (isEditing) {
                dialog.setCanceledOnTouchOutside(false);
                dialog.setActionButton(DialogAction.NEGATIVE, R.string.dialog_tag_annotation_cancel);
                dialog.getActionButton(DialogAction.NEGATIVE).setOnClickListener(v -> {
                    editLayout.setAnnotation(tagBean.getAnnotation());
                    editLayout.exitEditMode();
                });
                dialog.setActionButton(DialogAction.POSITIVE, R.string.dialog_tag_annotation_save);
                dialog.getActionButton(DialogAction.POSITIVE).setOnClickListener(v -> {
                    tagBean.setAnnotation(editLayout.getAnnotation());
                    GreenDaoUtils.updateFavoriteTag(tagBean);
                    if (listener != null) {
                        listener.onPositive();
                    }
                    dialog.dismiss();
                });
            } else {
                dialog.setCanceledOnTouchOutside(true);
                dialog.setActionButton(DialogAction.NEGATIVE, R.string.dialog_tag_annotation_close);
                dialog.getActionButton(DialogAction.NEGATIVE).setOnClickListener(v -> dialog.dismiss());
                dialog.setActionButton(DialogAction.POSITIVE, R.string.dialog_tag_annotation_edit);
                dialog.getActionButton(DialogAction.POSITIVE).setOnClickListener(v -> editLayout.enterEditMode());
            }
        });
        if (edit) {
            editLayout.enterEditMode();
        } else {
            editLayout.exitEditMode();
        }

        dialog.setOnShowListener(dialog1 -> {
            editLayout.post(() -> {
                if (edit && dialog.isShowing()) {
                    editLayout.showSoftInput();
                }
            });
        });
        dialog.show();
    }

    /**
     * Sort favorite tags
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showSortFavoriteTagsDialog(Context context, OnDialogActionListener listener) {
        View view = View.inflate(context, R.layout.layout_dialog_favorite_tag_sort, null);

        // Sort By
        TagOperationHelper.FavoriteTagSortBy tagSortBy = TagOperationHelper.getFavoriteTagSortBy();
        RecyclerView rvSortBy = view.findViewById(R.id.rv_sort_by);
        rvSortBy.setLayoutManager(new LinearLayoutManager(context));
        RecyclerSingleChoiceAdapter<TagOperationHelper.FavoriteTagSortBy> adapterSortBy
                = new RecyclerSingleChoiceAdapter<>(Arrays.asList(TagOperationHelper.FavoriteTagSortBy.values()));
        adapterSortBy.setSelectPos(tagSortBy.ordinal(), false);
        adapterSortBy.bindToRecyclerView(rvSortBy);

        // Order
        TagOperationHelper.FavoriteTagSortOrder tagSortOrder = TagOperationHelper.getFavoriteTagSortOrder();
        RecyclerView rvSortOrder = view.findViewById(R.id.rv_sort_order);
        rvSortOrder.setLayoutManager(new LinearLayoutManager(context));
        RecyclerSingleChoiceAdapter<TagOperationHelper.FavoriteTagSortOrder> adapterSortOrder
                = new RecyclerSingleChoiceAdapter<>(Arrays.asList(TagOperationHelper.FavoriteTagSortOrder.values()));
        adapterSortOrder.setSelectPos(tagSortOrder.ordinal(), false);
        adapterSortOrder.bindToRecyclerView(rvSortOrder);

        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_favorite_tag_sort_title)
                .customView(view, false)
                .negativeText(R.string.dialog_favorite_tag_sort_cancel)
                .positiveText(R.string.dialog_favorite_tag_sort_sure)
                .onPositive((dialog1, which) -> {
                    TagOperationHelper.FavoriteTagSortBy sortBy = adapterSortBy.getSelectData();
                    TagOperationHelper.FavoriteTagSortOrder sortOrder = adapterSortOrder.getSelectData();
                    if (sortBy != null && sortOrder != null) {
                        TagOperationHelper.saveFavoriteTagSortParam(sortBy, sortOrder);
                    }
                    listener.onPositive();
                }).show();
    }

    /**
     * Delete favorite tags
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showDeleteFavoriteTagsDialog(Context context, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(context.getString(R.string.dialog_delete_favorite_tags_msg))
                .negativeText(R.string.dialog_delete_cancel)
                .positiveText(R.string.dialog_delete_sure)
                .onPositive((dialog1, which) -> listener.onPositive())
                .show();
    }

    /**
     * Show tag type documentation
     *
     * @param context Context
     */
    public static void showTagTypeHelpDialog(Context context) {
        View view = View.inflate(context, R.layout.layout_dialog_scroll_text, null);
        TextView tvContent = view.findViewById(R.id.tv_content);
        tvContent.setText(DocDataHelper.getTagTypeHelpDoc(context));

        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_doc_tag_type_title)
                .customView(view, false)
                .positiveText(R.string.dialog_doc_sure)
                .show();
    }

    /**
     * Show advanced search documentation
     *
     * @param context Context
     */
    public static void showAdvancedSearchHelpDialog(Context context) {
        View view = View.inflate(context, R.layout.layout_dialog_scroll_text, null);
        TextView tvContent = view.findViewById(R.id.tv_content);
        tvContent.setText(DocDataHelper.getAdvancedSearchDoc(context));

        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_doc_advanced_search_title)
                .customView(view, false)
                .positiveText(R.string.dialog_doc_sure)
                .show();
    }

    /**
     * Request permission
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showRequestPermissionDialog(Context context, String title, String msg, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .title(title)
                .content(msg)
                .negativeText(R.string.dialog_permission_rationale_deny)
                .positiveText(R.string.dialog_permission_rationale_grant)
                .canceledOnTouchOutside(false)
                .onNegative((dialog1, which) -> {
                    if (listener != null) {
                        listener.onNegative();
                    }
                })
                .onPositive((dialog12, which) -> {
                    if (listener != null) {
                        listener.onPositive();
                    }
                })
                .cancelListener(dialog13 -> {
                    if (listener != null) {
                        listener.onNegative();
                    }
                }).show();
    }

    /**
     * Switch sites
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showChangeBaseUrlDialog(Context context, OnDialogActionListener listener) {
        String baseUrl = WebsiteManager.getInstance().getWebsiteConfig().getBaseUrl();
        List<String> baseList = Arrays.asList(WebsiteConfig.BASE_URLS);
        int baseIndex = baseList.indexOf(baseUrl);

        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_change_base_url_title)
                .negativeText(R.string.dialog_change_base_url_cancel)
                .items(R.array.website_list_item)
                .itemsCallbackSingleChoice(baseIndex, (dialog1, itemView, which, text) -> {
                    if (which != baseIndex) {
                        WebsiteManager.getInstance().changeWebsite(baseList.get(which));
                    }
                    if (listener != null) {
                        listener.onPositive();
                    }
                    return true;
                })
                .alwaysCallSingleChoiceCallback()
                .show();
    }

    /**
     * Choose an image size to download
     *
     * @param context  Context
     * @param itemList Details for three image sizes
     * @param listener Event listener
     */
    public static void showChooseToDownloadDialog(Context context, List<DownloadBean> itemList, OnDialogActionListener listener) {
        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.save)
                .negativeText(R.string.dialog_download_cancel)
                .positiveText(R.string.dialog_download_sure)
                .items(itemList)
                .itemsCallbackMultiChoice(null, (dialog1, which, text) -> {
                    List<DownloadBean> chosenList = new ArrayList<>();
                    for (int index : which) {
                        chosenList.add(itemList.get(index));
                    }
                    listener.onDownloadChosen(chosenList);
                    return false;
                }).show();
    }

    /**
     * Update prompt
     *
     * @param context Context
     * @param apkBean New version information
     */
    public static void showUpdateDialog(Context context, ApkBean apkBean) {
        String updateContent = apkBean.updatedContentEn;
        MaterialDialog dialog = new CustomDialog(context)
                .title(context.getString(R.string.dialog_update_title))
                .titleGravity(GravityEnum.CENTER)
                .content(context.getString(R.string.dialog_update_msg, apkBean.versionName,
                        FileUtils.computeFileSize(apkBean.apkSize), updateContent))
                .canceledOnTouchOutside(false)
                .negativeText(R.string.dialog_update_ignore)
                .positiveText(R.string.dialog_update_update)
                .onPositive((dialog1, which) -> {
                    if (!com.ess.anime.wallpaper.download.apk.UpdateDownloadManager.start(context, apkBean, false)) {
                        android.widget.Toast.makeText(context, "An update is downloading or cannot start right now. Please try again later.",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    /**
     * Feedback instructions
     *
     * @param context Context
     */
    public static void showFeedbackDialog(Context context) {
        String email = "zlbdha@gmail.com";
        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_feedback_title)
                .content(context.getString(R.string.dialog_feedback_msg, email))
                .negativeText(R.string.dialog_feedback_cancel)
                .neutralText(R.string.dialog_feedback_neutral)
                .onNeutral((dialog12, which) -> {
                    String url = "https://github.com/zlbdh/Konachan/issues";
                    HyperlinkActivity.launch(context, url);
                })
                .positiveText(R.string.dialog_feedback_sure)
                .onPositive((dialog1, which) -> {
                    Uri uri = Uri.parse("mailto:" + email);
                    Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
                    intent.putExtra(Intent.EXTRA_SUBJECT, "["
                            + SystemUtils.getVersionName(context)
                            + "] Feedback - K Anime Wallpaper");
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.feedback_title)));
                }).show();
    }

    /**
     * Site feature documentation
     *
     * @param context Context
     * @param title   title
     * @param msgRes  msg
     */
    public static void showWebsiteHelpDialog(Context context, CharSequence title, String msgRes) {
        MaterialDialog dialog = new CustomDialog(context)
                .title(title)
                .content(msgRes)
                .positiveText(R.string.dialog_doc_sure)
                .show();
    }

    /**
     * Pixiv account login prompt
     *
     * @param context Context
     */
    public static void showPixivLoginStateDialog(Context context) {
        MaterialDialog dialog = new CustomDialog(context)
                .content(PixivLoginManager.getInstance().isCookieExpired()
                        ? R.string.dialog_pixiv_login_state_desc_login_expired
                        : R.string.dialog_pixiv_login_state_desc_already_logged)
                .neutralText(R.string.dialog_pixiv_login_state_btn_close)
                .negativeText(R.string.dialog_pixiv_login_state_btn_logout)
                .onNegative((dialog1, which) -> PixivLoginManager.getInstance().setCookie(null))
                .positiveText(R.string.dialog_pixiv_login_state_btn_relogin)
                .onPositive((dialog2, which) -> PixivLoginManager.getInstance().login(context))
                .show();
    }

    private final static String NOT_SHOW_WALLPAPER_PROMPT_AGAIN = "NOT_SHOW_WALLPAPER_PROMPT_AGAIN";

    /**
     * Warn that some devices cannot apply a custom lock-screen wallpaper
     *
     * @param context Context
     */
    public static void checkToShowCannotCustomLockscreenWallpaperDialog(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if ((!QMUIDeviceHelper.isMIUI() && !QMUIDeviceHelper.isHuawei())
                || preferences.getBoolean(NOT_SHOW_WALLPAPER_PROMPT_AGAIN, false)) {
            return;
        }

        MaterialDialog dialog = new CustomDialog(context)
                .content(R.string.dialog_cannot_wallpaper_lockscreen_msg)
                .positiveText(R.string.dialog_cannot_wallpaper_lockscreen_sure)
                .neutralText(R.string.dialog_cannot_wallpaper_lockscreen_neutral)
                .onNeutral((dialog1, which) -> {
                    preferences.edit().putBoolean(NOT_SHOW_WALLPAPER_PROMPT_AGAIN, true).apply();
                }).show();
    }

    private final static String NOT_SHOW_MOBILE_PRELOAD_PROMPT_AGAIN = "NOT_SHOW_MOBILE_PRELOAD_PROMPT_AGAIN";

    /**
     * Warn about mobile-data image preloading and offer the Wi-Fi-only setting
     *
     * @param context Context
     */
    public static void checkToShowPromptUseMobileNetworkPreloadImage(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean(NOT_SHOW_MOBILE_PRELOAD_PROMPT_AGAIN, false)) {
            return;
        }
        boolean isUseMobileNetwork = NetworkUtils.getNetworkType(context) == ConnectivityManager.TYPE_MOBILE;
        boolean preloadOnlyWifi = preferences.getBoolean(Constants.PRELOAD_IMAGE_ONLY_WIFI, false);
        if (!isUseMobileNetwork || preloadOnlyWifi) {
            return;
        }

        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_prompt_use_mobile_network_preload_image_title)
                .content(R.string.dialog_prompt_use_mobile_network_preload_image_msg)
                .canceledOnTouchOutside(false)
                .negativeText(R.string.dialog_prompt_use_mobile_network_preload_image_negative)
                .positiveText(R.string.dialog_prompt_use_mobile_network_preload_image_positive)
                .onPositive((dialog1, which) -> {
                    context.startActivity(new Intent(context, SettingActivity.class));
                })
                .neutralText(R.string.dialog_prompt_use_mobile_network_preload_image_neutral)
                .onNeutral((dialog2, which) -> {
                    preferences.edit().putBoolean(NOT_SHOW_MOBILE_PRELOAD_PROMPT_AGAIN, true).apply();
                }).show();
    }

    /**
     * Switch screen orientation mode
     *
     * @param context  Context
     * @param listener Event listener
     */
    public static void showChangeScreenOrientationDialog(Context context, OnDialogActionListener listener) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        List<Integer> supportList = Arrays.asList(BaseActivity.SUPPORT_SCREEN_ORIENTATIONS);
        int curOrientation = preferences.getInt(Constants.SCREEN_ORIENTATION, supportList.get(0));
        int curIndex = supportList.indexOf(curOrientation);

        MaterialDialog dialog = new CustomDialog(context)
                .title(R.string.dialog_change_screen_orientation_mode_title)
                .negativeText(R.string.dialog_change_screen_orientation_mode_cancel)
                .items(R.array.screen_orientation_list_item)
                .itemsCallbackSingleChoice(curIndex, (dialog1, itemView, which, text) -> {
                    if (which != curIndex) {
                        preferences.edit().putInt(Constants.SCREEN_ORIENTATION, supportList.get(which)).apply();
                    }
                    listener.onPositive();
                    return true;
                })
                .alwaysCallSingleChoiceCallback()
                .show();
    }

    public interface OnDialogActionListener {
        void onPositive();

        void onNegative();

        void onDownloadChosen(List<DownloadBean> chosenList);
    }

    public static class SimpleDialogActionListener implements OnDialogActionListener {

        @Override
        public void onPositive() {
        }

        @Override
        public void onNegative() {
        }

        @Override
        public void onDownloadChosen(List<DownloadBean> chosenList) {
        }
    }
}
