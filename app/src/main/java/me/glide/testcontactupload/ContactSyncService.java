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

import com.google.gson.Gson;

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

    public static final String[] PROJECTION = new String[]{
            Data.CONTACT_ID,
            Data.MIMETYPE,
            Data.DATA1,
            Data.DATA4,
            Data.DISPLAY_NAME
    };
    public static final String[] SELECTION_ARGS = new String[]{
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
    };
    public static final String SELECTION = Data.MIMETYPE + "=? OR " + Data.MIMETYPE + "=?";
    public static final String FILE_NAME = "contacts.gz";
    public static final Gson GSON = new Gson();

    public ContactSyncService() {
        super(TAG);
    }

    public static void startSync(Context context) {
        Intent intent = new Intent(context, ContactSyncService.class);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final long now = System.currentTimeMillis();

        Cursor contacts = null;
        try {
            contacts = getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI,
                    PROJECTION,
                    SELECTION,
                    SELECTION_ARGS,
                    Data.CONTACT_ID);

            if (contacts != null && contacts.moveToFirst()) {
                // point to cursir for deserialization
                final int contactIdIndex = contacts.getColumnIndex(ContactsContract.Data.CONTACT_ID);
                final int typeIndex = contacts.getColumnIndex(ContactsContract.Data.MIMETYPE);
                final int dataIndex = contacts.getColumnIndex(ContactsContract.Data.DATA1);
                final int normalizedNumberIndex = contacts.getColumnIndex(ContactsContract.Data.DATA4);
                final  int nameIndex = contacts.getColumnIndex(ContactsContract.Data.DISPLAY_NAME);

                // initialize StringBuilder
                final int size = contacts.getCount();
                final StringBuilder sb = new StringBuilder(size * 35);

                String currentContactId = "";
                Contact contact = null;

                while (!contacts.isAfterLast()) {
                    String contactId = contacts.getString(contactIdIndex);
                    boolean isNewContact = !currentContactId.equals(contactId);
                    if (isNewContact) {
                        if (contact != null) {
                            sb.append(GSON.toJson(contact)).append('\n');
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
                    writeGzippedData(sb);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }finally {
            if (contacts!=null && !contacts.isClosed()) {
                contacts.close();
            }
        }

        Log.wtf(TAG, "this took: " + (System.currentTimeMillis() - now) + " millis to run");
    }

    private void writeGzippedData(StringBuilder sb) throws IOException {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File gzipContacts = new File(path, FILE_NAME);
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

    }

}
