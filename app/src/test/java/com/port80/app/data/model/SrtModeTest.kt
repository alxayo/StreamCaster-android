package com.port80.app.data.model

import org.junit.Assert.*
import org.junit.Test

class SrtModeTest {

    @Test
    fun `CALLER toUrlParam returns caller`() {
        assertEquals("caller", SrtMode.CALLER.toUrlParam())
    }

    @Test
    fun `LISTENER toUrlParam returns listener`() {
        assertEquals("listener", SrtMode.LISTENER.toUrlParam())
    }

    @Test
    fun `RENDEZVOUS toUrlParam returns rendezvous`() {
        assertEquals("rendezvous", SrtMode.RENDEZVOUS.toUrlParam())
    }

    @Test
    fun `fromString parses lowercase`() {
        assertEquals(SrtMode.CALLER, SrtMode.fromString("caller"))
        assertEquals(SrtMode.LISTENER, SrtMode.fromString("listener"))
        assertEquals(SrtMode.RENDEZVOUS, SrtMode.fromString("rendezvous"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(SrtMode.CALLER, SrtMode.fromString("CALLER"))
        assertEquals(SrtMode.LISTENER, SrtMode.fromString("Listener"))
    }

    @Test
    fun `fromString defaults to CALLER for unknown`() {
        assertEquals(SrtMode.CALLER, SrtMode.fromString("unknown"))
    }

    @Test
    fun `fromString defaults to CALLER for null`() {
        assertEquals(SrtMode.CALLER, SrtMode.fromString(null))
    }
}
