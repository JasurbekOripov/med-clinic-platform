package uz.medclinic.vendor.identity

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.vendor.identity.domain.PasswordPolicy
import uz.medclinic.vendor.shared.domain.ValidationException

class PasswordPolicyTest {
    @Test
    fun `accepts 8 characters, rejects 7, blank and more than 72 bytes`() {
        assertThatCode { PasswordPolicy.validate("abcd1234") }.doesNotThrowAnyException()
        assertThatCode { PasswordPolicy.validate("пароль12") }.doesNotThrowAnyException()
        listOf("abc1234", "        ", "a".repeat(73), "ж".repeat(37)).forEach { bad ->
            assertThatThrownBy { PasswordPolicy.validate(bad) }.isInstanceOf(ValidationException::class.java)
        }
    }
}
