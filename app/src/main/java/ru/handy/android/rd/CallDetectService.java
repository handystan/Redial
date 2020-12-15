package ru.handy.android.rd;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.Date;

/**
 * Call detect service.
 * This service is needed, because MainActivity can lost it's focus,
 * and calls will not be detected.
 *
 * @author HandySystems
 */
public class CallDetectService extends Service {

    private CallHelper callHelper;
    private MyBinder binder = new MyBinder();
    private String notificationChannelId = "ENDLESS FOREGROUND SERVICE CHANNEL";

    public CallDetectService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("myLogs", "CallDetectService: onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("myLogs", "CallDetectService: startId = " + startId);
        // запускаем хэлпер только при первом запуске сервиса
        if (startId == 1) {
            callHelper = new CallHelper(this);
            callHelper.start();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { // это работает после 26 версии (8 Android)
                //сначала создаем канал для нотификации
                NotificationChannel serviceChannel = new NotificationChannel(
                        notificationChannelId, "Foreground Service Channel",
                        NotificationManager.IMPORTANCE_DEFAULT);
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.createNotificationChannel(serviceChannel);
                //затем создаем саму нотификацию
                Intent notificationIntent = new Intent(this, RedialSettings.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
                Notification notification = new NotificationCompat.Builder(this, notificationChannelId)
                        .setContentTitle(s(R.string.autoredial_on, this))
                        .setContentText(s(R.string.press_to_change, this))
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentIntent(pendingIntent)
                        .build();
                startForeground(1, notification);
            }
        }
        return START_REDELIVER_INTENT; // после убивания сервиса системой он восстанавливается
    }

    public void setAutoCall(boolean isAutoCall) {
        callHelper.setAutoCall(isAutoCall);
    }

    @Override
    public void onDestroy() {
        Log.d("myLogs", "CallDetectService: Service is destroyed at " + new Date());
        if (callHelper != null) callHelper.stop();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { // это работает после 26 версии (8 Android)
            stopForeground(true);
        }
        stopSelf();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return binder;
    }

    class MyBinder extends Binder {
        CallDetectService getService() {
            return CallDetectService.this;
        }
    }

    private static String s(int res, Context ctx) {
        return ctx.getResources().getString(res);
    }
}
