package com.wmods.wppenhacer.utils

/** Exact filter-only wrappers verified in WA 2.26.46.82. No broad parent removal. */
object HomeFilterLayoutPolicy {
    val wrapperNames: Set<String> = setOf(
        "conversations_swipe_to_reveal_container",
        "conversations_swipe_to_reveal_header_combo"
    )
    fun shouldCollapse(name: String, filtersEnabled: Boolean) =
        !filtersEnabled && name in wrapperNames
}
