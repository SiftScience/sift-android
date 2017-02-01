// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android.hellosift;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import siftscience.android.Sift;

public class HelloSift extends AppCompatActivity {
    private static final String TAG = HelloSift.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate");

        setContentView(R.layout.activity_hello_sift);

        Sift.open(this);

        // TODO(gary): Remove testing code eventually
        Sift.get().setUserId("janice");
        Sift.get().setConfig(new Sift.Config.Builder()
                .withAccountId("4e1a50e172beb95cf1e4ae54")
                .withBeaconKey("f92ed5bf7b")
                .build()
        );

        Sift.collect(this);

        // Configure Sift object.  If you have multiple activities, you
        // probably should only do this in the "main" activity (the
        // activity that starts first).
        Sift.Config.Builder builder = new Sift.Config.Builder(Sift.get().getConfig());

        Button buttonUpload = (Button) findViewById(R.id.buttonUpload);
        buttonUpload.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Force upload");
                Sift.get().upload(true);
            }
        });

        EditText editTextAccountId = (EditText) findViewById(R.id.editTextAccountId);
        // Configure Sift object with UI component's default text (same
        // below).
        builder.withAccountId(editTextAccountId.getText().toString());
        editTextAccountId.addTextChangedListener(new AfterTextChanged() {
            @Override
            public void afterTextChanged(Editable editable) {
                Log.d(TAG, String.format("Account ID: \"%s\"", editable.toString()));
                Sift sift = Sift.get();
                sift.setConfig(new Sift.Config.Builder(sift.getConfig())
                        .withAccountId(editable.toString())
                        .build());
            }
        });

        EditText editTextBeaconKey = (EditText) findViewById(R.id.editTextBeaconKey);
        builder.withBeaconKey(editTextBeaconKey.getText().toString());
        editTextBeaconKey.addTextChangedListener(new AfterTextChanged() {
            @Override
            public void afterTextChanged(Editable editable) {
                Log.d(TAG, String.format("Beacon key: \"%s\"", editable.toString()));
                Sift sift = Sift.get();
                sift.setConfig(new Sift.Config.Builder(sift.getConfig())
                        .withBeaconKey(editable.toString())
                        .build());
            }
        });

        EditText editTextServerUrl = (EditText) findViewById(R.id.editTextServerUrl);
        builder.withServerUrlFormat(editTextServerUrl.getText().toString());
        editTextServerUrl.addTextChangedListener(new AfterTextChanged() {
            @Override
            public void afterTextChanged(Editable editable) {
                Log.d(TAG, String.format("Server URL: \"%s\"", editable.toString()));
                Sift sift = Sift.get();
                sift.setConfig(new Sift.Config.Builder(sift.getConfig())
                        .withServerUrlFormat(editable.toString())
                        .build());
            }
        });

        Sift.get().setConfig(builder.build());

        EditText editTextUserId = (EditText) findViewById(R.id.editTextUserId);
        editTextUserId.addTextChangedListener(new AfterTextChanged() {
            @Override
            public void afterTextChanged(Editable editable) {
                Log.d(TAG, String.format("User ID: \"%s\"", editable.toString()));
                Sift.get().setUserId(editable.toString());
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        Sift.get().save();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        Sift.close();
    }

    private static abstract class AfterTextChanged implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
            // Nothing here.
        }

        @Override
        public void onTextChanged(CharSequence sequence, int start, int before, int count) {
            // Nothing here.
        }
    }
}
