package com.openlauncher.app.data

import android.content.Context
import android.net.Uri
import android.bluetooth.BluetoothManager
import android.database.Cursor
import android.provider.ContactsContract
import com.openlauncher.app.model.PhoneContact

/**
 * Reads the head unit's PBAP mirror, then Bluetooth sync-adapter contacts,
 * with the general contacts provider used only as a final compatibility path.
 * Vendor PBAP content URIs are OEM-specific and are not a public Android API.
 */
object BluetoothContactsRepository {
    private val pbapUris = listOf(
        Uri.parse("content://bt_contacts/contacts"),
        Uri.parse("content://com.android.bluetooth.pbap/contacts"),
        Uri.parse("content://bluetooth/contacts")
    )

    fun phoneKey(number: String): String = number.filter { it.isDigit() || it == '+' }

    fun load(context: Context): List<PhoneContact> {
        val jancarContacts = JancarBluetoothManager.contacts.value
        // Jancar already returns the PBAP phone book through its Binder API.
        // Avoid querying several OEM providers and the system ContactsProvider
        // once that authoritative list is available.
        if (jancarContacts.isNotEmpty()) return mergeContacts(emptyList(), jancarContacts)
        // Some automotive vendors expose the synchronized PBAP book through
        // their own ContentProvider. These providers are not part of Android's
        // public API, so each query is isolated and may safely fail.
        val providerContacts = linkedMapOf<String, PhoneContact>()
        pbapUris.forEachIndexed { providerIndex, uri ->
            val authority = uri.authority ?: return@forEachIndexed
            // Avoid ContentResolver's noisy "Failed to find provider info"
            // log on head units that do not implement a given OEM provider.
            val providerExists = runCatching {
                context.packageManager.resolveContentProvider(authority, 0) != null
            }.getOrDefault(false)
            if (!providerExists) return@forEachIndexed
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    readProviderCursor(cursor, providerIndex).forEach { contact ->
                        val key = phoneKey(contact.phoneNumber)
                        if (key.isNotBlank()) providerContacts.putIfAbsent(key, contact)
                    }
                }
            }
        }
        if (providerContacts.isNotEmpty()) return mergeContacts(jancarContacts, providerContacts.values.toList())

        // Next, use PBAP/Bluetooth-specific Android sync-adapter accounts.
        val syncedContacts = runCatching { loadSyncedContacts(context) }.getOrDefault(emptyList())
        if (syncedContacts.isNotEmpty()) return mergeContacts(jancarContacts, syncedContacts)

        // Last-resort compatibility for head units that mirror the phone book
        // into the normal contacts provider without marking its account type.
        val localContacts = runCatching { loadLocalFallback(context) }.getOrDefault(emptyList())
        return mergeContacts(jancarContacts, localContacts)
    }

    private fun mergeContacts(first: List<PhoneContact>, second: List<PhoneContact>): List<PhoneContact> =
        (first + second)
            .filter { phoneKey(it.phoneNumber).isNotBlank() }
            .distinctBy { phoneKey(it.phoneNumber) }
            .sortedBy { it.name.lowercase() }

    private fun readProviderCursor(cursor: Cursor, providerIndex: Int): List<PhoneContact> {
        fun column(vararg aliases: String): Int {
            val names = cursor.columnNames.mapIndexed { index, name -> name.lowercase() to index }.toMap()
            return aliases.firstNotNullOfOrNull { names[it.lowercase()] }
                ?: aliases.firstNotNullOfOrNull { alias -> names.entries.firstOrNull { it.key.endsWith(".$alias") || it.key.endsWith("_$alias") }?.value }
                ?: -1
        }
        val nameCol = column("display_name", "name", "contact_name", "displayname", "title")
        val firstCol = column("given_name", "first_name", "firstname")
        val lastCol = column("family_name", "last_name", "lastname", "surname")
        val numberCols = listOf("number", "phone_number", "phone", "data1", "tel", "phonenumber", "mobile")
            .mapNotNull { alias -> column(alias).takeIf { it >= 0 } }.distinct()
        if ((nameCol < 0 && firstCol < 0) || numberCols.isEmpty()) return emptyList()

        val result = linkedMapOf<String, PhoneContact>()
        while (cursor.moveToNext()) {
            val name = (if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "")
                .ifBlank { listOf(firstCol, lastCol).filter { it >= 0 }.joinToString(" ") { cursor.getString(it).orEmpty() }.trim() }
                .trim()
            if (name.isBlank()) continue
            numberCols.forEach { index ->
                val number = cursor.getString(index)?.trim().orEmpty()
                val key = phoneKey(number)
                if (number.isNotBlank() && key.isNotBlank()) {
                    result.putIfAbsent(key, PhoneContact("pbap:$providerIndex:$key", name, number))
                }
            }
        }
        return result.values.toList()
    }

    private fun loadSyncedContacts(context: Context): List<PhoneContact> {
        val resolver = context.contentResolver
        val contacts = linkedMapOf<String, PhoneContact>()
        // Several head units store PBAP rows in a local account (no account
        // type) named after the paired handset instead of using the AOSP PBAP
        // sync adapter. Match that account to a paired Bluetooth device while
        // continuing to exclude unrelated phone/cloud accounts.
        val pairedDeviceNames = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
                ?.flatMap { device -> listOfNotNull(device.name, device.address) }
                ?.map(::normalizeAccountName)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
        val rawProjection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME
        )

        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            rawProjection,
            // Some head units import PBAP contacts into an account with a null
            // account type. Keep those rows eligible for the paired-device
            // account-name check below.
            "${ContactsContract.RawContacts.DELETED}=0",
            null,
            null
        )?.use { rawCursor ->
            val rawIdColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts._ID)
            val contactIdColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
            val accountColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val accountNameColumn = rawCursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            while (rawCursor.moveToNext()) {
                if (rawIdColumn < 0 || contactIdColumn < 0 || accountColumn < 0 || accountNameColumn < 0) continue
                val accountType = rawCursor.getString(accountColumn)?.lowercase().orEmpty()
                val accountName = normalizeAccountName(rawCursor.getString(accountNameColumn).orEmpty())
                val isBluetoothSyncAccount = accountType.contains("bluetooth") || accountType.contains("pbap")
                val isPairedHandsetAccount = accountName.isNotBlank() &&
                    pairedDeviceNames.any { device -> accountName == device || accountName.contains(device) || device.contains(accountName) }
                // Some head units expose their PBAP mirror as generic local
                // ContactsProvider rows, with no account type or an OEM
                // "local/device" account name rather than the phone's name.
                // This launcher is used on a head unit with no local address
                // book, so accept those rows only when a phone is paired.
                val isHeadUnitLocalMirror = pairedDeviceNames.isNotEmpty() &&
                    (accountType.isBlank() || accountType.contains("local") || accountType.contains("device"))
                if (!isBluetoothSyncAccount && !isPairedHandsetAccount && !isHeadUnitLocalMirror) continue

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

    private fun loadLocalFallback(context: Context): List<PhoneContact> {
        val contacts = linkedMapOf<String, PhoneContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                if (nameCol < 0 || numberCol < 0) continue
                val name = cursor.getString(nameCol)?.trim().orEmpty()
                val number = cursor.getString(numberCol)?.trim().orEmpty()
                val key = phoneKey(number)
                if (name.isNotBlank() && key.isNotBlank()) {
                    val id = if (idCol >= 0) cursor.getString(idCol).orEmpty() else key
                    contacts.putIfAbsent(key, PhoneContact("local:$id:$key", name, number))
                }
            }
        }
        return contacts.values.sortedBy { it.name.lowercase() }
    }

    private fun normalizeAccountName(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}
