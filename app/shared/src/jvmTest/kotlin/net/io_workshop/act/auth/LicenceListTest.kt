package net.io_workshop.act.auth

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LicenceListTest {
    private fun library(id: String, name: String, version: String) = """
        {"uniqueId": "$id", "artifactVersion": "$version", "name": "$name", "licenses": ["Apache-2.0"]}
    """.trimIndent()

    @Test
    fun no_two_lines_of_the_committed_licence_list_are_equal() {
        val json = File("src/commonMain/composeResources/files/aboutlibraries.json").readText()
        val lines = licenceLines(json).map { it.text }
        assertTrue(lines.isNotEmpty())
        assertEquals(lines.distinct(), lines)
    }

    @Test
    fun artifacts_with_the_same_name_and_version_show_once_but_a_different_version_stays() {
        val json = """{"libraries": [
            ${library("a:collections", "collections", "1.5.0")},
            ${library("b:collections-jvm", "collections", "1.5.0")},
            ${library("a:collections-new", "collections", "1.10.0")}
        ], "licenses": {"Apache-2.0": {"hashCode": "x", "name": "Apache-2.0", "url": ""}}}"""
        val lines = licenceLines(json).map { it.text }
        assertEquals(
            listOf("collections 1.10.0 — Apache-2.0", "collections 1.5.0 — Apache-2.0").sorted(),
            lines.sorted(),
        )
    }
}
