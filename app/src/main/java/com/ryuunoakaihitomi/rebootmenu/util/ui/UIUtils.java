package com.ryuunoakaihitomi.rebootmenu.util.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Magnifier;
import android.widget.TextView;

import com.ryuunoakaihitomi.rebootmenu.BuildConfig;
import com.ryuunoakaihitomi.rebootmenu.R;
import com.ryuunoakaihitomi.rebootmenu.activity.Shortcut;
import com.ryuunoakaihitomi.rebootmenu.activity.base.MyActivity;
import com.ryuunoakaihitomi.rebootmenu.csc_compat.CoolapkCompat;
import com.ryuunoakaihitomi.rebootmenu.service.ReleaseDownloadService;
import com.ryuunoakaihitomi.rebootmenu.util.ConfigManager;
import com.ryuunoakaihitomi.rebootmenu.util.DebugLog;
import com.ryuunoakaihitomi.rebootmenu.util.NetUtils;
import com.ryuunoakaihitomi.rebootmenu.util.SpecialSupport;
import com.ryuunoakaihitomi.rebootmenu.util.hook.ReflectionOnPie;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import androidx.annotation.DrawableRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.FileProvider;

/**
 * 本应用关于界面操作的工具集合
 * Created by ZQY on 2018/2/10.
 *
 * @author ZQY
 */

public class UIUtils {
    private static final String TAG = "UIUtils";

    /**
     * 加载特定主题颜色的AlertDialog
     *
     * @param isWhite      是否白色主题
     * @param activityThis 当前activity的上下文
     * @return 已处理Builder对象
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    public static AlertDialog.Builder LoadDialog(boolean isWhite, Activity activityThis) {
        //在API级别23中，AlertDialog的主题定义被废弃。用在API级别22中新引入的Android默认主题格式代替。
        boolean isAndroidMPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;
        int themeCode;
        if (isWhite) {
            if (isAndroidMPlus)
                themeCode = android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
            else
                themeCode = AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
        } else {
            if (isAndroidMPlus)
                themeCode = android.R.style.Theme_DeviceDefault_Dialog_Alert;
            else
                themeCode = AlertDialog.THEME_DEVICE_DEFAULT_DARK;
        }
        new DebugLog("LoadDialog: themeCode=" + themeCode, DebugLog.LogLevel.V);
        return new AlertDialog.Builder(activityThis, themeCode);
    }

    /**
     * 将窗体透明展示
     * 不建议在Low Ram设备启动此功能
     *
     * @param w 欲透明化的dialog
     * @param f 透明度
     * @throws NullPointerException null.XXX();
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    public static void alphaShow(@NonNull AlertDialog w, @FloatRange(from = 0, to = 1) Float f) {
        Window window = w.getWindow();
        assert window != null;
        //使用反射来取系统属性（但在Android P(ill)上行不通）
        /*因为RoSystemProperties的缓存，
        在部分情况下（用magisk附带的resetprop设置键值，在热重启前）
        会出现SystemProperties返回值
        和isLowRamDevice()不同的情况，所以保留此法
         */
        boolean isLowRam = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1)
            /*
            警告：经查询
            https://android.googlesource.com/platform/prebuilts/runtime/+/master/appcompat/hiddenapi-dark-greylist.txt
            发现以下方法被明确添加进深灰名单！
            目前(2018.06.11 16:03)
            50263行 Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;
             */
            try {
                @SuppressLint("PrivateApi") Class<?> clazz = Class.forName("android.os.SystemProperties");
                Method method = clazz.getMethod("get", String.class);
                isLowRam = "true".equals(method.invoke(null, "ro.config.low_ram"));
            } catch (Exception e) {
                new DebugLog(e, "alphaShow", true);
            }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            //noinspection ConstantConditions
            isLowRam = ((ActivityManager) w.getContext().getSystemService(Context.ACTIVITY_SERVICE)).isLowRamDevice();
        new DebugLog("alphaShow: isLowRam=" + isLowRam, DebugLog.LogLevel.I);
        if (!isLowRam) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.alpha = f;
            window.setAttributes(lp);
        }
        w.show();
    }

    //显示帮助对话框
    private static void helpDialog(@NonNull final Activity activityThis, boolean cancelable, boolean isWhite) {
        new DebugLog("helpDialog", DebugLog.LogLevel.V);
        new TextToast(activityThis, String.format(activityThis.getString(R.string.help_notice), BuildConfig.VERSION_NAME, activityThis.getString(R.string.help_update_date)));
        AlertDialog.Builder h = LoadDialog(isWhite, activityThis);
        h.setTitle(activityThis.getString(R.string.help));
        String help = inputStream2String(activityThis.getResources().openRawResource(R.raw.help_body), null);
        h.setMessage(Html.fromHtml(help));
        h.setOnCancelListener(p1 -> {
            MyActivity.helpDialogReference = null;
            restartApp(activityThis);
        });
        h.setNeutralButton(activityThis.getString(R.string.offical_download_link), (dialogInterface, i) -> {
            if (CoolapkCompat.openCoolapk(activityThis)) {
                activityThis.finish();
                return;
            }
            if (SpecialSupport.isAndroidWearOS(activityThis))
                openURL(activityThis, NetUtils.GITHUB_RELEASE_WEB_LINK);
            else {
                if (activityThis.checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    openURL(activityThis, NetUtils.GITHUB_RELEASE_WEB_LINK);
                else {
                    //启用下载服务
                    new TextToast(activityThis, true, activityThis.getString(R.string.auto_download_hint));
                    activityThis.startService(new Intent(activityThis, ReleaseDownloadService.class));
                    activityThis.finish();
                }
            }
        });
        h.setNegativeButton(activityThis.getString(R.string.source_code), (p1, p2) -> openURL(activityThis, NetUtils.GITHUB_LINK));
        //有意保留的bug:帮助对话框的退出方式与配置相反
        if (cancelable) {
            h.setPositiveButton(activityThis.getString(R.string.exit), (dialogInterface, i) -> restartApp(activityThis));
            h.setCancelable(false);
        }
        AlertDialog hc = h.create();
        MyActivity.helpDialogReference = hc;
        alphaShow(hc, TransparentLevel.HELP);
        //不这么自作聪明的下载方式因为兼容性高当作备用
        hc.getButton(DialogInterface.BUTTON_NEUTRAL).setOnLongClickListener(view -> {
            openURL(activityThis, NetUtils.GITHUB_RELEASE_WEB_LINK);
            return true;
        });
        //通过反射取得AlertDialog的窗体对象
        /*
        Android P不开始允许反射AlertController
        日志：
        Accessing hidden field Landroid/app/AlertDialog;->mAlert:Lcom/android/internal/app/AlertController; (light greylist, reflection)
        Accessing hidden field Lcom/android/internal/app/AlertController;->mMessageView:Landroid/widget/TextView; (dark greylist, reflection)
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            ReflectionOnPie.clearClassLoaderInClass(UIUtils.class);
        try {
            @SuppressWarnings("JavaReflectionMemberAccess") Field mAlert = AlertDialog.class.getDeclaredField("mAlert");
            mAlert.setAccessible(true);
            Object mAlertController = mAlert.get(hc);
            Field mMessageView = mAlertController.getClass().getDeclaredField("mMessageView");
            mMessageView.setAccessible(true);
            TextView textView = (TextView) mMessageView.get(mAlertController);
            //修改文本颜色，因为我的诺基亚把默认文字颜色改成灰的了，看得不太清楚
            textView.setTextColor(ConfigManager.get(ConfigManager.WHITE_THEME) ?
                    activityThis.getResources().getColor(R.color.fujimurasaki) : activityThis.getResources().getColor(R.color.tohoh));
            //可选择文本
            textView.setTextIsSelectable(true);
        } catch (Exception e) {
            new DebugLog(e, "helpDialog", true);
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                ReflectionOnPie.restoreLoaderInClass(UIUtils.class);
        }
    }

    private UIUtils() {
    }

    /**
     * 使状态栏透明
     * 来自https://github.com/laobie/StatusBarUtil
     *
     * @param activity 要渲染的活动
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static void transparentStatusBar(@NonNull Activity activity) {
        new DebugLog("transparentStatusBar", DebugLog.LogLevel.V);
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    //通过配置文件选择退出方式和设置帮助按钮
    public static void setExitStyleAndHelp(@NonNull final Activity context, @NonNull final AlertDialog.Builder builder) {
        new DebugLog("setExitStyleAndHelp", DebugLog.LogLevel.V);
        //是否需要退出键
        if (!ConfigManager.get(ConfigManager.CANCELABLE))
            builder.setPositiveButton(R.string.exit, (dialogInterface, i) -> context.finish());
        //不按退出的退出监听
        builder.setCancelable(ConfigManager.get(ConfigManager.CANCELABLE));
        builder.setOnCancelListener(p1 -> {
            new TextToast(context.getApplicationContext(), false, context.getString(R.string.exit_notice));
            context.finish();
        });
        //帮助
        builder.setNegativeButton(R.string.help, (dialogInterface, i) ->
                UIUtils.helpDialog(context, ConfigManager.get(ConfigManager.CANCELABLE), ConfigManager.get(ConfigManager.WHITE_THEME)));
    }

    /**
     * 启动器添加快捷方式
     *
     * @param context     上下文
     * @param titleRes    标题资源id
     * @param iconRes     图标资源id
     * @param shortcutAct Shortcut额外
     * @param isForce     是否是root强制模式
     * @see com.ryuunoakaihitomi.rebootmenu.activity.Shortcut
     */
    @SuppressWarnings("ConstantConditions")
    public static void addLauncherShortcut(@NonNull Context context, @StringRes int titleRes, @DrawableRes int iconRes, int shortcutAct, boolean isForce) {
        new DebugLog("addLauncherShortcut", DebugLog.LogLevel.V);
        String forceToken = isForce ? "*" : "";
        String title = forceToken + context.getString(titleRes);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            context.sendBroadcast(new Intent("com.android.launcher.action.INSTALL_SHORTCUT")
                    .putExtra("duplicate", false)
                    .putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
                    .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, iconRes))
                    .putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(context, Shortcut.class)
                            .putExtra(Shortcut.extraTag, shortcutAct)));
        else {
            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(context, "o_launcher_shortcut:" + shortcutAct)
                    .setShortLabel(title)
                    .setIcon(Icon.createWithResource(context, iconRes))
                    .setIntent(new Intent(context, Shortcut.class)
                            .putExtra(Shortcut.extraTag, shortcutAct)
                            .setAction(Intent.ACTION_VIEW))
                    .build();
            new DebugLog("addLauncherShortcut: requestPinShortcut:"
                    + context.getSystemService(ShortcutManager.class).requestPinShortcut(shortcutInfo, null));
        }
    }

    /**
     * 将输入流转为字符串
     *
     * @param in     待转换的输入流
     * @param encode 字符编码
     * @return 转换后的字符串
     */
    private static String inputStream2String(InputStream in, @SuppressWarnings("SameParameterValue") String encode) {
        new DebugLog("inputStream2String", DebugLog.LogLevel.V);
        String str = "";
        try {
            if (encode == null || encode.equals(""))
                encode = "utf-8";
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, encode));
            StringBuilder sb = new StringBuilder();
            while ((str = reader.readLine()) != null)
                sb.append(str).append("\n");
            return sb.toString();
        } catch (Exception ignored) {
        }
        return str;
    }

    /**
     * 添加放大镜🔍
     * 参照https://developer.android.google.cn/guide/topics/text/magnifier#java的Magnify on user interaction章节
     * {@link android.widget.Magnifier}
     *
     * @param baseView 基底View
     */
    @SuppressLint("ClickableViewAccessibility")
    @TargetApi(Build.VERSION_CODES.P)
    public static void addMagnifier(@NonNull View baseView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                //可穿戴设备屏幕太小，选项会和放大镜一起滚动，很碍眼
                && !SpecialSupport.isAndroidWearOS(baseView.getContext())) {
            Magnifier magnifier = new Magnifier(baseView);
            baseView.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE: {
                        final int[] viewPosition = new int[2];
                        v.getLocationOnScreen(viewPosition);
                        magnifier.show(event.getRawX() - viewPosition[0],
                                event.getRawY() - viewPosition[1]);
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        magnifier.dismiss();
                }
                //不覆盖其他交互操作
                return false;
            });
        }
    }

    //尝试打开URL
    private static void openURL(@NonNull Context context, String link) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        } catch (ActivityNotFoundException e) {
            new TextToast(context, true, link + "\n" + context.getString(R.string.url_open_failed_notice));
        } catch (SecurityException e) {
            e.printStackTrace();
            if (SpecialSupport.isAndroidWearOS(context)) {
                // 很有趣的一点就是，zxing的BarcodeScanner应用在Wear OS直接打开是会崩溃的，
                // 但是调用生成二维码这个是没问题的。而且大小位置正好能够让手机扫描。
                boolean isZxAppLaunchSucceeded = SpecialSupport.showQRCodeWithZxingApp(context, link, false);
                new TextToast(context, !isZxAppLaunchSucceeded, isZxAppLaunchSucceeded
                        ? context.getString(R.string.android_wear_show_qr_code_zxing_tips)
                        : context.getString(R.string.android_waer_cannot_open_url, link));
            }
        } finally {
            if (context instanceof Activity) ((Activity) context).finish();
        }
    }

    /**
     * 打开文件（字面意思）
     *
     * @param context  {@link Context}
     * @param filePath 文件完整路径
     * @return 是否成功打开
     */
    public static boolean openFile(Context context, String filePath) {
        File f = new File(filePath);
        Uri uri = Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                ? Uri.fromFile(f)
                : FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".file_provider", f);
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setDataAndType(uri, getMimeTypeFromUrl(filePath)));
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        }
    }

    /**
     * 从url中取mime type
     *
     * @param url url
     * @return mime type
     */
    private static String getMimeTypeFromUrl(String url) {
        String ret = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url));
        DebugLog.i(TAG, "getMimeTypeFromUrl: " + url + " -> " + ret);
        return ret;
    }

    /**
     * 重启应用
     *
     * @param activity {@link Activity#startActivity(Intent)}
     */
    public static void restartApp(@NonNull Activity activity) {
        //noinspection ConstantConditions
        activity.startActivity(activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        activity.finish();
    }

    //半透明级别(alphaShow参数)
    public static class TransparentLevel {
        public static final float NORMAL = 0.75f;
        public static final float CONFIRM = 0.9f;
        public static final float PREFERENCES = 0.6f;
        static final float HELP = 0.8f;

        private TransparentLevel() {
        }
    }
}
