package ru.handy.android.rd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.telecom.CallRedirectionService;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.Date;

/**
 * Helper class to detect incoming and outgoing calls.
 * Created by Андрей on 28.07.2015.
 *
 * @author HandySystems
 */
public class CallHelper {

    private static Context ctx;
    private TelephonyManager tm;
    private static CallStateListener callStateListener;
    private OutgoingReceiver outgoingReceiver; // слушатель для исходящих вызовов для версии Android до 10 (SDK 29)
    private CallRedirectionServiceImpl callRedirectionServiceImpl; // слушатель для исходящих вызовов для версии Android начиная с 10 (SDK 29)

    public CallHelper(Context ctx) {
        this.ctx = ctx;
        callStateListener = new CallStateListener(ctx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callRedirectionServiceImpl = new CallRedirectionServiceImpl();
            callStateListener.setCallRedirectionServiceImpl(callRedirectionServiceImpl);
        } else {
            outgoingReceiver = new OutgoingReceiver();
        }
    }

    /**
     * Start calls detection.
     */
    public void start() {
        tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_NEW_OUTGOING_CALL);
            ctx.registerReceiver(outgoingReceiver, intentFilter);
        }
    }

    /**
     * Stop calls detection.
     */
    public void stop() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ctx.unregisterReceiver(outgoingReceiver);
        }
        tm.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
        Log.d("myLogs", "CallHelper is stopped");
    }

    public void setAutoCall(boolean isAutoCall) {
        callStateListener.setAutoCall(isAutoCall);
    }

    /**
     * Broadcast receiver to detect the outgoing calls до версии SDK 29 (Android 10)
     */
    public class OutgoingReceiver extends BroadcastReceiver {
        public OutgoingReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Date outgoingDate = new Date();
            callStateListener.setOutgoingDate(outgoingDate);
            Log.d("myLogs", "CallHelper: action = " + intent.getAction() + ", number = " + number);
        }
    }

    /**
     * Класс пришедший на смену BroadcastReceiver с версии SDK 29 (Android 10)
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static class CallRedirectionServiceImpl extends CallRedirectionService {
        public CallRedirectionServiceImpl() {
        }

        /**
         * @param handle                   the phone number dialed by the user, represented in E.164 format if possible
         * @param initialPhoneAccount      the {@link PhoneAccountHandle} on which the call will be placed.
         * @param allowInteractiveResponse a boolean to tell if the implemented
         *                                 {@link CallRedirectionService} should allow interactive
         *                                 responses with users. Will be {@code false} if, for example
         *                                 the device is in car mode and the user would not be able to
         */
        @Override
        public void onPlaceCall(@NonNull Uri handle, @NonNull PhoneAccountHandle initialPhoneAccount, boolean allowInteractiveResponse) {
            Date outgoingDate = new Date();
            callStateListener.setOutgoingDate(outgoingDate);
            Log.d("myLogs", "CallHelper.CallRedirectionServiceImpl: number = " + handle + ", allowInteractiveResponse = " + allowInteractiveResponse);
            placeCallUnmodified();
        }

        public void startActivityFromService (Intent intent) {
            ctx.startActivity(intent);
        }
    }
}
