package com.wmods.wppenhacer.utils

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Avoid choosing an unrelated List getter after a host implementation moves to its base. */
object ConversationMethodResolver {
    fun chatList(type: Class<*>): Method? {
        fun accepts(m: Method) = m.parameterCount == 0 &&
            m.returnType == List::class.java && !Modifier.isStatic(m.modifiers)
        val direct = type.declaredMethods.filter(::accepts)
        if (direct.isNotEmpty()) return direct.singleOrNull()
        // Verified on WA 2.26.46.82: ConversationsFragmentKt.A31, not BGF.
        return type.superclass?.declaredMethods?.singleOrNull { it.name == "A31" && accepts(it) }
    }
}
