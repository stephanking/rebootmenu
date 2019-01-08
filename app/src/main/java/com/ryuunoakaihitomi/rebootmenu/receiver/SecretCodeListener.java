package com.ryuunoakaihitomi.rebootmenu.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ryuunoakaihitomi.rebootmenu.R;
import com.ryuunoakaihitomi.rebootmenu.util.DebugLog;
import com.ryuunoakaihitomi.rebootmenu.util.ShellUtils;
import com.ryuunoakaihitomi.rebootmenu.util.hook.RMPowerActionManager;
import com.ryuunoakaihitomi.rebootmenu.util.hook.SuJavaPlugin;
import com.ryuunoakaihitomi.rebootmenu.util.hook.XposedUtils;
import com.ryuunoakaihitomi.rebootmenu.util.ui.TextToast;

import java.util.Objects;

/**
 * 神秘代码调用试验性功能，不开放在帮助文档
 * <p>
 * Created by ZQY on 2018/12/24.
 */

public class SecretCodeListener extends BroadcastReceiver {
    private static final String TAG = "SecretCodeListener";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SECRET_CODE".equals(intent.getAction())) {
            new DebugLog(TAG, "code:" + Objects.requireNonNull(intent.getData()).getHost(), null);
            new TextToast(context, true, context.getString(R.string.hidden_function_description));
            if (XposedUtils.isActive)
                RMPowerActionManager.getInstance().safeMode();
            else
                ShellUtils.runSuJavaWithAppProcess(context, SuJavaPlugin.class, SuJavaPlugin.ARG_SHUT_DOWN_DIALOG);
        }
    }
}
