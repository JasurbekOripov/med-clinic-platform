package uz.medclinic.vendor.identity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import uz.medclinic.platform.security.LoginThrottle
import uz.medclinic.platform.domain.DomainException
import uz.medclinic.vendor.support.MutableClock
import java.time.Duration

class LoginThrottleTest {
    private val clock = MutableClock()
    private val throttle = LoginThrottle(clock)

    @Test
    fun `blocks a login after five failures and releases it after the lockout`() {
        repeat(5) { throttle.recordFailure("Owner", "10.0.0.1") }

        assertThatThrownBy { throttle.checkAllowed("owner", "10.0.0.2") }
            .isInstanceOfSatisfying(DomainException::class.java) {
                assertThat(it.code).isEqualTo("auth.too_many_attempts")
                assertThat(it.params["retryAfterSeconds"]).isEqualTo(900L)
            }

        clock.advance(Duration.ofMinutes(15).plusSeconds(1))
        assertThatCode { throttle.checkAllowed("owner", "10.0.0.2") }.doesNotThrowAnyException()
    }

    @Test
    fun `failures outside the window do not accumulate`() {
        repeat(4) { throttle.recordFailure("owner", null) }
        clock.advance(Duration.ofMinutes(16))
        throttle.recordFailure("owner", null)

        assertThatCode { throttle.checkAllowed("owner", null) }.doesNotThrowAnyException()
    }

    @Test
    fun `success resets the login counter`() {
        repeat(4) { throttle.recordFailure("owner", null) }
        throttle.recordSuccess("owner")
        throttle.recordFailure("owner", null)

        assertThatCode { throttle.checkAllowed("owner", null) }.doesNotThrowAnyException()
    }

    @Test
    fun `blocks an ip spraying many logins`() {
        repeat(20) { throttle.recordFailure("user$it", "10.0.0.9") }

        assertThatThrownBy { throttle.checkAllowed("someone-else", "10.0.0.9") }.isInstanceOf(DomainException::class.java)
        assertThatCode { throttle.checkAllowed("someone-else", "10.0.0.10") }.doesNotThrowAnyException()
    }
}
