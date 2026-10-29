package io.github.mattgrechtech.tvkiosk.overlay

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PopupTest {

    @Test fun readsTvOverlayPayloadsFromTheKit() {
        // What the kit's script.tv_popup sends to TvOverlay today.
        val popup = Popup.fromJson(JSONObject("""
            {"title":"Front door","message":"Motion","corner":"bottom_start","duration":15,
             "image":"http://ha.test:8123/api/camera_proxy/camera.x?token=t","smallIcon":"mdi:cctv","smallIconColor":"#FF5A3C"}
        """.trimIndent()))!!
        assertEquals("Front door", popup.title)
        assertEquals("Motion", popup.message)
        assertEquals(Popup.Corner.BOTTOM_START, popup.corner)
        assertEquals(15, popup.durationSeconds)
        assertEquals("http://ha.test:8123/api/camera_proxy/camera.x?token=t", popup.image)
        assertEquals("mdi:cctv", popup.icon)
        assertEquals(0xFFFF5A3C.toInt(), popup.color)
    }

    @Test fun shortNamesAndDefaults() {
        val popup = Popup.fromJson(JSONObject("""{"message":"Hi","icon":"mdi:home","color":"80FF0000","largeIcon":"x"}"""))!!
        assertEquals("", popup.title)
        assertEquals(Popup.Corner.TOP_END, popup.corner)
        assertEquals(Popup.DEFAULT_DURATION, popup.durationSeconds)
        assertEquals("mdi:home", popup.icon)
        assertEquals(0x80FF0000.toInt(), popup.color)
        assertEquals("x", popup.image)
        assertNotNull(popup.id)
    }

    @Test fun nullsAndJunk() {
        assertNull(Popup.fromJson(JSONObject("""{"title":null,"message":""}""")))
        val popup = Popup.fromJson(JSONObject("""{"title":"T","image":null,"corner":"middle","duration":99999,"smallIconColor":"red"}"""))!!
        assertNull(popup.image)
        assertNull(popup.color)
        assertEquals(Popup.Corner.TOP_END, popup.corner)
        assertEquals(Popup.MAX_DURATION, popup.durationSeconds)
    }

    @Test fun keepsTheGivenId() {
        assertEquals("doorbell", Popup.fromJson(JSONObject("""{"title":"T","id":"doorbell"}"""))!!.id)
    }

    @Test fun mdiNamesMapToEmoji() {
        assertEquals("📹", MdiIcons.emojiFor("mdi:cctv"))
        assertEquals("🗑️", MdiIcons.emojiFor("mdi:trash-can-outline"))
        assertEquals("🔔", MdiIcons.emojiFor("mdi:doorbell-video"))
        assertNull(MdiIcons.emojiFor("mdi:zzz-unknown"))
        assertNull(MdiIcons.emojiFor("http://x/icon.png"))
    }
}
