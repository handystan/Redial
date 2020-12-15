package ru.handy.android.rd;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.view.MenuItem;
import android.widget.TextView;

public class Help extends AppCompatActivity {

    private TextView mTextView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help);
        mTextView = (TextView) findViewById(R.id.tvHelp);
        mTextView.setText(Html.fromHtml(s(R.string.help_desc)));

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

    private String s(int res) {
        return getResources().getString(res);
    }
}
