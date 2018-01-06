// Copyright (c) 2017 Sift Science. All rights reserved.

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

        MobileEventJson event0 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                        .build()
                )
                .withTime(now)
                .build();

        MobileEventJson event1 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                        .build()
                )
                .withTime(now)
                .build();

        assertTrue(Utils.eventsAreBasicallyEqual(event0, event1));
    }

    @Test
    public void testEventToJson() throws IOException {
        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        MobileEventJson actual = Sift.GSON.fromJson(Sift.GSON.toJson(event), MobileEventJson.class);

        assertTrue(Utils.eventsAreBasicallyEqual(event, actual));
        assertEquals(event.time, actual.time);
    }
}
