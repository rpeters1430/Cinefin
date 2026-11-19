package com.rpeters.jellyfin.testing.fixtures

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.MediaUrl
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.time.LocalDateTime
import java.util.UUID

private const val SERVER_ID = "server-1"
private const val GENRE_DRAMA = "Drama"
private const val PRIMARY_TAG = "prim"
private const val SERIES_NAME = "The Test Series"
private const val ARTIST_NAME = "The Testers"
private const val ALBUM_NAME = "Test Album"

/**
 * Realistic, fully populated Jellyfin items for tests. Screens branch on many optional fields
 * (ratings, genres, people, streams, progress), so fixtures fill them in by default; pass
 * overrides through [BaseItemDto.copy] when a test needs a specific shape.
 */
object TestItems {

    private const val TICKS_PER_MINUTE = 600_000_000L

    fun id(seed: Int): UUID = UUID(0L, seed.toLong())

    fun userData(
        itemId: UUID,
        played: Boolean = false,
        favorite: Boolean = false,
        progressPercent: Double? = 42.0,
        positionTicks: Long = 30 * TICKS_PER_MINUTE,
    ) = UserItemDataDto(
        playbackPositionTicks = positionTicks,
        playCount = if (played) 1 else 0,
        isFavorite = favorite,
        played = played,
        playedPercentage = progressPercent,
        unplayedItemCount = 3,
        lastPlayedDate = LocalDateTime.of(2026, 11, 1, 20, 0),
        key = itemId.toString(),
        itemId = itemId,
    )

    fun people(): List<BaseItemPerson> = listOf(
        BaseItemPerson(id = id(9001), name = "Ada Lovelace", role = "Captain", type = PersonKind.ACTOR, primaryImageTag = "p1"),
        BaseItemPerson(id = id(9002), name = "Grace Hopper", role = "Navigator", type = PersonKind.ACTOR),
        BaseItemPerson(id = id(9003), name = "Alan Turing", type = PersonKind.DIRECTOR),
        BaseItemPerson(id = id(9004), name = "Katherine Johnson", type = PersonKind.WRITER),
    )

    fun videoStream(codec: String = "hevc", width: Int = 3840, height: Int = 2160) = MediaStream(
        codec = codec,
        displayTitle = "4K HEVC HDR",
        width = width,
        height = height,
        bitRate = 25_000_000,
        averageFrameRate = 23.976f,
        videoRange = org.jellyfin.sdk.model.api.VideoRange.HDR,
        videoRangeType = org.jellyfin.sdk.model.api.VideoRangeType.HDR10,
        isInterlaced = false,
        isDefault = true,
        isForced = false,
        isHearingImpaired = false,
        isOriginal = true,
        type = MediaStreamType.VIDEO,
        index = 0,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
    )

    fun audioStream(index: Int = 1, codec: String = "eac3", language: String = "eng", channels: Int = 6) = MediaStream(
        codec = codec,
        language = language,
        displayTitle = "English - Dolby Digital+ 5.1",
        channels = channels,
        channelLayout = "5.1",
        bitRate = 640_000,
        isInterlaced = false,
        isDefault = index == 1,
        isForced = false,
        isHearingImpaired = false,
        isOriginal = true,
        type = MediaStreamType.AUDIO,
        index = index,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
    )

    fun subtitleStream(index: Int = 3, codec: String = "subrip", language: String = "eng", external: Boolean = false) = MediaStream(
        codec = codec,
        language = language,
        displayTitle = "English (SRT)",
        title = "English",
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        isOriginal = false,
        type = MediaStreamType.SUBTITLE,
        index = index,
        isExternal = external,
        isTextSubtitleStream = true,
        supportsExternalStream = true,
    )

    fun streams(): List<MediaStream> = listOf(
        videoStream(),
        audioStream(1),
        audioStream(2, codec = "aac", language = "spa", channels = 2),
        subtitleStream(3),
        subtitleStream(4, codec = "ass", language = "jpn", external = true),
    )

    fun mediaSource(itemId: UUID, name: String = "Main") = MediaSourceInfo(
        protocol = MediaProtocol.FILE,
        id = itemId.toString().replace("-", ""),
        path = "/media/${itemId}.mkv",
        type = MediaSourceType.DEFAULT,
        container = "mkv",
        size = 12_345_678_901L,
        name = name,
        isRemote = false,
        runTimeTicks = 120 * TICKS_PER_MINUTE,
        readAtNativeFramerate = false,
        ignoreDts = false,
        ignoreIndex = false,
        genPtsInput = false,
        supportsTranscoding = true,
        supportsDirectStream = true,
        supportsDirectPlay = true,
        isInfiniteStream = false,
        requiresOpening = false,
        requiresClosing = false,
        requiresLooping = false,
        supportsProbing = true,
        mediaStreams = streams(),
        bitrate = 26_000_000,
        transcodingSubProtocol = MediaStreamProtocol.HTTP,
        hasSegments = false,
    )

    fun chapters(): List<ChapterInfo> = (0 until 4).map { i ->
        ChapterInfo(
            startPositionTicks = i * 20 * TICKS_PER_MINUTE,
            name = "Chapter ${i + 1}",
            imageTag = "chap$i",
            imageDateModified = LocalDateTime.of(2026, 1, 1, 0, 0),
        )
    }

    private fun genres(vararg names: String) = names.mapIndexed { i, n -> NameGuidPair(name = n, id = id(8000 + i)) }

    fun movie(seed: Int = 1, name: String = "The Test Movie") = id(seed).let { uuid ->
        BaseItemDto(
            id = uuid,
            name = name,
            originalTitle = "$name (Original)",
            type = BaseItemKind.MOVIE,
            mediaType = MediaType.VIDEO,
            serverId = SERVER_ID,
            overview = "A crew of testers discovers that every screen in the app renders at least once. " +
                "Long overviews exercise the expand and collapse paths.",
            taglines = listOf("Every branch counts."),
            genres = listOf("Science Fiction", "Adventure", GENRE_DRAMA),
            genreItems = genres("Science Fiction", "Adventure", GENRE_DRAMA),
            studios = listOf(NameGuidPair(name = "Test Studio", id = id(7000))),
            productionYear = 2025,
            premiereDate = LocalDateTime.of(2025, 6, 13, 0, 0),
            dateCreated = LocalDateTime.of(2026, 11, 10, 12, 0),
            officialRating = "PG-13",
            communityRating = 8.4f,
            criticRating = 91f,
            runTimeTicks = 128 * TICKS_PER_MINUTE,
            people = people(),
            userData = userData(uuid),
            mediaSources = listOf(mediaSource(uuid), mediaSource(id(seed + 500), name = "Director's Cut")),
            mediaStreams = streams(),
            chapters = chapters(),
            remoteTrailers = listOf(MediaUrl(url = "https://www.youtube.com/watch?v=test", name = "Trailer")),
            providerIds = mapOf("Imdb" to "tt0000001", "Tmdb" to "1"),
            imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG, ImageType.LOGO to "logo", ImageType.THUMB to "thumb"),
            backdropImageTags = listOf("bd1", "bd2"),
            canDelete = true,
            canDownload = true,
            hasSubtitles = true,
            container = "mkv",
            width = 3840,
            height = 2160,
            path = "/media/movies/test.mkv",
            tags = listOf("4K"),
        )
    }

    fun series(seed: Int = 100, name: String = SERIES_NAME) = id(seed).let { uuid ->
        BaseItemDto(
            id = uuid,
            name = name,
            type = BaseItemKind.SERIES,
            serverId = SERVER_ID,
            overview = "A long-running series about writing tests for every screen.",
            genres = listOf(GENRE_DRAMA, "Mystery"),
            genreItems = genres(GENRE_DRAMA, "Mystery"),
            studios = listOf(NameGuidPair(name = "Test Network", id = id(7001))),
            productionYear = 2021,
            premiereDate = LocalDateTime.of(2021, 3, 1, 0, 0),
            endDate = LocalDateTime.of(2026, 5, 1, 0, 0),
            status = "Continuing",
            officialRating = "TV-14",
            communityRating = 8.9f,
            childCount = 3,
            recursiveItemCount = 30,
            people = people(),
            userData = userData(uuid, progressPercent = 60.0),
            remoteTrailers = listOf(MediaUrl(url = "https://www.youtube.com/watch?v=series", name = "Trailer")),
            providerIds = mapOf("Tvdb" to "123", "Imdb" to "tt0000002"),
            imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG, ImageType.LOGO to "logo"),
            backdropImageTags = listOf("bd1"),
            airDays = listOf(org.jellyfin.sdk.model.api.DayOfWeek.FRIDAY),
            airTime = "9:00 PM",
        )
    }

    fun season(seed: Int = 200, index: Int = 1, seriesSeed: Int = 100) = id(seed).let { uuid ->
        BaseItemDto(
            id = uuid,
            name = "Season $index",
            type = BaseItemKind.SEASON,
            serverId = SERVER_ID,
            indexNumber = index,
            seriesId = id(seriesSeed),
            seriesName = SERIES_NAME,
            parentId = id(seriesSeed),
            overview = "Season $index overview.",
            productionYear = 2020 + index,
            childCount = 10,
            userData = userData(uuid, progressPercent = 30.0),
            imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG),
        )
    }

    fun episode(seed: Int = 300, index: Int = 1, seasonIndex: Int = 1, played: Boolean = false) = id(seed).let { uuid ->
        BaseItemDto(
            id = uuid,
            name = "Episode $index: The Pilot",
            type = BaseItemKind.EPISODE,
            mediaType = MediaType.VIDEO,
            serverId = SERVER_ID,
            indexNumber = index,
            parentIndexNumber = seasonIndex,
            seriesId = id(100),
            seriesName = SERIES_NAME,
            seasonId = id(200),
            seasonName = "Season $seasonIndex",
            overview = "In which the tests are written.",
            premiereDate = LocalDateTime.of(2021, 3, index.coerceIn(1, 28), 0, 0),
            productionYear = 2021,
            officialRating = "TV-14",
            communityRating = 7.8f,
            runTimeTicks = 44 * TICKS_PER_MINUTE,
            people = people(),
            userData = userData(uuid, played = played),
            mediaSources = listOf(mediaSource(uuid)),
            mediaStreams = streams(),
            chapters = chapters(),
            imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG, ImageType.THUMB to "thumb"),
            seriesPrimaryImageTag = "sprim",
            parentBackdropItemId = id(100),
            parentBackdropImageTags = listOf("pbd"),
            canDelete = true,
            canDownload = true,
        )
    }

    fun episodes(count: Int = 6, seasonIndex: Int = 1): List<BaseItemDto> =
        (1..count).map { episode(seed = 300 + seasonIndex * 100 + it, index = it, seasonIndex = seasonIndex, played = it == 1) }

    fun homeVideo(seed: Int = 400) = movie(seed, "Birthday 2024").copy(
        type = BaseItemKind.VIDEO,
        genres = emptyList(),
        genreItems = emptyList(),
        people = emptyList(),
        remoteTrailers = emptyList(),
    )

    fun album(seed: Int = 500) = id(seed).let { uuid ->
        BaseItemDto(
            id = uuid,
            name = ALBUM_NAME,
            type = BaseItemKind.MUSIC_ALBUM,
            serverId = SERVER_ID,
            albumArtist = ARTIST_NAME,
            artists = listOf(ARTIST_NAME),
            albumArtists = listOf(NameGuidPair(name = ARTIST_NAME, id = id(5900))),
            artistItems = listOf(NameGuidPair(name = ARTIST_NAME, id = id(5900))),
            productionYear = 2019,
            genres = listOf("Rock"),
            childCount = 4,
            runTimeTicks = 42 * TICKS_PER_MINUTE,
            userData = userData(uuid),
            imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG),
        )
    }

    fun track(seed: Int = 600, index: Int = 1) = id(seed + index).let { uuid ->
        BaseItemDto(
            id = uuid,
            name = "Track $index",
            type = BaseItemKind.AUDIO,
            mediaType = MediaType.AUDIO,
            serverId = SERVER_ID,
            indexNumber = index,
            album = ALBUM_NAME,
            albumId = id(500),
            albumArtist = ARTIST_NAME,
            artists = listOf(ARTIST_NAME),
            runTimeTicks = 4 * TICKS_PER_MINUTE,
            userData = userData(uuid, favorite = index == 2),
            imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG),
        )
    }

    fun tracks(count: Int = 4) = (1..count).map { track(index = it) }

    fun library(seed: Int, name: String, type: CollectionType) = BaseItemDto(
        id = id(seed),
        name = name,
        type = BaseItemKind.COLLECTION_FOLDER,
        collectionType = type,
        serverId = SERVER_ID,
        childCount = 120,
        imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG),
    )

    fun libraries() = listOf(
        library(1000, "Movies", CollectionType.MOVIES),
        library(1001, "Shows", CollectionType.TVSHOWS),
        library(1002, "Music", CollectionType.MUSIC),
        library(1003, "Home Videos", CollectionType.HOMEVIDEOS),
        library(1004, "Books", CollectionType.BOOKS),
    )

    fun movies(count: Int = 8) = (1..count).map { movie(seed = 10 + it, name = "Movie $it") }

    fun seriesList(count: Int = 5) = (1..count).map { series(seed = 120 + it, name = "Series $it") }

    fun person(seed: Int = 9001) = BaseItemDto(
        id = id(seed),
        name = "Ada Lovelace",
        type = BaseItemKind.PERSON,
        serverId = SERVER_ID,
        overview = "An actor known for test-driven roles.",
        premiereDate = LocalDateTime.of(1980, 12, 10, 0, 0),
        productionLocations = listOf("London, England"),
        imageTags = mapOf(ImageType.PRIMARY to PRIMARY_TAG),
    )
}
