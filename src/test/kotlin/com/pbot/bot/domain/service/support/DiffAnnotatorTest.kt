package com.pbot.bot.domain.service.support

import com.pbot.bot.infrastructure.github.PullRequestFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiffAnnotatorTest {

    // --- annotatePatch ---

    @Test
    fun `annotatePatch handles null patch (binary file)`() {
        val file = PullRequestFile(path = "image.png", patch = null)

        val output = DiffAnnotator.annotatePatch(file)

        assertThat(output).contains("=== image.png ===")
        assertThat(output).contains("(binary or no patch)")
    }

    @Test
    fun `annotatePatch numbers added lines starting from hunk new-start`() {
        val file = PullRequestFile(
            path = "Foo.kt",
            patch = """
                @@ -10,2 +14,3 @@
                +added line one
                +added line two
            """.trimIndent(),
        )

        val output = DiffAnnotator.annotatePatch(file)

        assertThat(output).contains("L14")
        assertThat(output).contains("L15")
        assertThat(output).contains("[+] added line one")
        assertThat(output).contains("[+] added line two")
    }

    @Test
    fun `annotatePatch marks removed lines with L-- and does not advance counter`() {
        val file = PullRequestFile(
            path = "Foo.kt",
            patch = """
                @@ -10,3 +20,3 @@
                 context
                -removed line
                +added line
            """.trimIndent(),
        )

        val output = DiffAnnotator.annotatePatch(file)

        assertThat(output).contains("L20")
        assertThat(output).contains("L--")
        assertThat(output).contains("[-] removed line")
        assertThat(output).contains("L21")
        assertThat(output).contains("[+] added line")
    }

    @Test
    fun `annotatePatch numbers context lines (space-prefixed)`() {
        val file = PullRequestFile(
            path = "Foo.kt",
            patch = """
                @@ -10,3 +14,3 @@
                 context A
                 context B
                 context C
            """.trimIndent(),
        )

        val output = DiffAnnotator.annotatePatch(file)

        assertThat(output).contains("L14")
        assertThat(output).contains("L15")
        assertThat(output).contains("L16")
        assertThat(output).contains("context A")
        assertThat(output).doesNotContain("[+]")
        assertThat(output).doesNotContain("[-]")
    }

    @Test
    fun `annotatePatch handles multiple hunks resetting line counter`() {
        val file = PullRequestFile(
            path = "Foo.kt",
            patch = """
                @@ -1,1 +1,1 @@
                +first hunk added
                @@ -50,1 +60,1 @@
                +second hunk added
            """.trimIndent(),
        )

        val output = DiffAnnotator.annotatePatch(file)

        assertThat(output).contains("L1   ").contains("first hunk added")
        assertThat(output).contains("L60").contains("second hunk added")
    }

    @Test
    fun `annotatePatch ignores file headers (+++ and ---)`() {
        val file = PullRequestFile(
            path = "Foo.kt",
            patch = """
                --- a/Foo.kt
                +++ b/Foo.kt
                @@ -1,1 +1,1 @@
                +new content
            """.trimIndent(),
        )

        val output = DiffAnnotator.annotatePatch(file)

        // 파일 헤더는 출력에 라인 번호 prefix 없이 무시되어야 함
        assertThat(output).doesNotContain("L1   [+] ++ b/Foo.kt")
        assertThat(output).doesNotContain("L1   [-] -- a/Foo.kt")
        assertThat(output).contains("[+] new content")
    }

    // --- lineNumbersInDiff ---

    @Test
    fun `lineNumbersInDiff includes added and context lines but not removed`() {
        val patch = """
            @@ -10,3 +20,3 @@
             context
            -removed
            +added
        """.trimIndent()

        val lines = DiffAnnotator.lineNumbersInDiff(patch)

        assertThat(lines).containsExactlyInAnyOrder(20, 21)
    }

    @Test
    fun `lineNumbersInDiff handles multiple hunks`() {
        val patch = """
            @@ -1,1 +1,1 @@
            +line1
            @@ -50,1 +60,2 @@
             ctx
            +line61
        """.trimIndent()

        val lines = DiffAnnotator.lineNumbersInDiff(patch)

        assertThat(lines).containsExactlyInAnyOrder(1, 60, 61)
    }

    @Test
    fun `lineNumbersInDiff returns empty for patch without hunks`() {
        val patch = "no hunk header here"

        val lines = DiffAnnotator.lineNumbersInDiff(patch)

        assertThat(lines).isEmpty()
    }

    @Test
    fun `lineNumbersInDiff does not advance counter on empty or unknown lines`() {
        // hunk + 추가 라인 + 빈 줄 (trailing newline 가정) + 끝
        val patch = "@@ -1,1 +5,1 @@\n+only added\n"

        val lines = DiffAnnotator.lineNumbersInDiff(patch)

        // line 5만 있어야 하고 phantom line 6 같은 건 없어야 함
        assertThat(lines).containsExactly(5)
    }

    @Test
    fun `lineNumbersInDiff parses hunk header without count (single-line change)`() {
        // GitHub은 변경 라인 수 1인 경우 ",count" 생략 가능
        val patch = """
            @@ -10 +25 @@
            +single line
        """.trimIndent()

        val lines = DiffAnnotator.lineNumbersInDiff(patch)

        assertThat(lines).containsExactly(25)
    }
}
