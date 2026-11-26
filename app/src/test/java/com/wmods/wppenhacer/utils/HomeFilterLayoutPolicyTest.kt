package com.wmods.wppenhacer.utils

import org.junit.Assert.*
import org.junit.Test

class HomeFilterLayoutPolicyTest {
    @Test fun collapsesBothHiddenFilterWrappers() {
        for (name in HomeFilterLayoutPolicy.wrapperNames)
            assertTrue(HomeFilterLayoutPolicy.shouldCollapse(name, false))
    }
    @Test fun leavesEnabledFilterLayoutUntouched() {
        for (name in HomeFilterLayoutPolicy.wrapperNames)
            assertFalse(HomeFilterLayoutPolicy.shouldCollapse(name, true))
    }
    @Test fun preservesSearchArchiveAndChatRows() {
        for (name in listOf("my_search_bar", "list", "conversations_archive_header",
                "contact_row_container", "conversation_container"))
            assertFalse(HomeFilterLayoutPolicy.shouldCollapse(name, false))
    }
}
