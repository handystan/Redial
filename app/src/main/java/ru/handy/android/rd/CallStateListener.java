package ru.handy.android.rd;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.telecom.TelecomManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Listener to detect incoming calls.
 * Created by Андрей on 15.08.2015.
 */
public class CallStateListener extends PhoneStateListener {

    private Context ctx;
    private boolean isIdle = true; // текущее состояние: true - нет никаких звонков
    private Date outgoingDate = null; // время начала исходящего звонка
    private boolean isAutoCall = false; // признак автозвонка
    private boolean isSysSpeakerphoneOn; // включен ли по умолчанию спикерфон в системе
    private int currentAttempt = 1; // номер текущего по порядоку автодозвона
    private AudioManager audioManager;
    private SharedPreferences sharedPref; // доступ к установленным настройкам приложения
    private Timer timer;
    private MyTimerTask myTimerTask;

    private CallHelper.CallRedirectionServiceImpl callRedirectionServiceImpl; // слушатель для исходящих вызовов для версии Android начиная с 10 (SDK 29)

    public CallStateListener(Context ctx) {
        this.ctx = ctx;
        audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
//        audioManager.setMode(AudioManager.MODE_IN_CALL);
        isSysSpeakerphoneOn = audioManager.isSpeakerphoneOn();
        sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    // установка слушателя для исходящих вызовов для версии Android начиная с 10 (SDK 29)
    public void setCallRedirectionServiceImpl(CallHelper.CallRedirectionServiceImpl callRedirectionServiceImpl) {
        this.callRedirectionServiceImpl = callRedirectionServiceImpl;
    }

    // установка даты начала исходящего звонка
    public void setOutgoingDate(Date outgoingDate) {
        // если последний вызов был вне сети и прекратился, то дозвон нужно начинать сначала
        if (this.outgoingDate != null) {
            isAutoCall = false;
            currentAttempt = 1;
        }
        this.outgoingDate = outgoingDate;
        if (isAutoCall) {
            // запуск задачи по автоматическому завершению автозвонка
            if (timer != null) cancelTimer();
            int redialDuration = Integer.parseInt(sharedPref.getString("redial_duration", "30"));
            if (redialDuration > 0) {
                timer = new Timer();
                myTimerTask = new MyTimerTask();
                timer.schedule(myTimerTask, redialDuration * 1000);
            }
        }
    }

    /**
     * установелние признака автодозвона
     *
     * @param isAutoCall
     */
    public void setAutoCall(boolean isAutoCall) {
        this.isAutoCall = isAutoCall;
        if (!isAutoCall) {
            currentAttempt = 1;
        }
    }

    @Override
    public void onCallStateChanged(int state, String number) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                // called when someone is ringing to this phone
                Log.d("myLogs", "CallStateListener: CALL_STATE_RINGING: " + number);
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                // вызывается, когда ничего не происходит или произошло окончание звонка
                Log.d("myLogs", "CallStateListener: CALL_STATE_IDLE: " + number);
                Log.d("myLogs", "outgoingDate =  " + outgoingDate + ", now = " + new Date());
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(isSysSpeakerphoneOn); // возвращаем спикерфон к настройкам системы
                Log.d("myLogs", "CallStateListener: audioManager.isSpeakerphoneOn() = " + audioManager.isSpeakerphoneOn());
                cancelTimer();
                if (!isIdle && outgoingDate != null) {
                    Cursor c = null;
                    try {
                        int i = 1;
                        while (i < 40) {
                            c = ctx.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                                    null, null, null, CallLog.Calls.DATE + " DESC");
                            int date = c.getColumnIndex(CallLog.Calls.DATE);
                            // из outgoingDate вычитаем 2 сек., т.к. в некоторых телефонах (в Sony
                            // например) запись в БД производится более ранним временем
                            if (c.moveToFirst()
                                    && (outgoingDate.getTime() - 2000) <= Long.valueOf(c.getString(date))) {
                                /*for (int j = 0; j < c.getColumnCount(); j++) {
                                    Log.d("myLogs", c.getColumnName(j) + "  " + c.getString(j));
                                }*/
                                int numberTel = c.getColumnIndex(CallLog.Calls.NUMBER);
                                int type = c.getColumnIndex(CallLog.Calls.TYPE);
                                int duration = c.getColumnIndex(CallLog.Calls.DURATION);
                                int name = c.getColumnIndex(CallLog.Calls.CACHED_NAME);
                                String phNumber = c.getString(numberTel);
                                String callType = c.getString(type);
                                String callDate = c.getString(date);
                                Date callDayTime = new Date(Long.valueOf(callDate));
                                String callDuration = c.getString(duration);
                                String callName = c.getString(name);
                                Log.d("myLogs", "CallStateListener: Number - " + phNumber + ", callType - " + callType
                                        + ", duration in sec - " + callDuration);
                                if (Integer.parseInt(callType) == CallLog.Calls.OUTGOING_TYPE
                                        && Integer.parseInt(callDuration) == 0) {
                                    if (!isAutoCall) {
                                        // запускаем Activity с первым вопросом
                                        Intent intent = new Intent(ctx, ActivityFirstQuestion.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        intent.putExtra("number", phNumber);
                                        intent.putExtra("name", callName);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            callRedirectionServiceImpl.startActivityFromService(intent);
                                            String notificationChannelId = "FULL SCREEN CHANNEL";
                                            NotificationChannel serviceChannel = new NotificationChannel(notificationChannelId,
                                                    "Full Screen Channel", NotificationManager.IMPORTANCE_HIGH);
                                            NotificationManager manager = ctx.getSystemService(NotificationManager.class);
                                            manager.createNotificationChannel(serviceChannel);
                                            Intent notificationIntent = new Intent(ctx, ActivityFirstQuestion.class);
                                            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            notificationIntent.putExtra("number", phNumber);
                                            notificationIntent.putExtra("name", callName);
                                            PendingIntent pendingIntent = PendingIntent.getActivity(ctx, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                                            Notification notification = new NotificationCompat.Builder(ctx, notificationChannelId)
                                                    .setContentTitle(callName)
                                                    .setContentText(phNumber)
                                                    .setPriority(NotificationCompat.PRIORITY_MAX)
                                                    .setCategory(NotificationCompat.CATEGORY_CALL)
                                                    .setSmallIcon(R.mipmap.ic_launcher)
                                                    .setFullScreenIntent(pendingIntent, true)
                                                    .build();
                                            int idNotifivation = (int) System.currentTimeMillis();
                                            manager.notify(idNotifivation, notification);
                                            //TimeUnit.MILLISECONDS.sleep(500);
                                            //manager.cancel(idNotifivation);
                                        } else {
                                            ctx.startActivity(intent);
                                        }
                                        currentAttempt = 2;
                                    } else {// если идет автодозвон
                                        if (currentAttempt <= Integer.parseInt(sharedPref.getString("attempts", "4"))) {
                                            // запускаем Activity с подтверждением о продолжении автодозвона
                                            Intent intent = new Intent(ctx, ActivitySecondQuestion.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                            intent.putExtra("number", phNumber);
                                            intent.putExtra("name", callName);
                                            intent.putExtra("amountAttempts", Integer.parseInt(sharedPref.getString("attempts", "4")));
                                            intent.putExtra("currentAttempt", currentAttempt);
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                callRedirectionServiceImpl.startActivityFromService(intent);
                                            } else {
                                                ctx.startActivity(intent);
                                            }
                                            currentAttempt++;
                                        } else {
                                            // все попытки автодозвона исчерпаны
                                            isAutoCall = false;
                                            currentAttempt = 1;
                                        }
                                    }
                                } else if (Integer.parseInt(callType) == CallLog.Calls.OUTGOING_TYPE
                                        && Integer.parseInt(callDuration) != 0) {
                                    // удалось дозвониться, поэтому следующие автодозвон начнется сначала
                                    isAutoCall = false;
                                    currentAttempt = 1;
                                }
                                break;
                            }
                            // ждем, когда в БД появится инфа о звонке
                            TimeUnit.MILLISECONDS.sleep(500);
                            i++;
                        }
                    } catch (SecurityException ex) {
                        // это для строки CallLog.Calls.CONTENT_URI (READ_CALL_LOG permission)
                        Log.e("myLogs", "CallStateListener: ex = " + ex.getMessage());
                    } catch (Exception ex) {
                        Log.e("myLogs", "CallStateListener: ex = " + ex.getMessage());
                    } finally {
                        if (c != null) c.close();
                    }
                }
                isIdle = true;
                outgoingDate = null;
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                /** Device call state: Off-hook. At least one call exists
                 * that is dialing, active, or on hold, and no calls are ringing
                 * or waiting. */
                //outgoingDate = new Date();
                Log.d("myLogs", "CallStateListener: CALL_STATE_OFFHOOK: " + number + ", outgoingDate = " + outgoingDate);
                isIdle = false;
                // включаем/выключаем спикерфон в зависимости от настроек в приложении
                if (isAutoCall) {
                    /*try {
                        Thread.sleep(500); // Delay 0,5 seconds to handle better turning on loudspeaker
                    } catch (InterruptedException e) {
                    }*/
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                    audioManager.setSpeakerphoneOn(sharedPref.getBoolean("dynamic", true));
                    Log.d("myLogs", "CallStateListener: sharedPref.getBoolean(\"dynamic\") = " + sharedPref.getBoolean("dynamic", true));
                    Log.d("myLogs", "CallStateListener: audioManager.isSpeakerphoneOn = " + audioManager.isSpeakerphoneOn());
/*                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            audioManager.setMode(AudioManager.MODE_IN_CALL);
                            audioManager.setSpeakerphoneOn(sharedPref.getBoolean("dynamic", true));
                            //MainActivity.shouldTurnSpeakerOn = false;
                            //MainActivity.shouldTurnSpeakerOff = true;
                            Log.d("myLogs", "CallStateListener: sharedPref.getBoolean(\"dynamic\") = " + sharedPref.getBoolean("dynamic", true));
                            Log.d("myLogs", "CallStateListener: audioManager.isSpeakerphoneOn = " + audioManager.isSpeakerphoneOn());
                        }
                    }, 500);*/
                }
                break;
            default:
                Log.d("myLogs", "CallStateListener: Default: " + state);
                break;
        }
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    // задачка по остановке автозвонка через количество секунд, установленного в настройках
    class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            try {
                Class c = Class.forName(tm.getClass().getName());
                Method method = c.getDeclaredMethod("getITelephony");
                method.setAccessible(true);
                Object telephonyService = method.invoke(tm); // Get the internal ITelephony object
                c = Class.forName(telephonyService.getClass().getName()); // Get its class
                method = c.getDeclaredMethod("endCall"); // Get the "endCall()" method
                method.setAccessible(true); // Make it accessible
                method.invoke(telephonyService); // invoke endCall()
                /* эти 2 строки были вместо предыдущих 5, но они не работали на реальных телефонах
                ITelephony telephonyService = (ITelephony) method.invoke(tm);
                telephonyService.endCall();*/
                cancelTimer();
            } catch (java.lang.NoSuchMethodException ex) {
                Log.w("myLogs", "CallStateListener: ex = " + ex);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // метод endCall работает только с 28 версии SDK
                    TelecomManager telecomManager = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                    if (telecomManager != null) {
                        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                            telecomManager.endCall();
                        } else {
                            Log.w("myLogs", "CallStateListener: не предоставлена permission ANSWER_PHONE_CALLS, поэтому ограничение времени звонка не срабатывает");
                        }
                    } else {
                        Log.w("myLogs", "CallStateListener: telecomManager is null, поэтому ограничение времени звонка не срабатывает");
                    }
                } else {
                    Log.w("myLogs", "CallStateListener: ниже 28 версии SDK нельзя вызвать метод endCall, поэтому ограничение времени звонка не срабатывает");
                }
            } catch (Exception ex) {
                Log.e("myLogs", "CallStateListener: ex = " + ex);
            }
        }
    }

}
