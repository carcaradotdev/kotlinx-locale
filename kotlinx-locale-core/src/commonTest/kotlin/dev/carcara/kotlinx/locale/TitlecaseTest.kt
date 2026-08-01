package dev.carcara.kotlinx.locale

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The transform CLDR's `contextTransforms` asks for, and the one language pair
 * where the obvious implementation is wrong.
 */
@OptIn(InternalKotlinxLocaleApi::class)
class TitlecaseTest {

    @Test
    fun titleCasesTheFirstWordAndLeavesTheRest() {
        assertEquals("Čeština", titlecaseFirstWord("čeština", "cs"))
        assertEquals("Hrvatski jezik", titlecaseFirstWord("hrvatski jezik", "hr"))
        assertEquals("", titlecaseFirstWord("", "cs"))
        assertEquals("Already", titlecaseFirstWord("Already", "en"))
    }

    @Test
    fun turkishAndAzerbaijaniCapitalizeADottedI() {
        // Kotlin's titlecaseChar is locale-invariant and maps i to I, which is
        // wrong in exactly the two languages whose alphabet distinguishes them.
        assertEquals("İngilizce", titlecaseFirstWord("ingilizce", "tr"))
        assertEquals("İyun", titlecaseFirstWord("iyun", "az"))
        assertEquals("Ingilizce", titlecaseFirstWord("ingilizce", "en"))
    }
}
