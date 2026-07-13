package com.example

import com.example.extractor.InstagramScraperService
import com.example.extractor.YtDlpManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testInstagramScraperDirectAndFallback() {
        val testUrl = "https://www.instagram.com/p/C_q8-rISeT7/" // Change to any known public post or reel
        println("=== Starting Instagram Extraction Test ===")
        println("Target URL: $testUrl")

        val shortcode = InstagramScraperService.extractShortcode(testUrl)
        println("Extracted Shortcode: $shortcode")
        assertNotNull("Shortcode extraction should succeed", shortcode)

        // Test Strategy A: Embed scraper
        println("\n--- Testing Strategy A: Embed Scraping ---")
        try {
            val result = runBlocking {
                try {
                    InstagramScraperService.extract(testUrl)
                } catch (e: Exception) {
                    println("InstagramScraperService.extract threw: ${e.message}")
                    e.printStackTrace()
                    null
                }
            }
            if (result != null) {
                println("SUCCESS: Direct/Fallback extraction succeeded!")
                println("Title: ${result.title}")
                println("Video URL: ${result.formats.firstOrNull()?.formatId} -> ${result.formats.firstOrNull()?.width}x${result.formats.firstOrNull()?.height}")
            } else {
                println("FAILED: Both direct scraping and Cobalt fallback failed.")
            }
        } catch (e: Exception) {
            println("Unexpected test exception: ${e.message}")
            e.printStackTrace()
        }
    }

    @Test
    fun testCobaltInstancesDirectly() {
        val testUrl = "https://www.instagram.com/p/C_q8-rISeT7/"
        val cobaltUrls = listOf(
            "https://rue-cobalt.xenon.zone/",
            "https://dog.kittycat.boo/",
            "https://cobalt.fast-ref.xyz/",
            "https://cobalt.pablomg.net/",
            "https://co.wuk.sh/",
            "https://api.cobalt.tools/",
            "https://cobaltapi.cjs.nz/",
            "https://cobalt.lucasl.dev/",
            "https://cobalt.k6.vc/",
            "https://co.v9.sh/"
        )

        println("\n=== Testing All Cobalt Instances with Valid v10 Payload ===")
        for (apiUrl in cobaltUrls) {
            println("Testing instance: $apiUrl")
            try {
                val json = JSONObject().apply {
                    put("url", testUrl)
                    put("videoQuality", "max")
                    put("alwaysProxy", true)
                }

                val client = okhttp3.OkHttpClient()
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(apiUrl)
                    .post(requestBody)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Origin", "https://cobalt.tools")
                    .header("Referer", "https://cobalt.tools/")
                    .build()

                client.newCall(request).execute().use { response ->
                    val respStr = response.body?.string() ?: ""
                    println("  Response Status: ${response.code}")
                    println("  Response Body  : $respStr")
                }
            } catch (e: Exception) {
                println("  Exception: ${e.message}")
            }
        }
    }
}


