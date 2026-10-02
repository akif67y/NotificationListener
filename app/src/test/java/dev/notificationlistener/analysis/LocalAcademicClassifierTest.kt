package dev.notificationlistener.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAcademicClassifierTest {
    @Test
    fun `detects CT information`() {
        val result = LocalAcademicClassifier.classify("CSE 221 CT tomorrow at 11")
        assertEquals("EVENT_INFORMATION", result.category)
        assertTrue(result.score >= LocalAcademicClassifier.UPLOAD_THRESHOLD)
    }

    @Test
    fun `detects CS course question`() {
        val result = LocalAcademicClassifier.classify("Why does Dijkstra fail with negative edges?")
        assertEquals("COURSE_QUESTION", result.category)
        assertTrue(result.score >= LocalAcademicClassifier.UPLOAD_THRESHOLD)
    }

    @Test
    fun `course alias makes a question relevant`() {
        val result = LocalAcademicClassifier.classify("How do I solve graph 3?", listOf("graph"))
        assertEquals("COURSE_QUESTION", result.category)
        assertTrue(result.score >= LocalAcademicClassifier.UPLOAD_THRESHOLD)
    }

    @Test
    fun `ordinary social chat is ignored`() {
        val result = LocalAcademicClassifier.classify("Let's have tea after lunch")
        assertEquals("IRRELEVANT", result.category)
        assertTrue(result.score < LocalAcademicClassifier.UPLOAD_THRESHOLD)
    }
}
