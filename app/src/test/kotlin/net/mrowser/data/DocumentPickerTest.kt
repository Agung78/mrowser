package net.mrowser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPickerTest {

    @Test fun `nothing resolving is no picker`() {
        assertFalse(DocumentPicker.isUsable(null))
    }

    @Test fun `the Android TV framework stub is no picker`() {
        assertFalse(DocumentPicker.isUsable("com.android.tv.frameworkpackagestubs"))
    }

    @Test fun `DocumentsUI is a picker`() {
        assertTrue(DocumentPicker.isUsable("com.google.android.documentsui"))
        assertTrue(DocumentPicker.isUsable("com.android.documentsui"))
    }

    @Test fun `a third-party file manager answering the intent is a picker`() {
        assertTrue(DocumentPicker.isUsable("com.example.files"))
    }
}
