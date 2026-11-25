package uz.medclinic.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NameNormalizerTest {
    @ParameterizedTest
    @CsvSource(
        "Тошматов, toshmatov",
        "Toshmatov, toshmatov",
        "TOSHMATOV , toshmatov",
        "Ўткир, otkir",
        "O'tkir, otkir",
        "Oʻtkir, otkir",
        "O`tkir, otkir",
        "Ғайратов, gayratov",
        "Gʻayratov, gayratov",
        "Хасанов, xasanov",
        "Khasanov, xasanov",
        "Ҳамидова, hamidova",
        "Шукуров, shukurov",
        "Чориев, choriev",
        "Қодиров, qodirov",
        "Ёқубов, yoqubov",
        "Юлдашева, yuldasheva",
        "Ахмедова-Каримова, axmedova karimova",
        "Иванов  Пётр, ivanov pyotr",
    )
    fun `latin, cyrillic and apostrophe variants become one form`(raw: String, expected: String) {
        assertThat(NameNormalizer.normalise(raw)).isEqualTo(expected)
    }

    @org.junit.jupiter.api.Test
    fun `full name joins non empty parts`() {
        assertThat(NameNormalizer.fullName("Каримова", "Дилноза", null)).isEqualTo("karimova dilnoza")
        assertThat(NameNormalizer.fullName(" ", "Ali", "Valiyevich")).isEqualTo("ali valiyevich")
        assertThat(NameNormalizer.normalise(null)).isEmpty()
    }
}
