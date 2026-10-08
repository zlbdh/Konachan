package com.ess.anime.wallpaper.global;

import com.ess.anime.wallpaper.MyApp;
import com.ess.anime.wallpaper.utils.StoragePaths;

public class Constants {

    // Sound flag
    public static boolean sRestart = true;
    public static boolean sAllowPlaySound;

    // Shared Preference
    public final static String BASE_URL = "baseUrl";
    public final static String SEARCH_MODE = "searchMode";
    public final static String GAME_COLUMN = "gameColumn";
    public final static String ALLOW_PLAY_SOUND = "allowPlaySound";
    public final static String ALREADY_ADD_USER = "alreadyAddUser";
    public final static String VIDEO_MUTE = "videoSilent";
    public final static String IS_POST_IMAGE_SHOWN_RECTANGULAR = "isPostImageShownRectangular";
    public final static String IMAGE_DETAIL_SWITCH_BUTTON_TOP_POSITION = "image_detail_switch_button_top_position";
    public final static String IMAGE_DETAIL_GESTURE_GUIDE_SHOWED = "image_detail_gesture_guide_showed";
    public final static String FAVORITE_TAG_SORT_BY = "favoriteTagSortBy";
    public final static String FAVORITE_TAG_SORT_ORDER = "favoriteTagSortOrder";
    public final static String SCREEN_ORIENTATION = "screenOrientation";
    public final static String PRELOAD_IMAGE_ONLY_WIFI = "preloadImageOnlyWifi";
    public final static String PIXIV_LOGIN_COOKIE = "pixiv_login_cookie";
    public final static String PIXIV_LOGIN_COOKIE_EXPIRED = "pixiv_login_cookie_expired";
    public final static String GELBOORU_API_KEY = "gelbooruApiKey";
    public final static String GELBOORU_USER_ID = "gelbooruUserId";
    public final static String AUTO_DOWNLOAD_UPDATE = "autoDownloadUpdate";
    public final static String RULE34_API_KEY = "rule34ApiKey";
    public final static String RULE34_USER_ID = "rule34UserId";
    public final static String DANBOORU_LOGIN = "danbooruLogin";
    public final static String DANBOORU_API_KEY = "danbooruApiKey";
    public final static String BATCH_DOWNLOAD_QUALITY = "batchDownloadQuality";

    // Intent
    public final static String APK_BEAN = "APK_BEAN";
    public final static String THUMB_BEAN = "THUMB_BEAN";
    public final static String IMAGE_BEAN = "IMAGE_BEAN";
    public final static String DOWNLOAD_BEAN = "DOWNLOAD_BEAN";
    public final static String LINK_TO_SHOW = "LINK_TO_SHOW";
    public final static String CURRENT_PAGE = "CURRENT_PAGE";
    public final static String CURRENT_FRAGMENT = "CURRENT_FRAGMENT";
    public final static String SEARCH_TAG = "SEARCH_TAG";
    public final static String ENLARGE = "ENLARGE";

    // Activity Result
    public final static int SEARCH_CODE = 1000;
    public final static int SEARCH_MODE_TAGS = 1001;
    public final static int SEARCH_MODE_ID = 1002;
    public final static int SEARCH_MODE_CHINESE = 1003;
    public final static int SEARCH_MODE_ADVANCED = 1004;
    public final static int SEARCH_MODE_HOME = 1005;
    public final static int SEARCH_MODE_RANDOM = 1006;
    public final static int FULLSCREEN_CODE = 2000;

    // EventBus
    public final static String CHECK_UPDATE = "checkUpdate";  // Notify MainActivity when a new version is detected
    public final static String GET_IMAGE_DETAIL = "getImageDetail";  // Notify details screens when image information arrives; PostFragment and PoolFragment update their adapters
    public final static String RELOAD_DETAIL_BY_ID = "reloadDetailById";  // PoolPostFragment requests tempPost again by ID after receiving imageBean
    public final static String LOCAL_FILES_CHANGED = "localFilesChanged";  // Notify FullscreenActivity to close when local favorite files change
    public final static String START_VIDEO = "startVideo";  // Notify MultipleMediaLayout to play video after FullscreenActivity changes pages
    public final static String RESUME_VIDEO = "resumeVideo";  // Resume MultipleMediaLayout video when ImageFragment or FullscreenActivity receives onResume()
    public final static String PAUSE_VIDEO = "pauseVideo";  // Pause MultipleMediaLayout video when ImageFragment or FullscreenActivity receives onPause()
    public final static String TOGGLE_VIDEO_CONTROLLER = "toggleVideoController";  // Toggle MultipleMediaLayout video controls when FullscreenActivity receives a single tap
    public final static String TOGGLE_SCREEN_ORIENTATION = "toggleScreenOrientation";  // Notify screens to rotate after the force-landscape setting changes

    // Image Detail
    public final static String RATING_S = "s";
    public final static String RATING_E = "e";
    public final static String RATING_Q = "q";

    // Glide
    public final static String IMAGE_DIR = StoragePaths.imageDirectory(MyApp.getInstance()).getAbsolutePath();
    public final static String IMAGE_TEMP = StoragePaths.temporaryDirectory(MyApp.getInstance()).getAbsolutePath();
    public final static String IMAGE_DONATE = StoragePaths.donationDirectory(MyApp.getInstance()).getAbsolutePath();
}
