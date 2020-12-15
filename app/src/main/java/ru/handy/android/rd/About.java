package ru.handy.android.rd;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.TimeZone;

public class About extends AppCompatActivity implements View.OnClickListener {

    private int amountClick = 0; // количество нажатий на текстовое поле
    private TextView mTextView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        mTextView = (TextView) findViewById(R.id.tvAbout);
        mTextView.setText(Html.fromHtml(s(R.string.about_desc)));
        mTextView.setOnClickListener(this);

        // в actionBar делаем доступной кнопку "назад"
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    // обрабатываем кнопку "назад" в ActionBar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Операции для выбранного пункта меню
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * 10-кратное нажатие на поле позволяет отмечать в приложении оплачено оно или нет
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        if (v.getId() == mTextView.getId()) {
            amountClick++;
            if (amountClick == 10) {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                RedialSettings.isPaid = !RedialSettings.isPaid;
                editor.putBoolean(RedialSettings.IS_PAID, RedialSettings.isPaid); // ставим статус оплаты
                if (!RedialSettings.isPaid && RedialSettings.amountAttempts > RedialSettings.DEF_ATTEMPTS) {
                    RedialSettings.amountAttempts = RedialSettings.DEF_ATTEMPTS;
                    editor.putString(RedialSettings.ATTEMPTS, RedialSettings.amountAttempts + "");
                    Preference prefAttempts = RedialSettings.prefAct.findPreference(RedialSettings.ATTEMPTS);
                    prefAttempts.setSummary(RedialSettings.DEF_ATTEMPTS + "");
                    ((EditTextPreference) prefAttempts).setText(RedialSettings.DEF_ATTEMPTS + ""); // вручную обновляем значение/**/
                }
                editor.apply();
                amountClick = 0;
            }
        }
        /*// получаем кодовую цифру, помогающую менять переменную isPaid без оплаты
        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        int magicNum = cal.get(Calendar.YEAR) + cal.get(Calendar.DAY_OF_YEAR)
                + (cal.get(Calendar.MONTH) + 1) * cal.get(Calendar.DAY_OF_MONTH)
                * (cal.get(Calendar.DAY_OF_WEEK) == 1 ? 7 : cal.get(Calendar.DAY_OF_WEEK) - 1);
        if (amountAttempts == magicNum) {
            isPaid = !isPaid;
            if (!isPaid) amountAttempts = Math.min(amountAttempts, DEF_ATTEMPTS);
            // сохраняем настройки
            SharedPreferences.Editor editor = preference.getEditor();
            editor.putString(ATTEMPTS, amountAttempts + ""); // кол-во повторений
            editor.putBoolean(IS_PAID, isPaid); // ставим статус оплаты
            editor.apply();
            preference.setSummary(amountAttempts + "");
            // вручную обновляем значение
            ((EditTextPreference) preference).setText(amountAttempts + "");
        }*/
    }

    private String s(int res) {
        return getResources().getString(res);
    }
}
