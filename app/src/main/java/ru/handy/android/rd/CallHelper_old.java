package ru.handy.android.rd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Date;

/**
 * Helper class to detect incoming and outgoing calls.
 * Created by Андрей on 28.07.2015.
 *
 * @author HandySystems
 */
public class CallHelper_old {

    private Context ctx;
    private TelephonyManager tm;
    private CallStateListener callStateListener;
    private OutgoingReceiver outgoingReceiver;

    public CallHelper_old(Context ctx) {
        this.ctx = ctx;
        callStateListener = new CallStateListener(ctx);
        outgoingReceiver = new OutgoingReceiver();
    }

    /**
     * Broadcast receiver to detect the outgoing calls.
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
     * Start calls detection.
     */
    public void start() {
        tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_NEW_OUTGOING_CALL);
        ctx.registerReceiver(outgoingReceiver, intentFilter);
    }

    /**
     * Stop calls detection.
     */
    public void stop() {
        ctx.unregisterReceiver(outgoingReceiver);
        tm.listen(callStateListener, PhoneStateListener.LISTEN_NONE);
    }

    public void setAutoCall(boolean isAutoCall) {
        callStateListener.setAutoCall(isAutoCall);
    }
}
