package com.slash.batterychargelimit;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;
import com.slash.batterychargelimit.R;
import com.slash.batterychargelimit.SharedMethods;

/**
 * Created by Michael on 20.04.2017.
 *
 * Handles new battery limits sent via Intents.
 */
public class LimitChangeActivity extends AppCompatActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // handle data sent by intent
        String batteryLimitMime = this.getString(R.string.mime_battery_limit);
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && batteryLimitMime.equals(intent.getType())) {
            SharedMethods.handleLimitChange(this, intent.getExtras().get(Intent.EXTRA_TEXT));
        } else {
            Toast.makeText(this, R.string.intent_invalid, Toast.LENGTH_LONG).show();
        }

        finish();
    }

}
