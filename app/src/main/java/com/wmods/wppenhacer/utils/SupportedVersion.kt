package com.wmods.wppenhacer.utils

/** Branch rules end in .xx; other entries identify one exact host version. */
object SupportedVersion {
    fun matches(version: String?, rules: List<String>): Boolean =
        !version.isNullOrBlank() && rules.any { rule ->
            if (rule.endsWith(".xx")) version.startsWith(rule.removeSuffix("xx"))
            else version == rule
        }

    fun displayList(rules: List<String>): String {
        val (fullSeries, specificBuilds) = rules.partition { it.endsWith(".xx") }
        val rows = specificBuilds.groupBy { it.substringBeforeLast('.') }.map { (series, builds) ->
            val buildNumbers = builds.joinToString(", ") { it.substringAfterLast('.') }
            "$series.xx (build $buildNumbers)"
        }
        return (listOf(fullSeries.joinToString(", ")).filter { it.isNotEmpty() } + rows)
            .joinToString("\n")
    }
}
