// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;

import static org.junit.Assert.*;

public class EventTest {

    @Test
    public void testEventEssentiallyEquals() {
        Event event0 = new Event("type-0", "path-0", ImmutableMap.of(
                "key-1", "value-1",
                "key-2", "value-2"));
        Event event1 = new Event("type-0", "path-0", ImmutableMap.of(
                "key-1", "value-1",
                "key-2", "value-2"));
        event1.time = event0.time + 1;  // Make sure their `time` differ.

        assertTrue(event0.basicallyEquals(event1));
    }

    @Test
    public void testEventToJson() {
        Event event = new Event("type-0", "path-0", ImmutableMap.of(
                "key-1", "value-1",
                "key-2", "value-2"));
        event.installationId = "some-id";
        event.deviceProperties = ImmutableMap.of("prop", "value");

        Event actual = Sift.GSON.fromJson(Sift.GSON.toJson(event), Event.class);
        assertTrue(event.basicallyEquals(actual));
        assertEquals(event.time, actual.time);
    }
}
