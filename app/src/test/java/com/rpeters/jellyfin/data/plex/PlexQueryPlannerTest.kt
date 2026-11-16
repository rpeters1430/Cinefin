package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexQueryPlannerTest {

    @Test
    fun plan_favoritesFilter_returnsStaticEmpty() {
        val plan = PlexQueryPlanner.plan(PlexQueryParams(filters = listOf("IsFavorite")))
        assertTrue(plan.isStaticEmpty)
    }

    @Test
    fun plan_musicQueries_returnsStaticEmpty() {
        val plan1 = PlexQueryPlanner.plan(PlexQueryParams(artistIds = listOf("123")))
        assertTrue(plan1.isStaticEmpty)

        val plan2 = PlexQueryPlanner.plan(PlexQueryParams(albumArtistIds = listOf("456")))
        assertTrue(plan2.isStaticEmpty)
    }

    @Test
    fun plan_search_routesToHubsSearch() {
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(searchTerm = "Inception", startIndex = 10, limit = 25),
        )
        assertEquals("/hubs/search", plan.path)
        assertEquals("Inception", plan.queryParameters["query"])
        assertEquals(10, plan.queryParameters[PlexQueryPlanner.HEADER_START])
        assertEquals(25, plan.queryParameters[PlexQueryPlanner.HEADER_SIZE])
    }

    @Test
    fun plan_continueWatching_routesToHubsContinueWatching() {
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(filters = listOf("IsResumable"), limit = 12),
        )
        assertEquals("/hubs/continueWatching", plan.path)
        assertEquals(12, plan.queryParameters[PlexQueryPlanner.HEADER_SIZE])
    }

    @Test
    fun plan_idsList_routesToMetadataByIds() {
        val id1 = ServerIdCodec.PLEX.encode("1001").toString()
        val id2 = ServerIdCodec.PLEX.encode("1002").toString()
        val plan = PlexQueryPlanner.plan(PlexQueryParams(ids = listOf(id1, id2)))

        assertEquals("/library/metadata/1001,1002", plan.path)
    }

    @Test
    fun plan_recentlyAddedGlobally_routesToRecentlyAdded() {
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(sortBy = listOf("DateCreated"), sortOrder = "Descending", limit = 20),
        )
        assertEquals("/library/recentlyAdded", plan.path)
        assertEquals(20, plan.queryParameters[PlexQueryPlanner.HEADER_SIZE])
    }

    @Test
    fun plan_personQuery_routesToSectionActor() {
        val personId = ServerIdCodec.PLEX_TAG.encode("301").toString()
        val parentId = ServerIdCodec.PLEX.encode("1").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(parentId = parentId, personIds = listOf(personId)),
        )
        assertEquals("/library/sections/1/all", plan.path)
        assertEquals("301", plan.queryParameters["actor"])
    }

    @Test
    fun plan_seriesChildren_routesToSeasons() {
        val seriesId = ServerIdCodec.PLEX.encode("2001").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(parentId = seriesId, includeItemTypes = listOf("Season")),
        )
        assertEquals("/library/metadata/2001/children", plan.path)
    }

    @Test
    fun plan_seriesAllLeaves_routesToAllLeaves() {
        val seriesId = ServerIdCodec.PLEX.encode("2001").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(parentId = seriesId, includeItemTypes = listOf("Episode"), recursive = true),
        )
        assertEquals("/library/metadata/2001/allLeaves", plan.path)
    }

    @Test
    fun plan_seasonChildren_routesToEpisodes() {
        val seasonId = ServerIdCodec.PLEX.encode("2002").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(parentId = seasonId, includeItemTypes = listOf("Episode"), recursive = false),
        )
        assertEquals("/library/metadata/2002/children", plan.path)
    }

    @Test
    fun plan_librarySectionRecentlyAdded() {
        val sectionId = ServerIdCodec.PLEX.encode("1").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(
                parentId = sectionId,
                sortBy = listOf("DateCreated"),
                sortOrder = "Descending",
                limit = 15,
            ),
        )
        assertEquals("/library/sections/1/recentlyAdded", plan.path)
        assertEquals(15, plan.queryParameters[PlexQueryPlanner.HEADER_SIZE])
    }

    @Test
    fun plan_librarySectionMoviesBrowse() {
        val sectionId = ServerIdCodec.PLEX.encode("1").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(
                parentId = sectionId,
                includeItemTypes = listOf("Movie"),
                recursive = true,
                sortBy = listOf("SortName"),
                sortOrder = "Ascending",
                startIndex = 50,
                limit = 50,
            ),
        )
        assertEquals("/library/sections/1/all", plan.path)
        assertEquals(PlexQueryPlanner.TYPE_MOVIE, plan.queryParameters["type"])
        assertEquals("titleSort:asc", plan.queryParameters["sort"])
        assertEquals(50, plan.queryParameters[PlexQueryPlanner.HEADER_START])
        assertEquals(50, plan.queryParameters[PlexQueryPlanner.HEADER_SIZE])
    }

    @Test
    fun plan_librarySectionSeriesBrowse() {
        val sectionId = ServerIdCodec.PLEX.encode("2").toString()
        val plan = PlexQueryPlanner.plan(
            PlexQueryParams(
                parentId = sectionId,
                includeItemTypes = listOf("Series"),
                recursive = true,
                sortBy = listOf("CommunityRating"),
                sortOrder = "Descending",
            ),
        )
        assertEquals("/library/sections/2/all", plan.path)
        assertEquals(PlexQueryPlanner.TYPE_SERIES, plan.queryParameters["type"])
        assertEquals("audienceRating:desc", plan.queryParameters["sort"])
    }

    @Test
    fun fromQueryMap_extractsAllParameters() {
        val map = mapOf(
            "parentId" to "section-1",
            "includeItemTypes" to "Movie,Series",
            "recursive" to "true",
            "sortBy" to listOf("SortName"),
            "sortOrder" to "Descending",
            "startIndex" to 10,
            "limit" to 20,
            "searchTerm" to "test",
            "filters" to listOf("IsResumable"),
            "ids" to "id1,id2",
        )
        val params = PlexQueryPlanner.fromQueryMap(map)

        assertEquals("section-1", params.parentId)
        assertEquals(listOf("Movie", "Series"), params.includeItemTypes)
        assertTrue(params.recursive)
        assertEquals(listOf("SortName"), params.sortBy)
        assertEquals("Descending", params.sortOrder)
        assertEquals(10, params.startIndex)
        assertEquals(20, params.limit)
        assertEquals("test", params.searchTerm)
        assertEquals(listOf("IsResumable"), params.filters)
        assertEquals(listOf("id1", "id2"), params.ids)
    }
}
