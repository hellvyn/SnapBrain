package com.snapbrain.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivationTest {
    @Test
    fun verbsBeforeActivation() {
        assertEquals("Masak sekarang", activationLabel("masak", false, toBelanja = true, toTodo = true))
        assertEquals("Mau beli", activationLabel("beli", false, toBelanja = true, toTodo = false))
        assertEquals("Kerjakan", activationLabel("kerjakan", false, toBelanja = false, toTodo = true))
        assertEquals("Bayar", activationLabel("bayar", false, toBelanja = false, toTodo = true))
        assertEquals("Ikut acara", activationLabel("ikut", false, toBelanja = false, toTodo = true))
        assertEquals("Coba sekarang", activationLabel("coba", false, toBelanja = false, toTodo = true))
    }

    @Test
    fun activeSaysWhereItLanded() {
        assertEquals("✓ Ada di Belanja & To-do", activationLabel("masak", true, toBelanja = true, toTodo = true))
        assertEquals("✓ Ada di Belanja", activationLabel("beli", true, toBelanja = true, toTodo = false))
        assertEquals("✓ Ada di To-do", activationLabel("ikut", true, toBelanja = false, toTodo = true))
    }

    @Test
    fun hiddenWhenNothingWouldLand() {
        assertNull(activationLabel("bayar", false, toBelanja = false, toTodo = false))
        assertNull(activationLabel("none", false, toBelanja = true, toTodo = true))
        assertNull(activationLabel(null, false, toBelanja = true, toTodo = true))
        assertNull(activationLabel("terbang", false, toBelanja = true, toTodo = true))
    }

    @Test
    fun targets() {
        assertTrue(activatesBelanja("masak"))
        assertTrue(activatesBelanja("beli"))
        assertFalse(activatesBelanja("ikut"))
        assertFalse(activatesBelanja(null))
        assertTrue(todoEligible("todo", "checklist"))
        assertTrue(todoEligible("bawa", "checklist"))
        assertTrue(todoEligible("lainnya", "steps"))
        assertFalse(todoEligible("belanja", "checklist"))
        assertFalse(todoEligible("lainnya", "checklist"))
    }
}
