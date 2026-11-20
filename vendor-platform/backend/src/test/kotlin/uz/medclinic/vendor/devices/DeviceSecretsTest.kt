package uz.medclinic.vendor.devices

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uz.medclinic.vendor.devices.domain.DeviceSecrets

class DeviceSecretsTest {
    @Test
    fun `pairing codes are grouped Crockford base32 and normalise user typing`() {
        val code = DeviceSecrets.newPairingCode()

        assertThat(code).matches("^[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")
        assertThat(DeviceSecrets.normalisePairingCode(code.lowercase().replace("-", " "))).isEqualTo(code.replace("-", ""))
        assertThat(DeviceSecrets.normalisePairingCode("k7qm-2xrd-9wtO")).isEqualTo("K7QM2XRD9WT0")
        assertThat(DeviceSecrets.normalisePairingCode("AAAA-BBBB")).isNull()
        assertThat(DeviceSecrets.normalisePairingCode("AAAA-BBBB-CCC#")).isNull()
    }

    @Test
    fun `device tokens carry the prefix and 256 bits`() {
        val token = DeviceSecrets.newDeviceToken()

        assertThat(token).startsWith("dvt1_").hasSize(5 + 43)
        assertThat(DeviceSecrets.sha256(token)).hasSize(64).isNotEqualTo(DeviceSecrets.sha256(DeviceSecrets.newDeviceToken()))
    }
}
