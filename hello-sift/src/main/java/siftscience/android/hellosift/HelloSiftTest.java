// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android.hellosift;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Field;

import siftscience.android.AppStateCollector;
import siftscience.android.Sift;

public class HelloSiftTest extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello_sift_test);

        // Clear shared prefs
        SharedPreferences preferences = getSharedPreferences("siftscience", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();

        Sift.open(this, new Sift.Config.Builder()
                .withAccountId("bar")
                .withBeaconKey("baz")
                .withDisallowLocationCollection(true)
                .withDebugLoggingEnabled(true)
                .build());

        Sift.get().setUserId("foo");

        Sift.collect();

        final Button collect = (Button) findViewById(R.id.collect);

        collect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Field f = Sift.get().getClass().getDeclaredField("appStateCollector");
                    f.setAccessible(true);
                    AppStateCollector appStateCollector = (AppStateCollector) f.get(null);
                    appStateCollector.collect();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }

                Sift.collect();
            }
        });
    }
}
