package com.openlauncher.app.data

import android.content.Context
import android.net.Uri
import android.bluetooth.BluetoothManager
import android.database.Cursor
import android.provider.ContactsContract
import com.openlauncher.app.model.PhoneContact

object BluetoothContactsRepository {
    private val pbapUris = listOf(
        Uri.parse("content://bt_contacts/contacts"),
        Uri.parse("content://com.android.bluetooth.pbap/contacts"),
        Uri.parse("content://bluetooth/contacts")
    )

    fun phoneKey(number: String): String = number.filter { it.isDigit() || it == '+' }

    fun load(context: Context): List<PhoneContact> {
        val jancarContacts = JancarBluetoothManager.contacts.value
        if (jancarContacts.isNotEmpty()) return mergeContacts(emptyList(), jancarContacts)

        val providerContacts = linkedMapOf<String, PhoneContact>()
        pbapUris.forEachIndexed { providerIndex, uri ->
            val authority = uri.authority ?: return@forEachIndexed
            val providerExists = runCatching {
                context.packageManager.resolveContentProvider(authority, 0) != null
            }.getOrDefault(false)
            if (!providerExists) return@forEachIndexed
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    readProviderCursor(cursor, providerIndex).forEach { contact ->
                        val key = phoneKey(contact.phoneNumber)
                        if (key.isNotBlank() && key !in providerContacts) providerContacts[key] = contact
                    }
                }
            }
        }
        if (providerContacts.isNotEmpty()) return mergeContacts(jancarContacts, providerContacts.values.toList())

        val syncedContacts = runCatching { loadSyncedContacts(context) }.getOrDefault(emptyList())
        if (syncedContacts.isNotEmpty()) return mergeContacts(jancarContacts, syncedContacts)

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
        val photoCol = column("photo_uri", "photo", "thumbnail")
        val numberCols = listOf("number", "phone_number", "phone", "data1", "tel", "phonenumber", "mobile")
            .mapNotNull { alias -> column(alias).takeIf { it >= 0 } }.distinct()
        if ((nameCol < 0 && firstCol < 0) || numberCols.isEmpty()) return emptyList()

        val result = linkedMapOf<String, PhoneContact>()
        while (cursor.moveToNext()) {
            val name = (if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "")
                .ifBlank { listOf(firstCol, lastCol).filter { it >= 0 }.joinToString(" ") { cursor.getString(it).orEmpty() }.trim() }
                .trim()
            if (name.isBlank()) continue
            val photoUri = if (photoCol >= 0) cursor.getString(photoCol) else null
            numberCols.forEach { index ->
                val number = cursor.getString(index)?.trim().orEmpty()
                val key = phoneKey(number)
                if (number.isNotBlank() && key.isNotBlank()) {
                    if (key !in result) result[key] = PhoneContact("pbap:$providerIndex:$key", name, number, photoUri)
                }
            }
        }
        return result.values.toList()
    }

    private fun loadSyncedContacts(context: Context): List<PhoneContact> {
        val resolver = context.contentResolver
        val contacts = linkedMapOf<String, PhoneContact>()
        val pairedDeviceNames = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
                ?.flatMap { device -> listOfNotNull(device.name, device.address) }
                ?.map(::normalizeAccountName)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty()
        } catch (_: SecurityException) {
            emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        val rawProjection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME
        )

        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            rawProjection,
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
                val isHeadUnitLocalMirror = pairedDeviceNames.isNotEmpty() &&
                    (accountType.isBlank() || accountType.contains("local") || accountType.contains("device"))
                if (!isBluetoothSyncAccount && !isPairedHandsetAccount && !isHeadUnitLocalMirror) continue

                val rawId = rawCursor.getString(rawIdColumn) ?: continue
                val dataProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
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
                    val photoCol = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                        .takeIf { it >= 0 } ?: dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                    while (dataCursor.moveToNext()) {
                        val number = if (numberColumn >= 0) dataCursor.getString(numberColumn)?.trim().orEmpty() else ""
                        val name = if (nameColumn >= 0) dataCursor.getString(nameColumn)?.trim().orEmpty() else ""
                        val photoUri = if (photoCol >= 0) dataCursor.getString(photoCol) else null
                        if (number.isBlank() || name.isBlank()) continue
                        val key = phoneKey(number)
                        if (!contacts.containsKey(key)) {
                            contacts[key] = PhoneContact("$rawId:$key", name, number, photoUri)
                        }
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
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
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
            val photoCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                .takeIf { it >= 0 } ?: cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            while (cursor.moveToNext()) {
                if (nameCol < 0 || numberCol < 0) continue
                val name = cursor.getString(nameCol)?.trim().orEmpty()
                val number = cursor.getString(numberCol)?.trim().orEmpty()
                val photoUri = if (photoCol >= 0) cursor.getString(photoCol) else null
                val key = phoneKey(number)
                if (name.isNotBlank() && key.isNotBlank()) {
                    val id = if (idCol >= 0) cursor.getString(idCol).orEmpty() else key
                    if (!contacts.containsKey(key)) {
                        contacts[key] = PhoneContact("local:$id:$key", name, number, photoUri)
                    }
                }
            }
        }
        return contacts.values.sortedBy { it.name.lowercase() }
    }

    private fun normalizeAccountName(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }
}
