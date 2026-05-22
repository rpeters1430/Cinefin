package com.rpeters.jellyfin.ui.utils

import android.content.Context
import android.content.Intent
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrailerUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        context = mockk(relaxed = true)
    }

    @Test
    fun testGetYouTubeVideoId_validUrls() {
        val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", TrailerUtils.getYouTubeVideoId(watchUrl))

        val watchUrlWithQuery = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"
        assertEquals("dQw4w9WgXcQ", TrailerUtils.getYouTubeVideoId(watchUrlWithQuery))

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", TrailerUtils.getYouTubeVideoId(shortUrl))

        val embedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", TrailerUtils.getYouTubeVideoId(embedUrl))

        val vUrl = "https://youtube.com/v/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", TrailerUtils.getYouTubeVideoId(vUrl))

        val vndUrl = "vnd.youtube:dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", TrailerUtils.getYouTubeVideoId(vndUrl))
    }

    @Test
    fun testGetYouTubeVideoId_invalidUrls() {
        val searchUrl = "https://www.youtube.com/results?search_query=matrix+trailer"
        assertNull(TrailerUtils.getYouTubeVideoId(searchUrl))

        val nonYoutubeUrl = "https://vimeo.com/12345678"
        assertNull(TrailerUtils.getYouTubeVideoId(nonYoutubeUrl))
    }

    @Test
    fun testPlayTrailer_youtubeVideo_opensVndSchemeFirst() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val intentSlot = slot<Intent>()

        TrailerUtils.playTrailer(context, url)

        verify(exactly = 1) { context.startActivity(capture(intentSlot)) }
        val capturedIntent = intentSlot.captured
        assertEquals(Intent.ACTION_VIEW, capturedIntent.action)
        assertEquals("vnd.youtube:dQw4w9WgXcQ", capturedIntent.dataString)
    }

    @Test
    fun testPlayTrailer_youtubeVideo_fallbackToPackageNameOnSchemeFailure() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val intentSlots = mutableListOf<Intent>()

        // Force vnd.youtube: scheme to fail
        every { context.startActivity(match { it.dataString == "vnd.youtube:dQw4w9WgXcQ" }) } throws RuntimeException("App not found")

        TrailerUtils.playTrailer(context, url)

        verify(exactly = 2) { context.startActivity(capture(intentSlots)) }
        
        // First call should be vnd.youtube
        assertEquals("vnd.youtube:dQw4w9WgXcQ", intentSlots[0].dataString)
        
        // Second call should fall back to opening standard URL with package set to youtube
        assertEquals(Intent.ACTION_VIEW, intentSlots[1].action)
        assertEquals(url, intentSlots[1].dataString)
        assertEquals("com.google.android.youtube", intentSlots[1].`package`)
    }

    @Test
    fun testPlayTrailer_youtubeVideo_fallbackToBrowserOnPackageFailure() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val intentSlots = mutableListOf<Intent>()

        // Force vnd.youtube: scheme and package launch to fail
        every { context.startActivity(match { it.dataString == "vnd.youtube:dQw4w9WgXcQ" }) } throws RuntimeException("Scheme failed")
        every { context.startActivity(match { it.`package` == "com.google.android.youtube" }) } throws RuntimeException("Package failed")

        TrailerUtils.playTrailer(context, url)

        verify(exactly = 3) { context.startActivity(capture(intentSlots)) }
        
        // Check 3rd intent (browser fallback)
        val finalIntent = intentSlots[2]
        assertEquals(Intent.ACTION_VIEW, finalIntent.action)
        assertEquals(url, finalIntent.dataString)
        assertNull(finalIntent.`package`)
    }

    @Test
    fun testPlayTrailer_youtubeSearch_opensPackageNameFirst() {
        val url = "https://www.youtube.com/results?search_query=matrix+trailer"
        val intentSlot = slot<Intent>()

        TrailerUtils.playTrailer(context, url)

        verify(exactly = 1) { context.startActivity(capture(intentSlot)) }
        val capturedIntent = intentSlot.captured
        assertEquals(Intent.ACTION_VIEW, capturedIntent.action)
        assertEquals(url, capturedIntent.dataString)
        assertEquals("com.google.android.youtube", capturedIntent.`package`)
    }

    @Test
    fun testPlayTrailer_nonYoutube_opensBrowserDirectly() {
        val url = "https://vimeo.com/12345678"
        val intentSlot = slot<Intent>()

        TrailerUtils.playTrailer(context, url)

        verify(exactly = 1) { context.startActivity(capture(intentSlot)) }
        val capturedIntent = intentSlot.captured
        assertEquals(Intent.ACTION_VIEW, capturedIntent.action)
        assertEquals(url, capturedIntent.dataString)
        assertNull(capturedIntent.`package`)
    }
}
