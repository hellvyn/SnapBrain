package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VersionTest {
    @Test
    fun exposesVersion() {
        assertEquals("0.1.0", CORE_VERSION)
    }
}
