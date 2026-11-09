package com.samajhsakhi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardsAndModelsTest {

    @Test fun everyCardHasTextInAllThreeLanguages() {
        for (c in Cards.offlineSet) {
            assertTrue(c.id, c.en.isNotBlank() && c.hi.isNotBlank() && c.mr.isNotBlank())
        }
        assertEquals(20, Cards.all.size)
    }

    @Test fun marathiCardUsesMarathiWording() {
        val water = Cards.all.first { it.id == "water" }
        assertEquals("मला पाणी हवे आहे.", water.text("mr-IN"))
        assertEquals("I need water.", water.text("en-IN"))
        assertEquals(water.hi, water.text("hi-en"))
    }

    @Test fun newUserSeesDefaultFourCards() {
        assertEquals(listOf("water", "pain", "help", "medicine"), Cards.homeFor("", 15).map { it.id })
    }

    @Test fun mostUsedCardsMoveToTheFront() {
        var use = ""
        repeat(5) { use = Cards.bump(use, "bathroom") }
        repeat(3) { use = Cards.bump(use, "wait") }
        val ids = Cards.homeFor(use, 15).map { it.id }
        assertEquals("bathroom", ids[0])
        assertEquals("wait", ids[1])
        assertEquals(4, ids.size)
    }

    @Test fun nightTimeNudgesSleepButNotAboveRealUsage() {
        assertTrue("sleep" in Cards.homeFor("", 23).map { it.id })
        var use = ""
        repeat(4) { use = Cards.bump(use, "family") }
        assertEquals("family", Cards.homeFor(use, 23)[0].id)
    }

    @Test fun usageEncodingRoundTripsAndIgnoresJunk() {
        val enc = Cards.bump(Cards.bump("", "water"), "water")
        assertEquals(mapOf("water" to 2), Cards.parseUse(enc))
        assertEquals(emptyMap<String, Int>(), Cards.parseUse("garbage,:,x:y"))
    }

    @Test fun outputLanguageDefaultsAndNoAnyLanguageOption() {
        assertEquals("mr-IN", AppSettings(inputLang = "mr-IN").effectiveOutLang)
        assertEquals("hi-IN", AppSettings(inputLang = "hi-en").effectiveOutLang)
        assertEquals("en-IN", AppSettings(inputLang = "mr-IN", outputLang = "en-IN").effectiveOutLang)
        assertFalse(Langs.input.any { it.code == "indic-auto" })
        assertTrue(Langs.output.map { it.code }.containsAll(listOf("mr-IN", "hi-IN", "en-IN")))
    }

    @Test fun speakStylesMatchServerCodes() {
        assertEquals(setOf("normal", "comfort", "urgent", "slow_clear"), SpeakStyle.entries.map { it.code }.toSet())
    }
}
