package uz.medclinic.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UzPhoneTest {
    @ParameterizedTest
    @ValueSource(strings = ["+998901234567", "998901234567", "901234567", "+998 (90) 123-45-67", " 90 123 45 67 "])
    fun `normalises accepted formats to E164`(raw: String) {
        assertThat(UzPhone.parse(raw)?.e164).isEqualTo("+998901234567")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "+7 901 234 56 78", "99890123456", "+9989012345678", "90123456a", "++998901234567", "8901234567"])
    fun `rejects invalid numbers`(raw: String) {
        assertThat(UzPhone.parse(raw)).isNull()
    }
}
