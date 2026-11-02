package dev.vereda.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FramesTest {
    @Test
    fun mediaFrameRoundTrip() {
        val id = ByteArray(16) { it.toByte() }
        val encoded = MediaFrames.write(id, "public", "image/jpeg", 1, 3, byteArrayOf(9, 8, 7))
        val part = MediaFrames.read(encoded)
        assertNotNull(part)
        assertEquals(Hex.encode(id), part.mediaId)
        assertEquals("public", part.conversation)
        assertEquals(1, part.index)
        assertEquals(3, part.count)
        assertEquals(listOf(9, 8, 7), part.data.map { it.toInt() })
    }

    @Test
    fun roomMediaHidesTheBytes() {
        val nonce = ByteArray(12) { 9 }
        val photo = byteArrayOf(1, 2, 3, 9, 9)
        val sealed = MediaSeal.sealRoom("segredo", "sala-1", "image/jpeg", photo, nonce)
        assertTrue("image/jpeg".encodeToByteArray().toList() !in sealed.toList().windowed(10))
        val open = MediaSeal.openRoom("segredo", "sala-1", sealed)
        assertNotNull(open)
        assertEquals("image/jpeg", open.first)
        assertEquals(photo.toList(), open.second.toList())
        assertTrue(MediaSeal.openRoom("outra", "sala-1", sealed) == null)
    }

    @Test
    fun roomPasswordRoundTrip() {
        val key = RoomCipher.key("segredo", "sala-1")
        val nonce = ByteArray(12) { 3 }
        val sealed = RoomCipher.seal(key, "sala-1", "olá".encodeToByteArray(), nonce)
        val open = RoomCipher.open(key, "sala-1", sealed, nonce)
        assertEquals("olá", open?.toString(Charsets.UTF_8))
        assertTrue(RoomCipher.open(RoomCipher.key("outra", "sala-1"), "sala-1", sealed, nonce) == null)
    }

    @Test
    fun geohashOfAKnownPoint() {
        val hash = Geohash.encode(57.64911, 10.40744, 11)
        assertEquals("u4pruydqqvj", hash)
        val center = Geohash.center("u4pruydqqvj")
        assertNotNull(center)
        assertTrue(kotlin.math.abs(center.first - 57.64911) < 0.001)
        val area = Geohash.box(hash)
        assertNotNull(area)
        assertTrue(area.contains(57.64911, 10.40744))
    }

    @Test
    fun coarserCellCoversTheFinerOne() {
        val fine = Geohash.encode(-23.5505, -46.6333, 6)
        val city = Geohash.box(fine.take(5))
        val block = Geohash.box(fine)
        assertNotNull(city)
        assertNotNull(block)
        assertTrue(city.contains(block.south, block.west))
        assertTrue(city.contains(block.north, block.east))
        assertTrue(city.north - city.south > block.north - block.south)
    }

    @Test
    fun roomMetaRoundTrip() {
        val bytes = RoomFrames.meta("abc", "amigos", true)
        val meta = RoomFrames.readMeta(bytes)
        assertNotNull(meta)
        assertEquals("amigos", meta.name)
        assertTrue(meta.hasPassword)
    }
}
