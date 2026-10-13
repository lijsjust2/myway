package com.my.way;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.baidu.mapapi.SDKInitializer;
import com.elvishew.xlog.XLog;
import com.my.way.utils.GoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FragmentSettings extends PreferenceFragmentCompat {

    // Set a non-empty decimal EditTextPreference
    private void setupDecimalEditTextPreference(EditTextPreference preference) {
        if (preference != null) {
            preference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) EditTextPreference::getText);
            preference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_CLASS_NUMBER);
                editText.setSelection(editText.length());
            });
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                if (newValue.toString().trim().isEmpty()) {
                    GoUtils.DisplayToast(this.getContext(), getResources().getString(R.string.app_error_input_null));
                    return false;
                }
                return true;
            });
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_main);

        ListPreference pfJoystick = findPreference("setting_joystick_type");
        if (pfJoystick != null) {
            // 使用自定义 SummaryProvider
            pfJoystick.setSummaryProvider((Preference.SummaryProvider<ListPreference>) preference -> Objects.requireNonNull(preference.getEntry()));
            pfJoystick.setOnPreferenceChangeListener((preference, newValue) -> !newValue.toString().trim().isEmpty());
        }

        EditTextPreference pfWalk = findPreference("setting_walk");
        setupDecimalEditTextPreference(pfWalk);

        EditTextPreference pfRun = findPreference("setting_run");
        setupDecimalEditTextPreference(pfRun);

        EditTextPreference pfBike = findPreference("setting_bike");
        setupDecimalEditTextPreference(pfBike);

        EditTextPreference pfAltitude = findPreference("setting_altitude");
        setupDecimalEditTextPreference(pfAltitude);

        EditTextPreference pfLatOffset = findPreference("setting_lat_max_offset");
        setupDecimalEditTextPreference(pfLatOffset);

        EditTextPreference pfLonOffset = findPreference("setting_lon_max_offset");
        setupDecimalEditTextPreference(pfLonOffset);

        SwitchPreferenceCompat pLog = findPreference("setting_log_off");
        if (pLog != null) {
            pLog.setOnPreferenceChangeListener((preference, newValue) -> {
                if(((SwitchPreferenceCompat) preference).isChecked() != (Boolean) newValue) {
                    XLog.d(preference.getKey() + newValue);

                    if (Boolean.parseBoolean(newValue.toString())) {
                        XLog.d("on");
                    } else {
                        XLog.d("off");
                    }
                    return true;
                } else {
                    return false;
                }
            });
        }

        EditTextPreference pfPosHisValid = findPreference("setting_history_expiration");
        setupDecimalEditTextPreference(pfPosHisValid);

        // 设置版本号
        String verName;
        verName = GoUtils.getVersionName(FragmentSettings.this.getContext());
        Preference pfVersion = findPreference("setting_version");
        if (pfVersion != null) {
            pfVersion.setSummary(verName);
        }

        // 权限状态总览：展示关键权限是否已授予，便于排查保活/模拟失败原因
        updatePermissionStatus();

        // 增强与保活：忽略电池优化 / 自启白名单引导（厂商深链跳转，失败回退对话框）
        Preference pfKeepAlive = findPreference("setting_keep_alive");
        if (pfKeepAlive != null) {
            pfKeepAlive.setOnPreferenceClickListener(preference -> {
                requestIgnoreBatteryOptimizations();
                openManufacturerKeepAlive();
                return true;
            });
        }

        // Fused 绕过（尽力而为）：引导关闭 Google 位置精确度
        Preference pfFused = findPreference("setting_fused_tip");
        if (pfFused != null) {
            pfFused.setOnPreferenceClickListener(preference -> {
                openLocationSettings();
                showFusedDialog();
                return true;
            });
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            XLog.e("KEEP_ALIVE: open battery settings failed", e);
        }
    }

    /**
     * 按厂商尝试深链跳转到「自启动 / 省电白名单」设置页（参考 smsforwarder 的 PermissionUtils）。
     * 找不到对应页面时回退到通用说明对话框。
     */
    private void openManufacturerKeepAlive() {
        Context context = requireContext();
        String pkg = context.getPackageName();
        String manufacturer = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = Build.BRAND.toLowerCase(Locale.ROOT);

        List<Intent> candidates = new ArrayList<>();
        if (manufacturer.contains("xiaomi") || brand.contains("xiaomi")
                || manufacturer.contains("redmi") || brand.contains("redmi")
                || manufacturer.contains("po") || brand.contains("po")) {
            // 小米 / Redmi / POCO（MIUI / HyperOS）
            candidates.add(new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autosettings.AutoSettingsActivity")).putExtra("packageName", pkg));
            candidates.add(new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).putExtra("packageName", pkg));
        } else if (manufacturer.contains("huawei") || brand.contains("huawei")
                || manufacturer.contains("honor") || brand.contains("honor")) {
            candidates.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity")));
            candidates.add(new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalActivity")));
        } else if (manufacturer.contains("oppo") || brand.contains("oppo")
                || manufacturer.contains("realme") || brand.contains("realme")
                || manufacturer.contains("oneplus") || brand.contains("oneplus")) {
            candidates.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupCheckActivity")));
            candidates.add(new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.PermissionTopActivity")));
        } else if (manufacturer.contains("vivo") || brand.contains("vivo")
                || manufacturer.contains("iqoo") || brand.contains("iqoo")) {
            candidates.add(new Intent().setComponent(new ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.MainActivity")));
            candidates.add(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionActivity")));
        } else if (manufacturer.contains("samsung") || brand.contains("samsung")) {
            candidates.add(new Intent().setComponent(new ComponentName("com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity")));
        }

        for (Intent intent : candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (canResolveActivity(intent)) {
                try {
                    startActivity(intent);
                    return;
                } catch (Exception e) {
                    XLog.e("KEEP_ALIVE: open manufacturer settings failed", e);
                }
            }
        }
        // 没有匹配到厂商或跳转失败，回退到通用说明对话框
        showKeepAliveDialog();
    }

    private boolean canResolveActivity(Intent intent) {
        return requireContext().getPackageManager()
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    private void updatePermissionStatus() {
        Context context = requireContext();
        String pkg = context.getPackageName();

        boolean mock = GoUtils.isAllowMockLocation(context);
        boolean overlay = Settings.canDrawOverlays(context);
        boolean battery = false;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            battery = pm.isIgnoringBatteryOptimizations(pkg);
        }
        boolean notification = NotificationManagerCompat.from(context).areNotificationsEnabled();

        setPermSummary("perm_mock", mock);
        setPermSummary("perm_overlay", overlay);
        setPermSummary("perm_battery", battery);
        setPermSummary("perm_notification", notification);
    }

    private void setPermSummary(String key, boolean granted) {
        Preference p = findPreference(key);
        if (p != null) {
            p.setSummary(granted ? getString(R.string.status_granted) : getString(R.string.status_denied));
        }
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception e) {
            XLog.e("FUSED: open location settings failed", e);
        }
    }

    private void showKeepAliveDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_keep_alive_title)
                .setMessage(R.string.dialog_keep_alive_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showFusedDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_fused_title)
                .setMessage(R.string.dialog_fused_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}