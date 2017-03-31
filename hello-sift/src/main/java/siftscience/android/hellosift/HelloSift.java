// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android.hellosift;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import siftscience.android.Sift;

public class HelloSift extends AppCompatActivity {
    private static final String TAG = HelloSift.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_hello_sift);

        // Clear shared prefs
        SharedPreferences preferences = getSharedPreferences("siftscience", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();

        Sift.open(this, new Sift.Config.Builder()
                .withAccountId("4e1a50e172beb95cf1e4ae54")
                .withBeaconKey("f92ed5bf7b")
                .build());

        Sift.get().setUserId("gary");

        Sift.collect();

        Button buttonUpload = (Button) findViewById(R.id.buttonUpload);
        buttonUpload.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Force upload");
                Sift.get().upload(true);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        Sift.get().save();
    }

    protected void onResume() {
        super.onResume();
        Sift.get().resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        Sift.close();
    }
}
