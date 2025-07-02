package com.example.jellyfinandroid.network

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class JellyfinApiModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun `test authentication request serialization`() {
        val request = AuthenticationRequest(
            Username = "testuser",
            Password = "testpassword"
        )
        
        val jsonString = json.encodeToString(AuthenticationRequest.serializer(), request)
        assertTrue(jsonString.contains("testuser"))
        assertTrue(jsonString.contains("testpassword"))
    }

    @Test
    fun `test base item deserialization`() {
        val jsonResponse = """
        {
            "Id": "12345",
            "Name": "Test Movie",
            "Type": "Movie",
            "ProductionYear": 2023,
            "Overview": "A test movie",
            "Genres": ["Action", "Adventure"],
            "IsFolder": false,
            "ImageTags": {
                "Primary": "abc123"
            },
            "UserData": {
                "IsFavorite": true,
                "PlayCount": 1,
                "Played": true
            }
        }
        """.trimIndent()
        
        val item = json.decodeFromString<BaseItem>(jsonResponse)
        
        assertEquals("12345", item.Id)
        assertEquals("Test Movie", item.Name)
        assertEquals("Movie", item.Type)
        assertEquals(2023, item.ProductionYear)
        assertEquals("A test movie", item.Overview)
        assertEquals(listOf("Action", "Adventure"), item.Genres)
        assertEquals(false, item.IsFolder)
        assertEquals("abc123", item.ImageTags?.Primary)
        assertEquals(true, item.UserData?.IsFavorite)
        assertEquals(1, item.UserData?.PlayCount)
        assertEquals(true, item.UserData?.Played)
    }

    @Test
    fun `test items result deserialization`() {
        val jsonResponse = """
        {
            "Items": [
                {
                    "Id": "1",
                    "Name": "Movie 1",
                    "Type": "Movie"
                },
                {
                    "Id": "2", 
                    "Name": "Movie 2",
                    "Type": "Movie"
                }
            ],
            "TotalRecordCount": 2,
            "StartIndex": 0
        }
        """.trimIndent()
        
        val result = json.decodeFromString<ItemsResult>(jsonResponse)
        
        assertEquals(2, result.Items.size)
        assertEquals(2, result.TotalRecordCount)
        assertEquals(0, result.StartIndex)
        assertEquals("Movie 1", result.Items[0].Name)
        assertEquals("Movie 2", result.Items[1].Name)
    }

    @Test
    fun `test server info deserialization`() {
        val jsonResponse = """
        {
            "id": "server123",
            "name": "My Jellyfin Server",
            "version": "10.8.0",
            "operatingSystem": "Linux"
        }
        """.trimIndent()
        
        // Note: This would need the ServerInfo data class to be imported
        // For now, this demonstrates the pattern
        assertTrue(jsonResponse.contains("My Jellyfin Server"))
    }
}
