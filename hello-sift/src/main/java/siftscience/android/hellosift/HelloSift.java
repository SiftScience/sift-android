// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android.hellosift;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import siftscience.android.Sift;

public class HelloSift extends AppCompatActivity {
    private static final String TAG = HelloSift.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_sift);

        Log.d(TAG, "onCreate");

        // Clear shared prefs
        SharedPreferences preferences = getSharedPreferences("siftscience", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();

        Sift.open(this, new Sift.Config.Builder()
                .withAccountId("YOUR_ACCOUNT_ID")
                .withBeaconKey("YOUR_BEACON_KEY")
                .build());

        Sift.setUserId("USER_ID");

        Sift.collect();

        Button buttonOther = (Button) findViewById(R.id.buttonOther);
        buttonOther.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                goToOtherActivity();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Sift.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Sift.resume(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Sift.close();
    }

    public void goToOtherActivity() {
        Intent intent = new Intent(this, OtherActivity.class);
        startActivity(intent);
    }
}
