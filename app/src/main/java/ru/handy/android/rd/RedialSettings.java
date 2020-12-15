package ru.handy.android.rd;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Главный Activity по настройкам программы
 */
public class RedialSettings extends PreferenceActivity {
    /**
     * Determines whether to always show the simplified settings UI, where
     * settings are presented in a single list. When false, settings are shown
     * as a master/detail two-pane view on tablets. When true, a single pane is
     * shown on tablets.
     */
    private static final boolean ALWAYS_SIMPLE_PREFS = false;
    private static final String IS_FIRST_LOAD = "is_first_load"; // константа для получение из Preference информации, о том первый раз запущено приложение или нет
    protected static final String IS_PAID = "is_paid"; // константа для получение из Preference информации, оплячено приложение или нет
    protected static final String ATTEMPTS = "attempts"; // константа для получение из Preference, кол-ва повторений автодозвона
    protected static final int DEF_ATTEMPTS = 4; // кол-во повторений автодозвона по умолчанию
    protected static PreferenceActivity prefAct; // PreferenceActivity для статических методов
    protected static boolean isPaid = false; // переменная, показывающая, оплачено приложение или нет
    private static Pay pay = null; // класс для обработки платежей
    protected static int amountAttempts = 0; // переменная для сохраненеия кол-ва попыток при изменении в настройках
    private static int notConfirmedAmountAttempts = 0; // не подтвержденное до момента оплаты кол-во попыток
    private static RoleManager roleManager = null; // менеджер ролей - необходим для роли ROLE_CALL_REDIRECTION для CallRedirectionService (для SDK >= 29 (Android 10))
    private static String[] permissions = null; // перечень разрешений, необходимых для приложения

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // доступ к установленным настройкам приложения
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        isPaid = sharedPref.getBoolean(IS_PAID, false);
        // проверяем не оплачивалось уже приложение
        if (!isPaid) {
            pay = new Pay(this);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int amount = pay.amountOfPurchased();
                    for (int i = 0; i < 100; i++) {
                        if (amount != -1) {
                            Log.i("myLogs", "i = " + i + ", amountDonate = " + amount);
                            break;
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        amount = pay.amountOfPurchased();
                    }
                    if (amount > 0) {
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putBoolean(IS_PAID, true); // ставим статус оплаты
                        editor.apply();
                        isPaid = true;
                        pay.close();
                        pay = null;
                    }
                }
            }).start();
        }
        prefAct = this;
        //активируем roleManager для SDK >= 29 (Android 10)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            roleManager = (RoleManager) prefAct.getSystemService(Context.ROLE_SERVICE);
        }
        // установка перечня разрешений, необходимых для приложения
        List<String> permissionsList = new ArrayList<String>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            permissionsList.add(Manifest.permission.PROCESS_OUTGOING_CALLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            permissionsList.add(Manifest.permission.ANSWER_PHONE_CALLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            permissionsList.add(Manifest.permission.USE_FULL_SCREEN_INTENT);
        permissionsList.add(Manifest.permission.READ_CALL_LOG);
        permissionsList.add(Manifest.permission.CALL_PHONE);
        permissions = new String[permissionsList.size()];
        permissionsList.toArray(permissions);
        setupSimplePreferencesScreen();

        // если разрешение на звонки не установлено, то убираем галку в "enable_redial"
        findPreference("enable_redial").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            /**
             * Called when a Preference has been clicked.
             *
             * @param preference The Preference that was clicked.
             * @return True if the click was handled.
             */
            @Override
            public boolean onPreferenceClick(Preference preference) {
                boolean isChecked = PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                        .getBoolean(preference.getKey(), true);
                // проверка на наличие разрешения на запись в файл
                if (((Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.PROCESS_OUTGOING_CALLS) != PackageManager.PERMISSION_GRANTED)
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED)
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.USE_FULL_SCREEN_INTENT) != PackageManager.PERMISSION_GRANTED)
                        || ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)))
                        && isChecked) {
                    ((CheckBoxPreference) preference).setChecked(false);
                    Log.d("myLogs", "RedialSettings: onPreferenceClick");
                }
                return false;
            }
        });

        // в случае, если программа запускается первый раз, воводим информационное сообщение
        if (sharedPref.getBoolean(IS_FIRST_LOAD, true)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(s(R.string.attention, this))
                    .setMessage(s(R.string.attention_in_details, this))
                    .setIcon(R.mipmap.ic_launcher)
                    .setNegativeButton("ОК", null);
            builder.show();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putBoolean(IS_FIRST_LOAD, false); // отмечаем, что первый запуск уже прошел
            editor.apply();
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(final Preference preference, Object value) {
            String stringValue = value.toString();

            String key = preference.getKey();
            if (key.equals("enable_redial")) {
                if ((Boolean) value) {
                    // проверка на наличие разрешения на запись в файл
                    if (isGrantedPermission(permissions, s(R.string.attention, preference.getContext())
                            , s(R.string.permission_for_redial, preference.getContext()), 1002)
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || isGrantedRole(RoleManager.ROLE_CALL_REDIRECTION, 1003))) {
                        // start detect service
                        Intent intent = new Intent(preference.getContext(), CallDetectService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // это работает после 26 версии (8 Android)
                            preference.getContext().startForegroundService(intent);
                        } else {
                            preference.getContext().startService(intent);
                        }
                        preference.setSummary(R.string.service_on);
                        Log.d("myLogs", "RedialSettings: startService onChange");
                    } else {
                        ((CheckBoxPreference) preference).setChecked(false);
                        preference.setSummary(R.string.service_off);
                    }
                } else {
                    // stop detect service
                    Intent intent = new Intent(preference.getContext(), CallDetectService.class);
                    preference.getContext().stopService(intent);
                    preference.setSummary(R.string.service_off);
                    Log.d("myLogs", "RedialSettings: stopService onChange");
                }
            } else if (key.equals(ATTEMPTS)) {
                try {
                    notConfirmedAmountAttempts = Integer.parseInt(stringValue);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (notConfirmedAmountAttempts > DEF_ATTEMPTS && !isPaid) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(preference.getContext());
                    builder.setTitle(R.string.paid_service);
                    builder.setMessage(R.string.agree_to_pay);
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            int purchaseRes = pay.purchase(Pay.ITEM_SKU_99rub, 1001);
                            int i = 0;
                            while (purchaseRes != 0) {
                                i++;
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                purchaseRes = pay.purchase(Pay.ITEM_SKU_99rub, 1001);
                                if (i == 10) {
                                    if (purchaseRes == 7) {
                                        Toast.makeText(prefAct, R.string.aready_purchased, Toast.LENGTH_LONG).show();
                                    } else if (purchaseRes == -1) {
                                        Toast.makeText(prefAct, R.string.pay_service_disconnected, Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(prefAct, R.string.purchase_error, Toast.LENGTH_LONG).show();
                                    }
                                    break;
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(R.string.no, null);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return false;
                }
                amountAttempts = notConfirmedAmountAttempts;
                preference.setSummary(amountAttempts + "");

            } else if (key.equals("dynamic")) {

            } else if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        if (preference instanceof CheckBoxPreference) {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getBoolean(preference.getKey(), true));
        } else {
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        }
    }

    private static String s(int res, Context ctx) {
        return ctx.getResources().getString(res);
    }

    /**
     * проверка на наличие разрешения на определенное действие
     *
     * @param permissions    - разрешения, наличие которых проверяются
     * @param title          - надпись в тексте сообщения
     * @param message        - текст самого сообщения
     * @param codePermission - индентификатор, сообщающий дальнейшие действия
     * @return true - разрешние уже есть, false - разрешения нет
     */
    private static boolean isGrantedPermission(final String[] permissions, String title, String message, final int codePermission) {
        String notGrantedPermission = "";
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(prefAct, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                notGrantedPermission = permission;
                break;
            }
        }
        if (!notGrantedPermission.equals("")) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(prefAct, notGrantedPermission)) {
                new AlertDialog.Builder(prefAct)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(s(R.string.agree, prefAct), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(prefAct, permissions, codePermission);
                            }
                        })
                        .setNegativeButton(s(R.string.not_agree, prefAct), null)
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(prefAct, permissions, codePermission);
            }
            return false;
        }
        return true;
    }

    /**
     * проверка на наличие роли у приложения
     *
     * @param role     - роли, наличие которых проверяются
     * @param codeRole - индентификатор, сообщающий дальнейшие действия
     * @return true - роль уже есть, false - роли нет
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static boolean isGrantedRole(final String role, final int codeRole) {
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)) {
            return true;
        } else {
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION);
            prefAct.startActivityForResult(intent, 1003);
            return false;
        }
    }


    // метод, который выполняется при предоставлении / не предоставлении разрешния (permission)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1002:
                boolean isGranted = true;
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        isGranted = false;
                        break;
                    }
                }
                if (isGranted) {
                    // чтобы включить сервис проверяем не только наличие всех разрешений, но и роли
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || roleManager.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION)) {
                        // start detect service
                        Preference pref = findPreference("enable_redial");
                        ((CheckBoxPreference) pref).setChecked(true);
                        Intent intent = new Intent(pref.getContext(), CallDetectService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // это работает после 26 версии (8 Android)
                            pref.getContext().startForegroundService(intent);
                        } else {
                            pref.getContext().startService(intent);
                        }
                        pref.setSummary(R.string.service_on);
                        Log.d("myLogs", "RedialSettings: startService onChange");
                    } else {
                        Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION);
                        prefAct.startActivityForResult(intent, 1003);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), s(R.string.not_granted_permission
                            , getApplicationContext()), Toast.LENGTH_LONG).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case 1003: // проверяем предоставлена ли роль
                if (resultCode == Activity.RESULT_OK) {
                    // чтобы включить сервис проверяем не только наличие роли, но и всех разрешений
                    if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.PROCESS_OUTGOING_CALLS) == PackageManager.PERMISSION_GRANTED)
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED)
                            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.USE_FULL_SCREEN_INTENT) == PackageManager.PERMISSION_GRANTED)
                            && ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                            && ActivityCompat.checkSelfPermission(prefAct, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        // start detect service
                        Preference pref = findPreference("enable_redial");
                        ((CheckBoxPreference) pref).setChecked(true);
                        Intent intent = new Intent(pref.getContext(), CallDetectService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // это работает после 26 версии (8 Android)
                            pref.getContext().startForegroundService(intent);
                        } else {
                            pref.getContext().startService(intent);
                        }
                        pref.setSummary(R.string.service_on);
                        Log.d("myLogs", "RedialSettings: startService onChange");
                    } else {
                        ActivityCompat.requestPermissions(prefAct, permissions, 1002);
                    }
                } else {
                    Toast.makeText(getApplicationContext(), s(R.string.not_granted_role
                            , getApplicationContext()), Toast.LENGTH_LONG).show();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }



    /**
     * Shows the simplified settings UI if the device configuration if the
     * device configuration dictates that a simplified, single-pane UI should be
     * shown.
     */
    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        addPreferencesFromResource(R.xml.pref_null);

        // Add 'redial' preferences, and a corresponding header.
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.general);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_redial);

        // Add 'call back' preferences, and a corresponding header.
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.addition);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_addition);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference("enable_redial"));
        bindPreferenceSummaryToValue(findPreference("attempts"));
        bindPreferenceSummaryToValue(findPreference("dynamic"));
        bindPreferenceSummaryToValue(findPreference("redial_duration"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    /**
     * действия в случае покупки
     */
    public void purchased() {
        // сохраняем настройки
        amountAttempts = notConfirmedAmountAttempts;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(ATTEMPTS, amountAttempts + ""); // кол-во повторений
        editor.putBoolean(IS_PAID, true); // ставим статус оплаты
        editor.apply();
        Preference preference = findPreference(ATTEMPTS);
        preference.setSummary(amountAttempts + "");
        // вручную обновляем значение
        ((EditTextPreference) preference).setText(amountAttempts + "");
        isPaid = true;
    }

    /**
     * действия в случае не подтверждения покупки
     */
    public void notAcknowledgedPurchase() {
        // сохраняем настройки
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(ATTEMPTS, DEF_ATTEMPTS + ""); // кол-во повторений
        editor.putBoolean(IS_PAID, false); // ставим статус оплаты
        editor.apply();
        Preference preference = findPreference(ATTEMPTS);
        preference.setSummary(DEF_ATTEMPTS + "");
        // вручную обновляем значение
        ((EditTextPreference) preference).setText(DEF_ATTEMPTS + "");
        isPaid = false;
    }

    @Override
    protected void onDestroy() {
        if (pay != null) {
            pay.close();
            pay = null;
        }
        prefAct = null;
        super.onDestroy();
        Log.d("myLogs", "RedialSettings: onDestroy");
    }

    /**
     * This fragment shows auto redial preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class RedialPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_redial);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference("enable_redial"));
            bindPreferenceSummaryToValue(findPreference("attempts"));
            bindPreferenceSummaryToValue(findPreference("dynamic"));
            bindPreferenceSummaryToValue(findPreference("redial_duration"));
        }
    }

    /**
     * This fragment shows auto call back preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class AdditionPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_addition);
        }
    }
}
