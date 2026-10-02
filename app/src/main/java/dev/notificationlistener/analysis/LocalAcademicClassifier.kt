package dev.notificationlistener.analysis

import java.util.Locale

data class LocalClassification(val category: String, val score: Double, val matchedTerms: Set<String>)

object LocalAcademicClassifier {
    const val UPLOAD_THRESHOLD = 0.35

    private val patterns = linkedMapOf(
        "EVENT_INFORMATION" to setOf("ct", "class test", "quiz", "midterm", "final", "exam", "পরীক্ষা"),
        "DEADLINE" to setOf("deadline", "submission", "submit", "due", "জমা"),
        "LAB_INFORMATION" to setOf("lab", "lab report", "lab final", "experiment", "ল্যাব"),
        "CLASS_INFORMATION" to setOf("class", "room", "faculty", "teacher", "sir", "ma'am", "cancelled", "rescheduled"),
        "PROGRAMMING_PROBLEM" to setOf("error", "exception", "bug", "code", "compile", "runtime", "debug", "algorithm"),
        "RESOURCE" to setOf("slide", "slides", "pdf", "github", "drive.google", "tutorial", "lecture", "notes"),
        "COURSE_QUESTION" to setOf("why", "how", "what", "explain", "solve", "problem", "bujhi nai", "কেন", "কিভাবে")
    )
    private val csTerms = setOf(
        "cse", "computer science", "data structure", "algorithm", "database", "dbms", "network",
        "operating system", "compiler", "machine learning", "artificial intelligence", "oop",
        "java", "python", "c++", "dijkstra", "bfs", "dfs", "sql", "cache", "memory", "cpu"
    )
    private val questionMarkers = setOf("?", "why", "how", "what", "kivabe", "ken", "কেন", "কিভাবে")

    fun classify(text: String, courseHints: Collection<String> = emptyList()): LocalClassification {
        val normalized = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return LocalClassification("IRRELEVANT", 0.0, emptySet())

        val matches = patterns.mapValues { (_, terms) -> terms.filterTo(mutableSetOf()) { containsTerm(normalized, it) } }
        val category = matches.maxByOrNull { it.value.size }?.takeIf { it.value.isNotEmpty() }?.key
            ?: "IRRELEVANT"
        val categoryTerms = matches[category].orEmpty()
        val csMatches = csTerms.filterTo(mutableSetOf()) { containsTerm(normalized, it) }
        val hintMatches = courseHints.map(String::trim).filter(String::isNotBlank)
            .filterTo(mutableSetOf()) { containsTerm(normalized, it.lowercase(Locale.ROOT)) }
        val isQuestion = questionMarkers.any { containsTerm(normalized, it) }

        var score = 0.0
        if (category != "IRRELEVANT") score += 0.35
        score += (categoryTerms.size.coerceAtMost(2) * 0.15)
        if (csMatches.isNotEmpty()) score += 0.25
        if (hintMatches.isNotEmpty()) score += 0.25
        if (isQuestion && (csMatches.isNotEmpty() || hintMatches.isNotEmpty())) score += 0.15
        if (Regex("\\b(cse|swe|ice|eee)\\s*[- ]?\\d{3,4}\\b").containsMatchIn(normalized)) score += 0.25

        val finalCategory = when {
            category == "IRRELEVANT" && isQuestion && (csMatches.isNotEmpty() || hintMatches.isNotEmpty()) -> "COURSE_QUESTION"
            else -> category
        }
        return LocalClassification(
            finalCategory,
            score.coerceIn(0.0, 1.0),
            categoryTerms + csMatches + hintMatches
        )
    }

    private fun containsTerm(text: String, term: String): Boolean {
        if (term.any { !it.isLetterOrDigit() && !it.isWhitespace() }) return text.contains(term)
        return Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])")
            .containsMatchIn(text)
    }
}
