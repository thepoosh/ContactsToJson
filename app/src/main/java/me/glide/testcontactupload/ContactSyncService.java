package me.glide.testcontactupload;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class ContactSyncService extends IntentService {

    public static final String TAG = "ContactSyncService";

    public ContactSyncService() {
        super(TAG);
    }


    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startSync(Context context) {
        Intent intent = new Intent(context, ContactSyncService.class);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String[] selectionArgs = {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        };

        Cursor contacts = null;
        ArrayList<Contact> contactArrayList = new ArrayList<Contact>();
        try {
            contacts = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, selectionArgs, null, null, null);
            if (contacts != null && contacts.moveToFirst()) {
                int lookupKeyIndex = contacts.getColumnIndex(selectionArgs[0]);
                int hasNumber = contacts.getColumnIndex(selectionArgs[1]);
                int name = contacts.getColumnIndex(selectionArgs[2]);
                while (!contacts.isAfterLast()) {
                    Contact contact = new Contact();
                    contact.name = contacts.getString(name);
                    if (contacts.getInt(hasNumber) > 0) {
                        getPhoneNumbers(contact, contacts.getString(lookupKeyIndex));
                    }
                    getEmails(contact, contacts.getString(lookupKeyIndex));
                    contactArrayList.add(contact);
                    contacts.moveToNext();
                }
            }
        }finally {
            if (contacts!=null) contacts.close();
        }

        for (Contact contact : contactArrayList) {
            try {
                Log.d(TAG, contact.toJson().toString(2));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void getEmails(Contact contact, String lookupId) {
        String[] selectionArgs = {ContactsContract.CommonDataKinds.Email.ADDRESS};
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    selectionArgs,
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                    new String[]{lookupId},
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    contact.emails.add(cursor.getString(0));
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void getPhoneNumbers(Contact contact, String lookupId) {
        String[] selectionArgs = {
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    selectionArgs,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{lookupId},
                    null);

            if (cursor != null && cursor.moveToFirst()) {
                int normalizedNumberIndex = cursor.getColumnIndex(selectionArgs[0]);
                int numberIndex = cursor.getColumnIndex(selectionArgs[1]);
                while (!cursor.isAfterLast()) {
                    String num = cursor.getString(normalizedNumberIndex);
                    if (TextUtils.isEmpty(num)) {
                        num = cursor.getString(numberIndex);
                    }
                    contact.numbers.add(num);
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static class Contact {
        String name;
        List<String> numbers;
        List<String> emails;

        public Contact() {
            numbers = new ArrayList<String>();
            emails = new ArrayList<String>();
        }


        public JSONObject toJson() throws JSONException {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", name);
            if (!numbers.isEmpty()) {
                jsonObject.put("numbers", new JSONArray(numbers));
            }
            if (!emails.isEmpty()) {
                jsonObject.put("emails", new JSONArray(emails));
            }
            return jsonObject;
        }

    }

}
