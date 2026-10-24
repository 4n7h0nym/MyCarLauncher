package com.openlauncher.app.data

import android.content.Context
import android.provider.ContactsContract
import com.openlauncher.app.model.PhoneContact

/**
 * Reads only raw contacts created by a Bluetooth/PBAP sync adapter. Android
 * does not expose the phone's PBAP client as a general third-party API; the
 * car unit/OEM must first sync the remote phone book into ContactsProvider.
 */
object BluetoothContactsRepository {
    fun phoneKey(number: String): String = number.filter { it.isDigit() || it == '+' }

    fun load(context: Context): List<PhoneContact> {
        val resolver = context.contentResolver
        val contacts = linkedMapOf<String, PhoneContact>()
        val rawProjection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            rawProjection,
            "${ContactsContract.RawContacts.DELETED}=0 AND ${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NOT NULL",
            null,
            null
        )?.use { rawCursor ->
            val rawIdColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts._ID)
            val contactIdColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
            val accountColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (rawCursor.moveToNext()) {
                if (rawIdColumn < 0 || contactIdColumn < 0 || accountColumn < 0) continue
                val accountType = rawCursor.getString(accountColumn)?.lowercase().orEmpty()
                if (!accountType.contains("bluetooth") && !accountType.contains("pbap")) continue

                val rawId = rawCursor.getString(rawIdColumn) ?: continue
                val dataProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.Data.DISPLAY_NAME
                )
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    dataProjection,
                    "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                    arrayOf(rawId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                    null
                )?.use { dataCursor ->
                    val numberColumn = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nameColumn = dataCursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    while (dataCursor.moveToNext()) {
                        val number = if (numberColumn >= 0) dataCursor.getString(numberColumn)?.trim().orEmpty() else ""
                        val name = if (nameColumn >= 0) dataCursor.getString(nameColumn)?.trim().orEmpty() else ""
                        if (number.isBlank() || name.isBlank()) continue
                        val key = phoneKey(number)
                        contacts.putIfAbsent(key, PhoneContact("$rawId:$key", name, number))
                    }
                }
            }
        }
        return contacts.values.sortedBy { it.name.lowercase() }
    }
}
