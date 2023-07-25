// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android.hellosift;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import siftscience.android.AccountKey;
import siftscience.android.Sift;

public class HelloSift extends AppCompatActivity {
    private static final String TAG = HelloSift.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_sift);

        // Clear shared prefs
        SharedPreferences preferences = getSharedPreferences("siftscience", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();

        List<AccountKey> accountKeys = new ArrayList<>();
        accountKeys.add(new AccountKey("ACCOUNT_ID_2", "BEACON_KEY_2"));
        accountKeys.add(new AccountKey("ACCOUNT_ID_3", "BEACON_KEY_3"));
        Sift.open(this, new Sift.Config.Builder()
                .withAccountId("ACCOUNT_ID_1")
                .withBeaconKey("BEACON_KEY_1")
                .withAccountKeys(accountKeys)
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
    protected void onDestroy() {
        super.onDestroy();
        Sift.close();
    }

    public void goToOtherActivity() {
        Intent intent = new Intent(this, OtherActivity.class);
        startActivity(intent);
    }
}
