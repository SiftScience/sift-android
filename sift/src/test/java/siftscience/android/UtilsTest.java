package siftscience.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;


public class UtilsTest {

    @Test
    public void testListEqualReturnsTrueWhenBothListsAreNull() {
        List<AccountKey> list1 = null;
        List<AccountKey> list2 = null;
        assertTrue(Utils.equals(list1, list2));
    }

    @Test
    public void testListEqualReturnsFalseWhenOnlyOneListIsNull() {
        List<AccountKey> list1 = null;
        List<AccountKey> list2 = new ArrayList<>();
        assertFalse(Utils.equals(list1, list2));
    }

    @Test
    public void testListEqualReturnsTrueWhenBothListsAreEmpty() {
        List<AccountKey> list1 = new ArrayList<>();
        List<AccountKey> list2 = new ArrayList<>();
        assertTrue(Utils.equals(list1, list2));
    }

    @Test
    public void testListEqualReturnsTrueWhenListsAreEqual() {
        List<AccountKey> list1 = new ArrayList<>();
        list1.add(new AccountKey("ACCOUNT_ID_1", "BEACON_KEY_1"));
        list1.add(new AccountKey("ACCOUNT_ID_2", "BEACON_KEY_2"));
        list1.add(new AccountKey("ACCOUNT_ID_3", "BEACON_KEY_3"));

        List<AccountKey> list2 = new ArrayList<>();
        list2.add(new AccountKey("ACCOUNT_ID_1", "BEACON_KEY_1"));
        list2.add(new AccountKey("ACCOUNT_ID_2", "BEACON_KEY_2"));
        list2.add(new AccountKey("ACCOUNT_ID_3", "BEACON_KEY_3"));
        assertTrue(Utils.equals(list1, list2));
    }


    @Test
    public void testListEqualReturnsFalseWhenListsAreNotEqual() {
        List<AccountKey> list1 = new ArrayList<>();
        list1.add(new AccountKey("ACCOUNT_ID_1", "BEACON_KEY_1"));
        list1.add(new AccountKey("ACCOUNT_ID_2", "BEACON_KEY_2"));
        list1.add(new AccountKey("ACCOUNT_ID_3", "BEACON_KEY_3"));

        List<AccountKey> list2 = new ArrayList<>();
        list2.add(new AccountKey("ACCOUNT_ID_1", "BEACON_KEY_1"));
        list2.add(new AccountKey("ACCOUNT_ID_2", "BEACON_KEY_2"));
        list2.add(new AccountKey("ACCOUNT_ID_5", "BEACON_KEY_5"));
        assertFalse(Utils.equals(list1, list2));
    }

}
