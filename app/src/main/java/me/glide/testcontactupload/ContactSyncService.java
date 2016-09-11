package me.glide.testcontactupload;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

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
        final long now = System.currentTimeMillis();
        String[] selectionArgs = {
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data.MIMETYPE,
        ContactsContract.Data.DATA1,
        ContactsContract.Data.DATA4,
        ContactsContract.Data.DISPLAY_NAME
        };

        Cursor contacts = null;
        try {
            contacts = getContentResolver().query(ContactsContract.Data.CONTENT_URI, selectionArgs, Data.MIMETYPE + "=? OR " + Data.MIMETYPE + "=?",
                    new String[]{ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE},
                    Data.CONTACT_ID);
            ;
            if (contacts != null && contacts.moveToFirst()) {
                final int size = contacts.getCount();
                final StringBuilder sb = new StringBuilder(size * 35);
                String currentContactId = "";
                Contact contact = null;
                int contactIdIndex = contacts.getColumnIndex(ContactsContract.Data.CONTACT_ID);
                int typeIndex = contacts.getColumnIndex(ContactsContract.Data.MIMETYPE);
                int dataIndex = contacts.getColumnIndex(ContactsContract.Data.DATA1);
                int normalizedNumberIndex = contacts.getColumnIndex(ContactsContract.Data.DATA4);
                int nameIndex = contacts.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);
                while (!contacts.isAfterLast()) {
                    String contactId = contacts.getString(contactIdIndex);
                    boolean isNewContact = !currentContactId.equals(contactId);
                    if (isNewContact) {
                        if (contact != null) {
                            try {
                                sb.append(contact.toJson()).append('\n');
                            } catch (JSONException e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }
                        contact = new Contact();
                        contact.name = contacts.getString(nameIndex);
                        currentContactId = contactId;
                    }

                    String type = contacts.getString(typeIndex);
                    if (type.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                        String num = contacts.getString(normalizedNumberIndex);
                        if (TextUtils.isEmpty(num)) {
                            num = contacts.getString(dataIndex);
                        }
                        if (!TextUtils.isEmpty(num)) {
                            contact.numbers.add(num);
                        }
                    } else if (type.equals(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)){
                        contact.emails.add(contacts.getString(dataIndex));
                    }

                    contacts.moveToNext();
                }


                try {
                    writeGzipedData(sb);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }finally {
            if (contacts!=null) contacts.close();
        }

        Log.wtf(TAG, "this took: " + (System.currentTimeMillis() - now) + " millis to run");
    }

    private void writeGzipedData(StringBuilder sb) throws IOException {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File gzipContacts = new File(path, "contacts.gz");
        GZIPOutputStream zip = new GZIPOutputStream(new FileOutputStream(gzipContacts));
        try {
            zip.write(sb.toString().getBytes());
            zip.finish();
        } finally {
            zip.close();
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
