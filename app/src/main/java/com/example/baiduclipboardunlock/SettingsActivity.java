package com.example.baiduclipboardunlock;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private EditText wordLimitInput;
    private Switch dedupBypassSwitch;
    private Switch countOverrideSwitch;
    private EditText maxCountInput;
    private Switch historyFilterBypassSwitch;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.app_name);

        prefs = openModuleSharedPreferences();

        wordLimitInput = findViewById(R.id.input_word_limit);
        dedupBypassSwitch = findViewById(R.id.switch_dedup_bypass);
        countOverrideSwitch = findViewById(R.id.switch_count_override);
        maxCountInput = findViewById(R.id.input_max_count);
        historyFilterBypassSwitch = findViewById(R.id.switch_history_filter_bypass);
        Button saveButton = findViewById(R.id.button_save);

        wordLimitInput.setText(String.valueOf(
                prefs.getInt(Prefs.KEY_WORD_LIMIT, Prefs.DEFAULT_WORD_LIMIT)));
        dedupBypassSwitch.setChecked(
                prefs.getBoolean(Prefs.KEY_ENABLE_DEDUP_BYPASS, Prefs.DEFAULT_ENABLE_DEDUP_BYPASS));
        countOverrideSwitch.setChecked(
                prefs.getBoolean(Prefs.KEY_ENABLE_COUNT_OVERRIDE, Prefs.DEFAULT_ENABLE_COUNT_OVERRIDE));
        maxCountInput.setText(String.valueOf(
                prefs.getInt(Prefs.KEY_MAX_COUNT, Prefs.DEFAULT_MAX_COUNT)));
        historyFilterBypassSwitch.setChecked(prefs.getBoolean(
                Prefs.KEY_ENABLE_HISTORY_FILTER_BYPASS, Prefs.DEFAULT_ENABLE_HISTORY_FILTER_BYPASS));

        saveButton.setOnClickListener(v -> saveValues());
    }

    private SharedPreferences openModuleSharedPreferences() {
        try {
            //noinspection deprecation
            return getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            return getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private void saveValues() {
        Integer wordLimit = parseBounded(wordLimitInput, 1, Prefs.MAX_LIMIT_VALUE);
        if (wordLimit == null) {
            Toast.makeText(this, R.string.error_invalid_number, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer maxCount = parseBounded(maxCountInput, 1, Prefs.MAX_COUNT_LIMIT_VALUE);
        if (maxCount == null) {
            Toast.makeText(this, R.string.error_invalid_number, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(Prefs.KEY_WORD_LIMIT, wordLimit);
        editor.putBoolean(Prefs.KEY_ENABLE_DEDUP_BYPASS, dedupBypassSwitch.isChecked());
        editor.putBoolean(Prefs.KEY_ENABLE_COUNT_OVERRIDE, countOverrideSwitch.isChecked());
        editor.putInt(Prefs.KEY_MAX_COUNT, maxCount);
        editor.putBoolean(Prefs.KEY_ENABLE_HISTORY_FILTER_BYPASS, historyFilterBypassSwitch.isChecked());
        editor.commit();

        Toast.makeText(this, R.string.saved_restart_hint, Toast.LENGTH_LONG).show();
    }

    private Integer parseBounded(EditText input, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < min || value > max) return null;
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
