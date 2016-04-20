// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import android.support.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * The POJO class that maps 1:1 to our JSON schema of the event object.
 */
public class Event {

    public long time;
    @SerializedName("mobile_event_type") public String type;
    public String path;
    public String userId;
    public Map<String, String> fields;

    // Package private - these are used internally.
    String installationId;
    Map<String, String> deviceProperties;

    // Gson recommends that you provide a no-args constructor.
    Event() {}

    public Event(@Nullable String type,
                 @Nullable String path,
                 @Nullable Map<String, String> fields) {
        this.time = Time.now();
        this.type = type;
        this.path = path;
        this.fields = fields;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("time", time)
                .add("type", type)
                .add("path", path)
                .add("userId", userId)
                .add("fields", fields)
                .add("installationId", installationId)
                .add("deviceProperties", deviceProperties)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                time, type, path, userId, fields, installationId, deviceProperties);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Event))
            return false;
        Event other = (Event)object;
        return Objects.equal(time, other.time) && basicallyEquals(other);
    }

    /** Compare all fields except event time. */
    public boolean basicallyEquals(Object object) {
        if (!(object instanceof Event))
            return false;
        return basicallyEquals((Event)object);
    }

    private boolean basicallyEquals(Event other) {
        return Objects.equal(type, other.type)
                && Objects.equal(path, other.path)
                && Objects.equal(userId, other.userId)
                && Objects.equal(fields, other.fields)
                && Objects.equal(installationId, other.installationId)
                && Objects.equal(deviceProperties, other.deviceProperties);
    }
}
