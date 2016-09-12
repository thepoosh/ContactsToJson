package me.glide.testcontactupload

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Environment
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.GZIPOutputStream

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 *
 *
 * helper methods.
 */
class ContactSyncService : IntentService(ContactSyncService.TAG) {

    override fun onHandleIntent(intent: Intent) {
        val now = System.currentTimeMillis()

        var contacts: Cursor? = null
        try {
            contacts = contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    PROJECTION,
                    SELECTION,
                    SELECTION_ARGS,
                    Data.CONTACT_ID)

            if (contacts != null && contacts.moveToFirst()) {
                // point to cursir for deserialization
                val contactIdIndex = contacts.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val typeIndex = contacts.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val dataIndex = contacts.getColumnIndex(ContactsContract.Data.DATA1)
                val normalizedNumberIndex = contacts.getColumnIndex(ContactsContract.Data.DATA4)
                val nameIndex = contacts.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)

                // initialize StringBuilder
                val size = contacts.count
                val sb = StringBuilder(size * 35)

                var currentContactId = ""
                var contact: Contact? = null

                while (!contacts.isAfterLast) {
                    val contactId = contacts.getString(contactIdIndex)
                    val isNewContact = currentContactId != contactId
                    if (isNewContact) {
                        if (contact != null) {
                            sb.append(GSON.toJson(contact)).append('\n')
                        }
                        contact = Contact()
                        contact.name = contacts.getString(nameIndex)
                        currentContactId = contactId
                    }

                    val type = contacts.getString(typeIndex)
                    if (type == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                        var num = contacts.getString(normalizedNumberIndex)
                        if (TextUtils.isEmpty(num)) {
                            num = contacts.getString(dataIndex)
                        }
                        if (!TextUtils.isEmpty(num)) {
                            contact!!.numbers.add(num)
                        }
                    } else if (type == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
                        contact!!.emails.add(contacts.getString(dataIndex))
                    }

                    contacts.moveToNext()
                }


                try {
                    writeGzippedData(sb)
                } catch (e: IOException) {
                    Log.e(TAG, e.message, e)
                }

            }
        } finally {
            if (contacts != null && !contacts.isClosed) {
                contacts.close()
            }
        }

        Log.wtf(TAG, "this took: " + (System.currentTimeMillis() - now) + " millis to run")
    }

    private fun writeGzippedData(sb: StringBuilder) {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val gzipContacts = File(path, FILE_NAME)
        val zip = GZIPOutputStream(FileOutputStream(gzipContacts))
        try {
            zip.write(sb.toString().toByteArray())
            zip.finish()
        } finally {
            zip.close()
        }

    }

    private class Contact {
        internal var name: String? = null

        internal var numbers: MutableList<String>
        internal var emails: MutableList<String>

        init {
            numbers = ArrayList<String>()
            emails = ArrayList<String>()
        }

    }

    companion object {

        val TAG = "ContactSyncService"

        val PROJECTION = arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA4, Data.DISPLAY_NAME)
        val SELECTION_ARGS = arrayOf(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        val SELECTION = Data.MIMETYPE + "=? OR " + Data.MIMETYPE + "=?"
        val FILE_NAME = "contacts.gz"
        val GSON = Gson()

        fun startSync(context: Context) {
            val intent = Intent(context, ContactSyncService::class.java)
            context.startService(intent)
        }
    }

}
