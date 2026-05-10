package com.pbot.bot.domain.service.support

import com.pbot.bot.domain.model.ProcessNote
import com.pbot.bot.domain.model.Severity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProcessReportBuilderTest {

    @Test
    fun `returns null on empty notes so caller can skip posting`() {
        assertThat(ProcessReportBuilder.build(emptyList())).isNull()
    }

    @Test
    fun `renders header and footer`() {
        val md = ProcessReportBuilder.build(listOf(ProcessNote(Severity.MEDIUM, "x")))!!

        assertThat(md).contains("## 🐶 Pawranoid Process Review")
        assertThat(md).contains("*코드가 아닌 PR 자체에 대한 평가입니다.*")
    }

    @Test
    fun `renders each note with severity emoji and label`() {
        val md = ProcessReportBuilder.build(
            listOf(
                ProcessNote(Severity.HIGH, "PR 너무 큼"),
                ProcessNote(Severity.MEDIUM, "제목 추상적"),
                ProcessNote(Severity.LOW, "테스트 동반"),
            ),
        )!!

        assertThat(md).contains("- 🔴 **HIGH** — PR 너무 큼")
        assertThat(md).contains("- 🟡 **MEDIUM** — 제목 추상적")
        assertThat(md).contains("- 🟢 **LOW** — 테스트 동반")
    }

    @Test
    fun `orders notes by severity HIGH first then MEDIUM then LOW`() {
        val md = ProcessReportBuilder.build(
            listOf(
                ProcessNote(Severity.LOW, "low one"),
                ProcessNote(Severity.HIGH, "high one"),
                ProcessNote(Severity.MEDIUM, "med one"),
            ),
        )!!

        val highIdx = md.indexOf("high one")
        val medIdx = md.indexOf("med one")
        val lowIdx = md.indexOf("low one")
        assertThat(highIdx).isLessThan(medIdx)
        assertThat(medIdx).isLessThan(lowIdx)
    }
}
