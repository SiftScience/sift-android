// Copyright (c) 2017 Sift Science. All rights reserved.

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import com.sift.api.representations.MobileEventJson;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import siftscience.android.Queue;
import siftscience.android.Sift;
import siftscience.android.hellosift.HelloSiftTest;
import siftscience.android.hellosift.R;

import static android.support.test.espresso.Espresso.onView;

import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * Test basic collection on Activity onCreate().
 */

@RunWith(AndroidJUnit4.class)
public class BasicIntegrationTest {
    @Rule
    public ActivityTestRule<HelloSiftTest> helloSiftActivityTestRule =
            new ActivityTestRule(HelloSiftTest.class);

    @Test
    public void basicTest() {
        onView(withId(R.id.deviceDebug)).perform(click());
        Queue queue = Sift.get().getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER);

        try {
            Method m = queue.getClass().getDeclaredMethod("transfer");
            m.setAccessible(true);
            List<MobileEventJson> backingQueue = (List<MobileEventJson>) m.invoke(queue);

            if (backingQueue.size() != 0) {
                onView(withId(R.id.collect))
                        .check(matches(withText("device properties queue should be flushed " +
                                "but has size " + backingQueue.size())));
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
