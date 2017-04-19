import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import siftscience.android.hellosift.HelloSiftTest;
import siftscience.android.hellosift.R;

import static android.support.test.espresso.Espresso.onView;

import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

/**
 * Test for the MainActivity screen
 */

@RunWith(AndroidJUnit4.class)
public class BasicIntegrationTest {
    @Rule
    public ActivityTestRule<HelloSiftTest> helloSiftActivityTestRule =
            new ActivityTestRule(HelloSiftTest.class);

    @Test
    public void basicDeviceTest() {
        onView(withId(R.id.deviceDebug)).perform(click());
        onView(withId(R.id.deviceDebug)).check(matches(withText("empty")));
    }

    @Test
    public void basicAppTest() {
        onView(withId(R.id.appDebug)).perform(click());
        onView(withId(R.id.appDebug)).check(matches(withText("1 HelloSiftTest foo bar baz https://api3.siftscience.com/v3/accounts/%s/mobile_events")));
    }
}
