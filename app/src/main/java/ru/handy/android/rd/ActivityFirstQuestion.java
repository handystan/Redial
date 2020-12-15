package ru.handy.android.rd;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

public class ActivityFirstQuestion extends AppCompatActivity implements View.OnClickListener {

    String number; // номер телефона
    String name; // имя контакта
    private TextView tvNumber;
    private TextView tvName;
    private Button bNo;
    private Button bYes;
    private Timer timer;
    private MyTimerTask myTimerTask;
    private ServiceConnection sConn; // connection с сервисом
    private CallDetectService callDetectService; // сервис
    private int secondsRemain = 10; // сколько секунд осталось до закрытия окна

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.first_question);
        setTitle(R.string.start_redial);
        tvNumber = (TextView) findViewById(R.id.tvNumber);
        tvName = (TextView) findViewById(R.id.tvName);
        bNo = (Button) findViewById(R.id.bNo);
        bYes = (Button) findViewById(R.id.bYes);
        Intent intent = getIntent();
        number = intent.getStringExtra("number");
        name = intent.getStringExtra("name");
        tvNumber.setText(number);
        tvName.setText(name == null ? s(R.string.unknown) : name);
        bNo.setOnClickListener(this);
        bYes.setOnClickListener(this);

        // запуск задачи по автоматическому закрытию окна через 10 сек., если пользователь ничего не сделал
        if (timer != null) timer.cancel();
        timer = new Timer();
        myTimerTask = new MyTimerTask();
        timer.schedule(myTimerTask, 0, 1000);

        // соединенеие с сервисом
        sConn = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) {
                callDetectService = ((CallDetectService.MyBinder) binder).getService();
            }

            public void onServiceDisconnected(ComponentName name) {
            }
        };
        bindService(new Intent(this, CallDetectService.class), sConn, 0);
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bYes) {
            try {
                // информируем сервис о запуске автодозвона
                callDetectService.setAutoCall(true);
                finish();
                cancelTimer();
                // вызываем звонилку
                Intent dial = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                startActivity(dial);
            } catch (SecurityException ex) {
                // это для строки Intent.ACTION_CALL (CALL_PHONE permission)
                Log.e("myLogs", "ex = " + ex.getMessage());
            }
        } else if (v.getId() == R.id.bNo) {
            callDetectService.setAutoCall(false);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
        unbindService(sConn); // разрываем связь с сервисом
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private String s(int res) {
        return getResources().getString(res);
    }

    class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            // все фоновые вычисления можно производить здесь

            // все изменения касающиеся интерфейса нужно запускать в UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (secondsRemain == 0) {
                        callDetectService.setAutoCall(false);
                        finish();
                        cancelTimer();
                    } else {
                        // пишем на кнопке сколько осталось секунд до закрытия окна
                        String text1 = s(R.string.no);
                        String allText = text1 + String.format(" 00:%02d", secondsRemain);
                        Spannable span = new SpannableString(allText);
                        span.setSpan(new RelativeSizeSpan(0.6f), text1.length(),
                                allText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        bNo.setText(span);
                        secondsRemain--;
                    }
                }
            });
        }
    }
}
