package com.rpeters.jellyfin.data.plex

import com.rpeters.jellyfin.data.common.ids.ServerIdCodec
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Maps Plex Media Server JSON responses (`Metadata`, `Directory`, `MediaContainer`) into
 * Jellyfin SDK models (`BaseItemDto`, `BaseItemDtoQueryResult`, `UserItemDataDto`).
 */
object PlexMapper {
    /** 1 millisecond = 10,000 ticks in Jellyfin / .NET time. */
    const val TICKS_PER_MS = 10_000L

    fun msToTicks(ms: Long): Long = ms * TICKS_PER_MS

    fun ticksToMs(ticks: Long): Long = ticks / TICKS_PER_MS

    fun toBaseItemKind(type: String?): BaseItemKind = when (type?.lowercase()) {
        "movie" -> BaseItemKind.MOVIE
        "show" -> BaseItemKind.SERIES
        "season" -> BaseItemKind.SEASON
        "episode" -> BaseItemKind.EPISODE
        "track", "song" -> BaseItemKind.AUDIO
        "album" -> BaseItemKind.MUSIC_ALBUM
        "artist" -> BaseItemKind.MUSIC_ARTIST
        "photo" -> BaseItemKind.PHOTO
        else -> BaseItemKind.VIDEO
    }

    fun toCollectionType(type: String?): CollectionType? = when (type?.lowercase()) {
        "movie" -> CollectionType.MOVIES
        "show" -> CollectionType.TVSHOWS
        "artist", "music" -> CollectionType.MUSIC
        "photo", "photos" -> CollectionType.PHOTOS
        "homevideos" -> CollectionType.HOMEVIDEOS
        else -> null
    }

    fun toBaseItemDto(directory: PlexDirectory): BaseItemDto {
        val id = ServerIdCodec.PLEX.encode(directory.key) ?: UUID.randomUUID()
        val tag = (directory.updatedAt ?: directory.createdAt)?.toString() ?: "1"
        return BaseItemDto(
            id = id,
            name = directory.title,
            type = BaseItemKind.COLLECTION_FOLDER,
            collectionType = toCollectionType(directory.type),
            imageTags = if (!directory.thumb.isNullOrBlank()) mapOf(ImageType.PRIMARY to tag) else emptyMap(),
            backdropImageTags = if (!directory.art.isNullOrBlank()) listOf(tag) else emptyList(),
        )
    }

    fun toBaseItemDto(metadata: PlexMetadata): BaseItemDto {
        val id = ServerIdCodec.PLEX.encode(metadata.ratingKey) ?: UUID.randomUUID()
        val kind = toBaseItemKind(metadata.type)
        val imageTag = (metadata.updatedAt ?: metadata.addedAt)?.toString() ?: "1"

        val parentId = metadata.parentRatingKey?.let { ServerIdCodec.PLEX.encode(it) }
        val grandparentRatingKey = metadata.grandparentRatingKey
        val grandparentId = grandparentRatingKey?.let { ServerIdCodec.PLEX.encode(it) }

        val seriesId = when (kind) {
            BaseItemKind.SERIES -> id
            BaseItemKind.SEASON -> parentId
            BaseItemKind.EPISODE -> grandparentId
            else -> null
        }

        val seriesName = when (kind) {
            BaseItemKind.SERIES -> metadata.title
            BaseItemKind.SEASON -> metadata.parentTitle
            BaseItemKind.EPISODE -> metadata.grandparentTitle
            else -> null
        }

        val seasonId = when (kind) {
            BaseItemKind.SEASON -> id
            BaseItemKind.EPISODE -> parentId
            else -> null
        }

        val seasonName = when (kind) {
            BaseItemKind.SEASON -> metadata.title
            BaseItemKind.EPISODE -> metadata.parentTitle
            else -> null
        }

        val runTimeTicks = metadata.duration?.let { msToTicks(it) }
        val userData = toUserDataDto(metadata, id)

        val people = buildList {
            metadata.roles.forEach { role ->
                val personId = role.id?.toString()?.let { ServerIdCodec.PLEX_TAG.encode(it) }
                    ?: UUID.nameUUIDFromBytes("person:${role.tag}".toByteArray())
                add(
                    BaseItemPerson(
                        name = role.tag,
                        id = personId,
                        role = role.role,
                        type = PersonKind.ACTOR,
                        primaryImageTag = role.thumb,
                    ),
                )
            }
            metadata.directors.forEach { director ->
                val personId = director.id?.toString()?.let { ServerIdCodec.PLEX_TAG.encode(it) }
                    ?: UUID.nameUUIDFromBytes("person:${director.tag}".toByteArray())
                add(
                    BaseItemPerson(
                        name = director.tag,
                        id = personId,
                        type = PersonKind.DIRECTOR,
                    ),
                )
            }
            metadata.writers.forEach { writer ->
                val personId = writer.id?.toString()?.let { ServerIdCodec.PLEX_TAG.encode(it) }
                    ?: UUID.nameUUIDFromBytes("person:${writer.tag}".toByteArray())
                add(
                    BaseItemPerson(
                        name = writer.tag,
                        id = personId,
                        type = PersonKind.WRITER,
                    ),
                )
            }
            metadata.producers.forEach { producer ->
                val personId = producer.id?.toString()?.let { ServerIdCodec.PLEX_TAG.encode(it) }
                    ?: UUID.nameUUIDFromBytes("person:${producer.tag}".toByteArray())
                add(
                    BaseItemPerson(
                        name = producer.tag,
                        id = personId,
                        type = PersonKind.PRODUCER,
                    ),
                )
            }
        }.takeIf { it.isNotEmpty() }

        val imageTags = buildMap {
            if (!metadata.thumb.isNullOrBlank()) put(ImageType.PRIMARY, imageTag)
            if (!metadata.clearLogo.isNullOrBlank()) put(ImageType.LOGO, imageTag)
        }

        val backdropTags = if (!metadata.art.isNullOrBlank()) listOf(imageTag) else emptyList()

        val premiereDate = parseDate(metadata.originallyAvailableAt)
        val dateCreated = metadata.addedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }

        val studios = metadata.studio?.let { studioName ->
            val studioId = ServerIdCodec.PLEX_TAG.encode(studioName)
                ?: UUID.nameUUIDFromBytes("studio:$studioName".toByteArray())
            listOf(NameGuidPair(name = studioName, id = studioId))
        }

        val chapters = metadata.markers.flatMap { marker ->
            listOf(
                ChapterInfo(
                    name = marker.type,
                    startPositionTicks = msToTicks(marker.startTimeOffset),
                    imagePath = null,
                    imageDateModified = LocalDateTime.MIN,
                    imageTag = null,
                ),
                ChapterInfo(
                    name = "${marker.type}_end",
                    startPositionTicks = msToTicks(marker.endTimeOffset),
                    imagePath = null,
                    imageDateModified = LocalDateTime.MIN,
                    imageTag = null,
                ),
            )
        }.takeIf { it.isNotEmpty() }

        return BaseItemDto(
            id = id,
            name = metadata.title,
            originalTitle = metadata.originalTitle,
            type = kind,
            overview = metadata.summary,
            taglines = metadata.tagline?.let { listOf(it) },
            genres = metadata.genres.map { it.tag }.takeIf { it.isNotEmpty() },
            studios = studios,
            productionYear = metadata.year,
            premiereDate = premiereDate,
            dateCreated = dateCreated,
            communityRating = (metadata.audienceRating ?: metadata.rating)?.toFloat(),
            officialRating = metadata.contentRating,
            runTimeTicks = runTimeTicks,
            indexNumber = metadata.index,
            parentIndexNumber = metadata.parentIndex,
            parentId = parentId,
            seriesId = seriesId,
            seriesName = seriesName,
            seasonId = seasonId,
            seasonName = seasonName,
            childCount = metadata.childCount,
            recursiveItemCount = metadata.leafCount,
            userData = userData,
            people = people,
            chapters = chapters,
            imageTags = imageTags.takeIf { it.isNotEmpty() },
            backdropImageTags = backdropTags.takeIf { it.isNotEmpty() },
        )
    }

    fun toUserDataDto(
        metadata: PlexMetadata,
        itemId: UUID = ServerIdCodec.PLEX.encode(metadata.ratingKey) ?: UUID.randomUUID(),
    ): UserItemDataDto {
        val positionTicks = metadata.viewOffset?.let { msToTicks(it) } ?: 0L
        val playCount = metadata.viewCount ?: 0
        val played = playCount > 0
        val lastPlayed = metadata.lastViewedAt?.let {
            LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC)
        }

        return UserItemDataDto(
            playbackPositionTicks = positionTicks,
            playCount = playCount,
            played = played,
            isFavorite = false,
            lastPlayedDate = lastPlayed,
            key = metadata.key ?: metadata.ratingKey,
            itemId = itemId,
        )
    }

    fun toQueryResult(container: PlexMediaContainer): BaseItemDtoQueryResult {
        val items = when {
            container.directory.isNotEmpty() -> container.directory.map(::toBaseItemDto)
            container.metadata.isNotEmpty() -> container.metadata.map(::toBaseItemDto)
            container.hub.isNotEmpty() -> container.hub.flatMap { it.metadata }.map(::toBaseItemDto)
            else -> emptyList()
        }
        val total = container.totalSize ?: container.size.takeIf { it > 0 } ?: items.size
        val start = container.offset ?: 0
        return BaseItemDtoQueryResult(
            items = items,
            totalRecordCount = total,
            startIndex = start,
        )
    }

    private fun parseDate(dateStr: String?): LocalDateTime? {
        if (dateStr.isNullOrBlank()) return null
        return runCatching {
            LocalDate.parse(dateStr).atStartOfDay()
        }.getOrNull()
    }
}
