// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class EventTest {

    @Test
    public void testEventEssentiallyEquals() {
        long now = System.currentTimeMillis();

        MobileEventJson event0 = new MobileEventJson()
                .withAndroidDeviceProperties(new AndroidDevicePropertiesJson()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                )
                .withTime(now);

        MobileEventJson event1 = new MobileEventJson()
                .withAndroidDeviceProperties(new AndroidDevicePropertiesJson()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                )
                .withTime(now);

        assertTrue(Utils.eventsAreBasicallyEqual(event0, event1));
    }

    @Test
    public void testEventToJson() throws IOException {
        MobileEventJson event = new MobileEventJson()
                .withAndroidDeviceProperties(new AndroidDevicePropertiesJson()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                )
                .withTime(System.currentTimeMillis());

        MobileEventJson actual = Sift.GSON.fromJson(Sift.GSON.toJson(event), MobileEventJson.class);

        assertTrue(Utils.eventsAreBasicallyEqual(event, actual));
        assertEquals(event.time, actual.time);
    }
}
