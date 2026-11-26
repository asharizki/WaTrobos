package com.wmods.wppenhacer.utils

import org.junit.Assert.*
import org.junit.Test

class ConversationMethodResolverTest {
    class Legacy { fun oldList(): List<Any> = emptyList() }
    open class Migrated { fun A31(): List<Any> = emptyList(); fun BGF(): List<Any> = emptyList() }
    class Current : Migrated()
    class Ambiguous { fun first(): List<Any> = emptyList(); fun other(): List<Any> = emptyList() }
    @Test fun preservesLegacyGetter() {
        assertEquals("oldList", ConversationMethodResolver.chatList(Legacy::class.java)?.name)
    }
    @Test fun selectsMigratedGetterNotUnrelatedList() {
        val method = ConversationMethodResolver.chatList(Current::class.java)
        assertEquals("A31", method?.name)
        assertEquals(Migrated::class.java, method?.declaringClass)
    }
    @Test fun rejectsAmbiguousGetters() {
        assertNull(ConversationMethodResolver.chatList(Ambiguous::class.java))
    }
}
