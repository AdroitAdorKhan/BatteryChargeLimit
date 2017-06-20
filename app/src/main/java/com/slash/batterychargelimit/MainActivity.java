package com.slash.batterychargelimit;

import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import eu.chainfire.libsuperuser.Shell;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.List;

import static com.slash.batterychargelimit.Constants.*;

public class MainActivity extends AppCompatActivity {
    private SeekBar rangeBar;
    private TextView rangeText;
    private TextView status_TextView;
    private EditText limit_TextView;
    private SharedPreferences settings;
    private RadioGroup batteryFile_RadioGroup;
    private Switch enable_Switch;
    private boolean initComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Exit immediately if no root support
        if (!Shell.SU.available()) {
            Toast.makeText(this, R.string.root_denied, Toast.LENGTH_LONG );
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(R.string.root_denied)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    }).create().show();
            return;
        }

        settings = getSharedPreferences(SETTINGS, 0);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean previouslyStarted = prefs.getBoolean(getString(R.string.previously_started), false);
        if (!previouslyStarted) {
            // whitelist App for Doze Mode
            Shell.SU.run("dumpsys deviceidle whitelist +com.slash.batterychargelimit");
            prefs.edit().putBoolean(getString(R.string.previously_started), true).apply();
        }

        int settingsVersion = prefs.getInt(SETTINGS_VERSION, 0);
        int versionCode = 0;
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace(); // should never happen
        }
        if (settingsVersion < versionCode) {
            switch(settingsVersion) {
                case 0:
                    boolean found = false;
                    for (ControlFile cf : getCtrlFiles()) {
                        if (cf.isValid()) {
                            setCtrlFile(cf);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage(R.string.device_not_supported)
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                }).create().show();
                        return;
                    }
                case 1: case 2: case 3: case 4:
                    if (settings.contains("limit_reached")) {
                        settings.edit().remove("limit_reached").apply();
                    }
                case 5: case 6: case 7: case 8: case 9: case 10: case 11: case 12: case 13:
                    if (settings.contains("recharge_threshold")) {
                        int limit = settings.getInt(LIMIT, 80);
                        int diff = settings.getInt("recharge_threshold", limit - 2);
                        settings.edit().putInt(MIN, limit - diff).remove("recharge_threshold").apply();
                    }
                case 14:
                    // settings upgrade for future version(s)
            }
            // update the settings version
            prefs.edit().putInt(SETTINGS_VERSION, versionCode).apply();
        }

        boolean is_enabled = settings.getBoolean(ENABLE, false);

        if (is_enabled && SharedMethods.isPhonePluggedIn(this)) {
            this.startService(new Intent(this, ForegroundService.class));
        }

        batteryFile_RadioGroup = (RadioGroup) findViewById(R.id.rgOpinion);
        enable_Switch = (Switch) findViewById(R.id.button1);
        limit_TextView = (EditText) findViewById(R.id.limit_EditText);
        status_TextView = (TextView) findViewById(R.id.status);
        final Button resetBatteryStats_Button = (Button) findViewById(R.id.reset_battery_stats);
        rangeBar = (SeekBar) findViewById(R.id.range_bar);
        rangeText = (TextView) findViewById(R.id.range_text);
        final Switch autoResetSwitch = (Switch) findViewById(R.id.auto_stats_reset);

        enable_Switch.setChecked(is_enabled);
        updateRadioButtons(true);
        autoResetSwitch.setChecked(settings.getBoolean(AUTO_RESET_STATS, false));

        // if limit is enabled, disable all editable settings
        if (is_enabled) {
            limit_TextView.setEnabled(false);
            rangeBar.setEnabled(false);
        }

        enable_Switch.setOnCheckedChangeListener(switchListener);
        limit_TextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int t = 0;
                try {
                    t = Integer.parseInt(limit_TextView.getText().toString());
                } catch (NumberFormatException e) {
                    //ignore this exception
                }
                if (t >= 40 && t <= 99) {
                    SharedMethods.setLimit(t, settings);
                    int min = settings.getInt(MIN, t - 2);
                    rangeBar.setMax(t);
                    rangeBar.setProgress(min);
                    updateRangeText(min);
                }
            }
        });
        rangeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int min, boolean fromUser) {
                settings.edit().putInt(MIN, min).apply();
                updateRangeText(min);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        resetBatteryStats_Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedMethods.resetBatteryStats(MainActivity.this);
            }
        });
        autoResetSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.edit().putBoolean(AUTO_RESET_STATS, isChecked).apply();
            }
        });

        //The onCreate() process was not stopped via return, UI elements should be available
        initComplete = true;
    }

    //OnCheckedChangeListener for Switch elements
    private CompoundButton.OnCheckedChangeListener switchListener = new CompoundButton.OnCheckedChangeListener() {
        Context context = MainActivity.this;
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                // reset TextView content to valid number
                limit_TextView.setText(String.valueOf(settings.getInt(LIMIT, 80)));
                SharedMethods.enableService(context);
            } else {
                SharedMethods.disableService(context);
            }

            settings.edit().putBoolean(ENABLE, isChecked).apply();

            limit_TextView.setEnabled(!isChecked);
            updateRadioButtons(false);
            rangeBar.setEnabled(!isChecked);

            EnableWidgetIntentReceiver.updateWidget(context, isChecked);
        }
    };

    private List<ControlFile> ctrlFiles = null;
    private List<ControlFile> getCtrlFiles() {
        if (ctrlFiles == null) {
            try {
                Reader r = new InputStreamReader(getResources().openRawResource(R.raw.control_files),
                        Charset.forName("UTF-8"));
                Gson gson = new Gson();
                ctrlFiles = gson.fromJson(r, new TypeToken<List<ControlFile>>(){}.getType());
            } catch (Exception e) {
                e.printStackTrace();
                finish();
            }
        }
        return ctrlFiles;
    }

    private View.OnClickListener radioButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            setCtrlFile(getCtrlFiles().get((int) v.getTag(R.id.radio_index_tag)));
        }
    };

    private static ColorStateList radioDefaultColors = null;
    private void updateRadioButtons(boolean init) {
        if (radioDefaultColors == null) {
            synchronized (MainActivity.class) {
                if (radioDefaultColors == null) {
                    radioDefaultColors = new RadioButton(this).getTextColors();
                }
            }
        }
        List<ControlFile> ctrlFiles = getCtrlFiles();
        String currentCtrlFile = settings.getString(FILE_KEY, "");
        boolean serviceEnabled = settings.getBoolean(ENABLE, false);
        for (int i = 0; i < ctrlFiles.size(); i++) {
            RadioGroup.LayoutParams layoutParams = init ? new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT) : null;
            ControlFile cf = ctrlFiles.get(i);
            RadioButton b;
            if (init) {
                b = new RadioButton(this);
                b.setId(View.generateViewId());
                b.setTag(R.id.radio_index_tag, i);
                if (cf.isExperimental()) {
                    b.setText(getString(R.string.experimental, cf.getLabel()));
                } else {
                    b.setText(cf.getLabel());
                }
                b.setChecked(currentCtrlFile.equals(cf.getFile()));
                b.setOnClickListener(radioButtonListener);
                batteryFile_RadioGroup.addView(b, i, layoutParams);
            } else {
                b = (RadioButton) batteryFile_RadioGroup.getChildAt(i);
            }
            // if service is not active, enable control file radio button if the control file is valid
            b.setEnabled(!serviceEnabled && cf.isValid());
            // color experimental control files red
            if (cf.isExperimental() && !serviceEnabled) {
                b.setTextColor(Color.RED);
            } else {
                b.setTextColor(radioDefaultColors);
            }
        }
    }

    private void setCtrlFile(ControlFile cf) {
        getSharedPreferences(SETTINGS, 0)
                .edit().putString(FILE_KEY, cf.getFile())
                .putString(CHARGE_ON_KEY, cf.getChargeOn())
                .putString(CHARGE_OFF_KEY, cf.getChargeOff()).apply();
    }

    //to update battery status on UI
    private BroadcastReceiver charging = new BroadcastReceiver() {
        private int previousStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;

        @Override
        public void onReceive(Context context, Intent intent) {
            int currentStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            if (currentStatus != previousStatus && status_TextView != null) {
                previousStatus = currentStatus;
                if (currentStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
                    status_TextView.setText(R.string.charging);
                    status_TextView.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.darkGreen));
                } else if (currentStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                    status_TextView.setText(R.string.discharging);
                    status_TextView.setTextColor(Color.DKGRAY);
                } else if (currentStatus == BatteryManager.BATTERY_STATUS_FULL) {
                    status_TextView.setText(R.string.full);
                    status_TextView.setTextColor(Color.BLACK);
                } else if (currentStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
                    status_TextView.setText(R.string.not_charging);
                    status_TextView.setTextColor(Color.BLACK);
                } else if (currentStatus == BatteryManager.BATTERY_STATUS_UNKNOWN) {
                    status_TextView.setText(R.string.unknown);
                    status_TextView.setTextColor(Color.BLACK);
                }
            }
        }
    };


    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.about:
                Intent intent = new Intent(this, About.class);
                this.startActivity(intent);
        }
        return true;
    }

    @Override
    public void onStop() {
        if(initComplete) {
            unregisterReceiver(charging);
        }
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(initComplete) {
            registerReceiver(charging, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            // the limits could have been changed by an Intent, so update the UI here
            updateUi();
        }
    }

    private void updateRangeText(int min) {
        if (min == 0) {
            rangeText.setText(R.string.no_recharge);
        } else {
            rangeText.setText(getString(R.string.recharge_below, min));
        }
    }

    private void updateUi() {
        enable_Switch.setChecked(settings.getBoolean(ENABLE, false));

        int limit = settings.getInt(LIMIT, 80);
        int min = settings.getInt(MIN, limit - 2);
        limit_TextView.setText(String.valueOf(limit));
        rangeBar.setMax(limit);
        rangeBar.setProgress(min);
        updateRangeText(min);
    }
}
