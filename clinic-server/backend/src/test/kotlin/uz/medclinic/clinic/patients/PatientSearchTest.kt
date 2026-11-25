package uz.medclinic.clinic.patients

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uz.medclinic.clinic.patients.domain.PatientSearch

class PatientSearchTest {
    @Test
    fun `one search box understands card numbers, phones and names in any script`() {
        assertThat(PatientSearch.parse(null)).isEqualTo(PatientSearch.Recent)
        assertThat(PatientSearch.parse("  ")).isEqualTo(PatientSearch.Recent)
        assertThat(PatientSearch.parse("123")).isEqualTo(PatientSearch.ByCard(123))
        assertThat(PatientSearch.parse("+998 90 123-45-67")).isEqualTo(PatientSearch.ByPhone("998901234567"))
        assertThat(PatientSearch.parse("4567890")).isEqualTo(PatientSearch.ByPhone("4567890"))
        assertThat(PatientSearch.parse("Тошматов Ўткир")).isEqualTo(PatientSearch.ByName("toshmatov otkir"))
    }
}
