package siftscience.android;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class AccountKey {
    @SerializedName(value = "account_id", alternate = {"accountId"})
    public final String accountId;

    /**
     * Your beacon key; defaults to null.
     */
    @SerializedName(value = "beacon_key", alternate = {"beaconKey"})
    public final String beaconKey;

    public AccountKey(String accountId, String beaconKey) {
        this.accountId = accountId;
        this.beaconKey = beaconKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountKey that = (AccountKey) o;
        return Objects.equals(accountId, that.accountId) && Objects.equals(beaconKey, that.beaconKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, beaconKey);
    }
}
