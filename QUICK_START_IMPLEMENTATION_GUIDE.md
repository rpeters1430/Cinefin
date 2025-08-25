# 🚀 Quick Start Implementation Guide - Phase 4 Priority Items

## 🎯 **IMMEDIATE IMPLEMENTATION (Week 1)**

### **1. Code Duplication Elimination - Rating Extension Function**

**File**: `app/src/main/java/com/rpeters/jellyfin/data/utils/Extensions.kt`

```kotlin
package com.rpeters.jellyfin.data.utils

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Extension functions for common operations across the app
 */

/**
 * Safely converts community rating to Double
 * Eliminates duplicate rating conversion logic found in 8+ locations
 */
fun BaseItemDto.getRatingAsDouble(): Double = 
    (communityRating as? Number)?.toDouble() ?: 0.0

/**
 * Safely converts official rating to Double
 */
fun BaseItemDto.getOfficialRatingAsDouble(): Double = 
    (officialRating as? Number)?.toDouble() ?: 0.0

/**
 * Checks if item has a high rating (>= 7.0)
 */
fun BaseItemDto.hasHighRating(): Boolean = getRatingAsDouble() >= 7.0

/**
 * Gets formatted rating string
 */
fun BaseItemDto.getFormattedRating(): String = 
    "%.1f".format(getRatingAsDouble())
```

**Usage Update**: Replace all instances of `(it.communityRating as? Number)?.toDouble() ?: 0.0` with `it.getRatingAsDouble()`

### **2. Centralized Constants**

**File**: `app/src/main/java/com/rpeters/jellyfin/core/AppConstants.kt`

```kotlin
package com.rpeters.jellyfin.core

/**
 * Centralized constants for the application
 * Eliminates magic numbers scattered throughout the codebase
 */
object AppConstants {
    // Rating thresholds
    const val HIGH_RATING_THRESHOLD = 7.0
    const val EXCELLENT_RATING_THRESHOLD = 8.0
    
    // UI dimensions
    const val CAROUSEL_ITEM_WIDTH = 320
    const val CARD_ASPECT_RATIO = 16f / 9f
    const val POSTER_ASPECT_RATIO = 2f / 3f
    
    // Timeouts and durations
    const val DEFAULT_TIMEOUT = 30_000L
    const val TOKEN_VALIDITY_DURATION = 50 * 60 * 1000L
    const val CACHE_DURATION = 24 * 60 * 60 * 1000L // 24 hours
    
    // HTTP status codes
    const val HTTP_OK = 200
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_NOT_FOUND = 404
    const val HTTP_SERVER_ERROR = 500
    
    // Pagination
    const val DEFAULT_PAGE_SIZE = 50
    const val MAX_PAGE_SIZE = 100
    
    // Image quality
    const val IMAGE_QUALITY_HIGH = 90
    const val IMAGE_QUALITY_MEDIUM = 70
    const val IMAGE_QUALITY_LOW = 50
}
```

### **3. Standardized Error Handling**

**File**: `app/src/main/java/com/rpeters/jellyfin/core/JellyfinError.kt`

```kotlin
package com.rpeters.jellyfin.core

/**
 * Standardized error types for the application
 * Provides consistent error handling across all components
 */
sealed class JellyfinError : Exception() {
    object NetworkError : JellyfinError() {
        override val message: String = "Network connection error"
    }
    
    object AuthenticationError : JellyfinError() {
        override val message: String = "Authentication failed"
    }
    
    object ServerError : JellyfinError() {
        override val message: String = "Server error occurred"
    }
    
    object NotFoundError : JellyfinError() {
        override val message: String = "Resource not found"
    }
    
    data class UnknownError(
        override val message: String = "An unknown error occurred"
    ) : JellyfinError()
    
    data class ApiError(
        val statusCode: Int,
        override val message: String
    ) : JellyfinError()
}

/**
 * Extension function to convert exceptions to JellyfinError
 */
fun Throwable.toJellyfinError(): JellyfinError = when (this) {
    is JellyfinError -> this
    is java.net.UnknownHostException -> JellyfinError.NetworkError
    is java.net.SocketTimeoutException -> JellyfinError.NetworkError
    is retrofit2.HttpException -> {
        when (code()) {
            401 -> JellyfinError.AuthenticationError
            404 -> JellyfinError.NotFoundError
            500 -> JellyfinError.ServerError
            else -> JellyfinError.ApiError(code(), message())
        }
    }
    else -> JellyfinError.UnknownError(message ?: "Unknown error")
}
```

### **4. Retry Mechanism Utility**

**File**: `app/src/main/java/com/rpeters/jellyfin/utils/RetryUtils.kt`

```kotlin
package com.rpeters.jellyfin.utils

import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Utility functions for retry operations
 */
object RetryUtils {
    
    /**
     * Executes an operation with exponential backoff retry
     */
    suspend fun <T> withRetry(
        times: Int = 3,
        initialDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block() // Last attempt
    }
    
    /**
     * Executes an operation with retry for specific exceptions
     */
    suspend fun <T> withRetryFor(
        exceptionClass: Class<out Exception>,
        times: Int = 3,
        initialDelay: Long = 1000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (exceptionClass.isInstance(e)) {
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    throw e
                }
            }
        }
        return block()
    }
}
```

## 🔧 **WEEK 2 IMPLEMENTATION**

### **5. Repository Size Reduction - Library Operations**

**File**: `app/src/main/java/com/rpeters/jellyfin/data/repository/JellyfinLibraryRepository.kt`

```kotlin
package com.rpeters.jellyfin.data.repository

import com.rpeters.jellyfin.core.AppConstants
import com.rpeters.jellyfin.core.JellyfinError
import com.rpeters.jellyfin.data.model.BaseItemDto
import com.rpeters.jellyfin.data.model.LibraryType
import com.rpeters.jellyfin.utils.RetryUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinLibraryRepository @Inject constructor(
    private val api: JellyfinApi,
    private val authRepository: JellyfinAuthRepository
) {
    
    /**
     * Get user libraries with retry mechanism
     */
    suspend fun getUserLibraries(): Flow<Result<List<BaseItemDto>>> = flow {
        try {
            val server = authRepository.getCurrentServer()
            if (server?.accessToken == null || server.userId == null) {
                emit(Result.failure(JellyfinError.AuthenticationError))
                return@flow
            }
            
            val libraries = RetryUtils.withRetry {
                api.getUserViews(
                    userId = server.userId,
                    includeExternalContent = true
                )
            }
            
            emit(Result.success(libraries.items))
        } catch (e: Exception) {
            emit(Result.failure(e.toJellyfinError()))
        }
    }
    
    /**
     * Get recently added items for a library
     */
    suspend fun getRecentlyAdded(
        libraryId: String,
        limit: Int = AppConstants.DEFAULT_PAGE_SIZE
    ): Flow<Result<List<BaseItemDto>>> = flow {
        try {
            val server = authRepository.getCurrentServer()
            if (server?.accessToken == null || server.userId == null) {
                emit(Result.failure(JellyfinError.AuthenticationError))
                return@flow
            }
            
            val items = RetryUtils.withRetry {
                api.getLatestMedia(
                    userId = server.userId,
                    parentId = libraryId,
                    limit = limit
                )
            }
            
            emit(Result.success(items))
        } catch (e: Exception) {
            emit(Result.failure(e.toJellyfinError()))
        }
    }
    
    /**
     * Get items by library type with filtering
     */
    suspend fun getItemsByType(
        libraryId: String,
        type: LibraryType,
        startIndex: Int = 0,
        limit: Int = AppConstants.DEFAULT_PAGE_SIZE
    ): Flow<Result<List<BaseItemDto>>> = flow {
        try {
            val server = authRepository.getCurrentServer()
            if (server?.accessToken == null || server.userId == null) {
                emit(Result.failure(JellyfinError.AuthenticationError))
                return@flow
            }
            
            val items = RetryUtils.withRetry {
                api.getItems(
                    userId = server.userId,
                    parentId = libraryId,
                    includeItemTypes = listOf(type.apiValue),
                    startIndex = startIndex,
                    limit = limit,
                    sortBy = listOf("SortName"),
                    sortOrder = listOf("Ascending")
                )
            }
            
            emit(Result.success(items.items))
        } catch (e: Exception) {
            emit(Result.failure(e.toJellyfinError()))
        }
    }
}
```

### **6. Enhanced Loading States**

**File**: `app/src/main/java/com/rpeters/jellyfin/ui/components/LoadingComponents.kt`

```kotlin
package com.rpeters.jellyfin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shimmerColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val transition = rememberInfiniteTransition()
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.LightGray.copy(alpha = 0.6f),
            shimmerColor,
            Color.LightGray.copy(alpha = 0.6f),
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(shimmerBrush)
    )
}

@Composable
fun SkeletonMovieCard() {
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
    ) {
        Column {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun SkeletonLibraryCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                )
            }
        }
    }
}
```

## 📋 **IMPLEMENTATION CHECKLIST**

### **Week 1 Tasks**
- [ ] Create `Extensions.kt` with rating functions
- [ ] Create `AppConstants.kt` with centralized constants
- [ ] Create `JellyfinError.kt` with standardized error types
- [ ] Create `RetryUtils.kt` with retry mechanisms
- [ ] Update all rating conversion calls to use extension functions
- [ ] Replace magic numbers with constants

### **Week 2 Tasks**
- [ ] Create `JellyfinLibraryRepository.kt`
- [ ] Move library operations from main repository
- [ ] Create `LoadingComponents.kt` with skeleton loading
- [ ] Update UI components to use new loading states
- [ ] Test all changes and ensure no regressions

### **Week 3 Tasks**
- [ ] Create `JellyfinPlaybackRepository.kt`
- [ ] Create `JellyfinMetadataRepository.kt`
- [ ] Complete repository refactoring
- [ ] Add comprehensive error handling
- [ ] Update all repository consumers

### **Week 4 Tasks**
- [ ] Add unit tests for new components
- [ ] Performance testing and optimization
- [ ] Code review and cleanup
- [ ] Documentation updates
- [ ] Final testing and validation

## 🎯 **SUCCESS METRICS**

### **Immediate (Week 1)**
- ✅ Eliminate 8+ duplicate rating conversion patterns
- ✅ Remove 20+ magic numbers
- ✅ Standardize error handling across components
- ✅ Add retry mechanisms for network operations

### **Short-term (Week 2-3)**
- ✅ Reduce main repository by 300-400 lines
- ✅ Improve loading user experience
- ✅ Better separation of concerns
- ✅ Enhanced error recovery

### **Long-term (Week 4+)**
- ✅ 80%+ test coverage
- ✅ < 2 second app launch time
- ✅ Smooth 60fps scrolling
- ✅ Comprehensive offline support

---

**This quick start guide provides immediate, actionable improvements that can be implemented right away to enhance code quality, maintainability, and user experience.**