package com.personalai.assistant.integrations.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.personalai.assistant.permissions.JarvisPermissionManager

data class ContactMatch(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

/**
 * Executive Phonebook & Contacts Agent.
 *
 * Resolves natural spoken names (Hindi/English) into real phone numbers
 * and contact entries without external cloud dependencies.
 */
class ContactsAgent(private val context: Context) {

    companion object {
        private const val TAG = "ContactsAgent"
    }

    /**
     * Searches the local phone contacts by name.
     */
    fun findContactByName(query: String): List<ContactMatch> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot search contacts: READ_CONTACTS permission not granted.")
            return emptyList()
        }

        JarvisPermissionManager.getInstance(context).recordPermissionAccess("contacts")
        val results = mutableListOf<ContactMatch>()

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$trimmed%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIdx != -1) cursor.getString(idIdx) else ""
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                    val number = if (numIdx != -1) cursor.getString(numIdx) else ""

                    if (name.isNotBlank() && number.isNotBlank()) {
                        results.add(ContactMatch(id = id, name = name, phoneNumber = number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts for '$query'", e)
        }

        return results
    }
}
