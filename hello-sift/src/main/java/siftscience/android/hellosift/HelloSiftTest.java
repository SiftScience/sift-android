package siftscience.android.hellosift;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.sift.api.representations.MobileEventJson;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import siftscience.android.AppStateCollector;
import siftscience.android.Queue;
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

        final TextView deviceDebug = (TextView) findViewById(R.id.deviceDebug);

        deviceDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Queue queue = Sift.get().getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER);

                try {
                    Method m = queue.getClass().getDeclaredMethod("transfer");
                    m.setAccessible(true);
                    List<MobileEventJson> backingQueue = (List<MobileEventJson>) m.invoke(queue);

                    if (!backingQueue.isEmpty()) {
                        String result = Joiner.on(" ").join(Lists.newArrayList(Integer.toString(backingQueue.size()),
                                backingQueue.get(0).androidDeviceProperties.appName,
                                backingQueue.get(0).userId,
                                Sift.get().getConfig().accountId,
                                Sift.get().getConfig().beaconKey,
                                Sift.get().getConfig().serverUrlFormat
                        ));

                        deviceDebug.setText(result);
                    } else {
                        deviceDebug.setText("empty");
                    }
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        });

        final TextView appDebug = (TextView) findViewById(R.id.appDebug);

        appDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Queue queue = Sift.get().getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER);

                try {
                    Method m = queue.getClass().getDeclaredMethod("transfer");
                    m.setAccessible(true);
                    List<MobileEventJson> backingQueue = (List<MobileEventJson>) m.invoke(queue);

                    if (!backingQueue.isEmpty()) {
                        String result = Joiner.on(" ").join(Lists.newArrayList(Integer.toString(backingQueue.size()),
                                backingQueue.get(0).androidAppState.activityClassName,
                                backingQueue.get(0).userId,
                                Sift.get().getConfig().accountId,
                                Sift.get().getConfig().beaconKey,
                                Sift.get().getConfig().serverUrlFormat
                        ));

                        appDebug.setText(result);
                    } else {
                        appDebug.setText("empty");
                    }
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
