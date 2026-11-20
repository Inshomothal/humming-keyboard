package com.humming.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceEditAndroidTest {
    @Test
    fun ordinaryDictationCanInitializeCommandMatching() {
        assertNull(VoiceEdit.voiceCommand("Existing text", "this is an ordinary sentence"))
    }

    @Test
    fun unicodeCaseAndWholeWordMatchingRemainSupported() {
        assertEquals(
            "BISTRO is open",
            VoiceEdit.voiceCommand("CAF\u00C9 is open", "change caf\u00E9 to bistro")?.outcome?.text,
        )
        assertNull(VoiceEdit.voiceCommand("caf\u00E9teria is open", "delete caf\u00E9"))
    }

    @Test
    fun unicodeWhitespaceRemainsSupported() {
        assertEquals(
            "bistro is open",
            VoiceEdit.voiceCommand(
                "caf\u00E9 is open",
                "change\u00A0caf\u00E9\u00A0to\u00A0bistro",
            )?.outcome?.text,
        )
    }
}
