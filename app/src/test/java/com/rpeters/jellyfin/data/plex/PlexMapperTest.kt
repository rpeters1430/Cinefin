package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadResource(path: String): String =
        requireNotNull(javaClass.classLoader?.getResource(path)) { "Resource not found: $path" }.readText()

    @Test
    fun timeConversion_msAndTicks() {
        assertEquals(0L, PlexMapper.msToTicks(0L))
        assertEquals(0L, PlexMapper.ticksToMs(0L))

        // 1 ms = 10,000 ticks
        assertEquals(10_000L, PlexMapper.msToTicks(1L))
        assertEquals(1L, PlexMapper.ticksToMs(10_000L))

        // 1 second (1000 ms) = 10,000,000 ticks
        assertEquals(10_000_000L, PlexMapper.msToTicks(1_000L))
        assertEquals(1_000L, PlexMapper.ticksToMs(10_000_000L))

        // Large movie duration (2.5 hours = 9,000,000 ms)
        val ms = 9_000_000L
        val ticks = PlexMapper.msToTicks(ms)
        assertEquals(90_000_000_000L, ticks)
        assertEquals(ms, PlexMapper.ticksToMs(ticks))
    }

    @Test
    fun mapMovie_fromFixture() {
        val body = loadResource("plex/pms/movie_metadata.json")
        val container = json.decodeFromString<PlexMediaContainerResponse>(body).mediaContainer
        assertEquals(1, container.metadata.size)

        val movie = PlexMapper.toBaseItemDto(container.metadata.first())

        assertEquals(ServerIdCodec.PLEX.encode("1001"), movie.id)
        assertEquals("Inception", movie.name)
        assertEquals("Inception", movie.originalTitle)
        assertEquals(BaseItemKind.MOVIE, movie.type)
        assertEquals(2010, movie.productionYear)
        assertEquals("PG-13", movie.officialRating)
        assertEquals(9.1f, movie.communityRating ?: 0f, 0.01f)
        assertEquals(listOf("Your mind is the scene of the crime."), movie.taglines)
        assertEquals(listOf("Action", "Sci-Fi"), movie.genres)
        assertEquals(1, movie.studios?.size)
        assertEquals("Syncopy", movie.studios?.first()?.name)
        assertEquals(88_800_000_000L, movie.runTimeTicks)

        // People
        val people = requireNotNull(movie.people)
        assertEquals(5, people.size)

        val actor = people.first { it.name == "Leonardo DiCaprio" }
        assertEquals(PersonKind.ACTOR, actor.type)
        assertEquals("Dom Cobb", actor.role)
        assertEquals(ServerIdCodec.PLEX_TAG.encode("301"), actor.id)
        assertEquals("https://metadata-static.plex.tv/people/301.jpg", actor.primaryImageTag)

        val director = people.first { it.type == PersonKind.DIRECTOR }
        assertEquals("Christopher Nolan", director.name)
        assertEquals(ServerIdCodec.PLEX_TAG.encode("401"), director.id)

        val producer = people.first { it.type == PersonKind.PRODUCER }
        assertEquals("Emma Thomas", producer.name)
        assertEquals(ServerIdCodec.PLEX_TAG.encode("501"), producer.id)

        // User data
        val userData = requireNotNull(movie.userData)
        assertTrue(userData.played)
        assertEquals(1, userData.playCount)
        assertEquals(PlexMapper.msToTicks(2_400_000L), userData.playbackPositionTicks)
        assertFalse(userData.isFavorite)

        // Images
        assertNotNull(movie.imageTags?.get(ImageType.PRIMARY))
        assertNotNull(movie.imageTags?.get(ImageType.LOGO))
        assertEquals(1, movie.backdropImageTags?.size)
    }

    @Test
    fun mapShow_fromFixture() {
        val body = loadResource("plex/pms/show_metadata.json")
        val container = json.decodeFromString<PlexMediaContainerResponse>(body).mediaContainer
        val show = PlexMapper.toBaseItemDto(container.metadata.first())

        assertEquals(ServerIdCodec.PLEX.encode("2001"), show.id)
        assertEquals("Succession", show.name)
        assertEquals(BaseItemKind.SERIES, show.type)
        assertEquals(2018, show.productionYear)
        assertEquals(4, show.childCount)
        assertEquals(39, show.recursiveItemCount)
        assertEquals(show.id, show.seriesId)
        assertEquals("Succession", show.seriesName)
    }

    @Test
    fun mapSeason_fromFixture() {
        val body = loadResource("plex/pms/season_metadata.json")
        val container = json.decodeFromString<PlexMediaContainerResponse>(body).mediaContainer
        val season = PlexMapper.toBaseItemDto(container.metadata.first())

        assertEquals(ServerIdCodec.PLEX.encode("2002"), season.id)
        assertEquals("Season 1", season.name)
        assertEquals(BaseItemKind.SEASON, season.type)
        assertEquals(1, season.indexNumber)
        assertEquals(ServerIdCodec.PLEX.encode("2001"), season.seriesId)
        assertEquals(ServerIdCodec.PLEX.encode("2001"), season.parentId)
        assertEquals("Succession", season.seriesName)
        assertEquals(season.id, season.seasonId)
    }

    @Test
    fun mapEpisode_fromFixture() {
        val body = loadResource("plex/pms/episode_metadata.json")
        val container = json.decodeFromString<PlexMediaContainerResponse>(body).mediaContainer
        val episode = PlexMapper.toBaseItemDto(container.metadata.first())

        assertEquals(ServerIdCodec.PLEX.encode("2003"), episode.id)
        assertEquals("Celebration", episode.name)
        assertEquals(BaseItemKind.EPISODE, episode.type)
        assertEquals(1, episode.indexNumber)
        assertEquals(1, episode.parentIndexNumber)
        assertEquals(ServerIdCodec.PLEX.encode("2002"), episode.seasonId)
        assertEquals(ServerIdCodec.PLEX.encode("2002"), episode.parentId)
        assertEquals(ServerIdCodec.PLEX.encode("2001"), episode.seriesId)
        assertEquals("Season 1", episode.seasonName)
        assertEquals("Succession", episode.seriesName)

        val userData = requireNotNull(episode.userData)
        assertFalse(userData.played)
        assertEquals(0, userData.playCount)
        assertEquals(PlexMapper.msToTicks(1_200_000L), userData.playbackPositionTicks)

        val chapters = requireNotNull(episode.chapters)
        assertEquals(4, chapters.size)
        assertEquals("intro", chapters[0].name)
        assertEquals(PlexMapper.msToTicks(150_000L), chapters[0].startPositionTicks)
        assertEquals("intro_end", chapters[1].name)
        assertEquals(PlexMapper.msToTicks(230_000L), chapters[1].startPositionTicks)
        assertEquals("credits", chapters[2].name)
        assertEquals(PlexMapper.msToTicks(3_500_000L), chapters[2].startPositionTicks)
        assertEquals("credits_end", chapters[3].name)
        assertEquals(PlexMapper.msToTicks(3_600_000L), chapters[3].startPositionTicks)
    }

    @Test
    fun mapSections_fromFixture() {
        val body = loadResource("plex/pms/sections.json")
        val container = json.decodeFromString<PlexMediaContainerResponse>(body).mediaContainer
        val result = PlexMapper.toQueryResult(container)

        assertEquals(2, result.totalRecordCount)
        assertEquals(2, result.items.size)

        val movies = result.items[0]
        assertEquals(ServerIdCodec.PLEX.encode("1"), movies.id)
        assertEquals("Movies", movies.name)
        assertEquals(BaseItemKind.COLLECTION_FOLDER, movies.type)
        assertEquals(CollectionType.MOVIES, movies.collectionType)

        val shows = result.items[1]
        assertEquals(ServerIdCodec.PLEX.encode("2"), shows.id)
        assertEquals("TV Shows", shows.name)
        assertEquals(BaseItemKind.COLLECTION_FOLDER, shows.type)
        assertEquals(CollectionType.TVSHOWS, shows.collectionType)
    }
}
