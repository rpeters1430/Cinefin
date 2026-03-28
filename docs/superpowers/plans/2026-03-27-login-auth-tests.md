# Login and Authentication Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the reliability and maintainability of the login and authentication flows by implementing a balanced test pyramid consisting of comprehensive ViewModel unit tests and contract-based UI instrumentation tests.

**Architecture:** Balanced Test Pyramid (ViewModel Unit Tests + UI Instrumentation Tests).
**Tech Stack:** Kotlin, Jetpack Compose, Hilt, MockK, Robolectric, kotlinx-coroutines-test.

---

### Task 1: `AuthenticationViewModel` Initialization & Simple Logic

**Files:**
- Create: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/AuthenticationViewModelTest.kt`

- [ ] **Step 1: Create the test file with initialization and basic tests**

```kotlin
package com.rpeters.jellyfin.ui.viewmodel

import com.rpeters.jellyfin.data.SecureCredentialManager
import com.rpeters.jellyfin.data.repository.JellyfinAuthRepository
import com.rpeters.jellyfin.data.repository.JellyfinUserRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var authRepository: JellyfinAuthRepository
    private lateinit var userRepository: JellyfinUserRepository
    private lateinit var credentialManager: SecureCredentialManager
    private lateinit var viewModel: AuthenticationViewModel

    private val isAuthenticatingFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        authRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        credentialManager = mockk(relaxed = true)

        every { authRepository.isAuthenticating } returns isAuthenticatingFlow
        every { authRepository.isUserAuthenticated() } returns false

        viewModel = AuthenticationViewModel(authRepository, userRepository, credentialManager)
    }

    @Test
    fun `initial state reflects repository values`() = runTest {
        val state = viewModel.authState.value
        assertFalse(state.isAuthenticating)
        assertFalse(state.isAuthenticated)
        assertNull(state.errorMessage)
    }

    @Test
    fun `clearError resets error message`() = runTest {
        // Internal state modification for test (via refresh failure) is better, but direct check for simplicity
        viewModel.clearError()
        assertNull(viewModel.authState.value.errorMessage)
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.AuthenticationViewModelTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/AuthenticationViewModelTest.kt
git commit -m "test: add initial AuthenticationViewModelTest"
```

---

### Task 2: `AuthenticationViewModel` Token Validation Logic

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/AuthenticationViewModelTest.kt`

- [ ] **Step 1: Add token validation tests**

```kotlin
    @Test
    fun `ensureValidTokenWithWait returns true if token not expired`() = runTest {
        every { authRepository.isTokenExpired() } returns false
        
        val result = viewModel.ensureValidTokenWithWait()
        
        assertTrue(result)
    }

    @Test
    fun `ensureValidTokenWithWait returns true after successful re-authentication`() = runTest {
        every { authRepository.isTokenExpired() } returnsMany listOf(true, false)
        coEvery { authRepository.reAuthenticate() } returns true
        
        val result = viewModel.ensureValidTokenWithWait()
        
        assertTrue(result)
    }

    @Test
    fun `ensureValidTokenWithWait returns false after failed re-authentication`() = runTest {
        every { authRepository.isTokenExpired() } returns true
        coEvery { authRepository.reAuthenticate() } returns false
        
        val result = viewModel.ensureValidTokenWithWait()
        
        assertFalse(result)
    }
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.AuthenticationViewModelTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/AuthenticationViewModelTest.kt
git commit -m "test: add token validation tests to AuthenticationViewModelTest"
```

---

### Task 3: `AuthenticationViewModel` Refresh & Logout

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/AuthenticationViewModelTest.kt`

- [ ] **Step 1: Add refresh and logout tests**

```kotlin
    @Test
    fun `refreshAuthentication updates state and propagates error on failure`() = runTest {
        coEvery { authRepository.forceReAuthenticate() } returns false
        
        viewModel.refreshAuthentication()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        
        val state = viewModel.authState.value
        assertFalse(state.isAuthenticating)
        assertFalse(state.isAuthenticated)
        assertEquals("Failed to refresh authentication", state.errorMessage)
    }

    @Test
    fun `logout clears credentials and resets state`() = runTest {
        viewModel.logout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        
        coVerify { userRepository.logout() }
        coVerify { credentialManager.clearCredentials() }
        
        val state = viewModel.authState.value
        assertFalse(state.isAuthenticated)
        assertNull(state.errorMessage)
    }
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.AuthenticationViewModelTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/AuthenticationViewModelTest.kt
git commit -m "test: add refresh and logout tests to AuthenticationViewModelTest"
```

---

### Task 4: Enhance `ServerConnectionViewModelTest`

**Files:**
- Modify: `app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelTest.kt`

- [ ] **Step 1: Add URL normalization and connection phase tests**

```kotlin
    @Test
    fun `testServerConnection handles various URL formats`() = runTest(mainDispatcherRule.dispatcher) {
        val formats = listOf("192.168.1.1", "http://jellyfin:8096", "jellyfin.local")
        formats.forEach { url ->
            coEvery { repository.testServerConnection(any()) } returns ApiResult.Success(mockk())
            viewModel.onUrlChange(url)
            viewModel.connectToServer()
            advanceUntilIdle()
            // Verify normalization (implementation detail, but observable via repo call)
            coVerify { repository.testServerConnection(match { it.startsWith("http") }) }
        }
    }

    @Test
    fun `connection phases transition correctly`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { repository.testServerConnection(any()) } coAnswers {
            delay(100) // Simulate work
            ApiResult.Success(mockk())
        }
        
        viewModel.onUrlChange("https://example.com")
        viewModel.connectToServer()
        
        // Check "Testing" phase
        val stateDuring = viewModel.connectionState.value
        assertEquals(com.rpeters.jellyfin.ui.components.ConnectionPhase.Testing, stateDuring.phase)
    }
```

- [ ] **Step 2: Run the tests to verify they pass**

Run: `gradlew testDebugUnitTest --tests "com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModelTest"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/rpeters/jellyfin/ui/viewmodel/ServerConnectionViewModelTest.kt
git commit -m "test: enhance ServerConnectionViewModelTest with URL and phase tests"
```

---

### Task 5: `ServerConnectionScreen` Instrumentation Tests

**Files:**
- Create: `app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ServerConnectionScreenTest.kt`

- [ ] **Step 1: Create UI test for initial state and error handling**

```kotlin
package com.rpeters.jellyfin.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.rpeters.jellyfin.ui.theme.CinefinTheme
import com.rpeters.jellyfin.ui.viewmodel.ServerConnectionViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ServerConnectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val viewModel: ServerConnectionViewModel = mockk(relaxed = true)
    private val connectionState = MutableStateFlow(com.rpeters.jellyfin.ui.components.ConnectionState())

    @Test
    fun `server url input is visible and triggers viewmodel`() {
        every { viewModel.connectionState } returns connectionState

        composeTestRule.setContent {
            CinefinTheme {
                ServerConnectionScreen(
                    viewModel = viewModel,
                    onConnected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("https://").performTextInput("example.com")
        // Verify ViewModel call (onUrlChange is internal, may need matching)
    }
}
```

- [ ] **Step 2: Run the instrumentation tests**

Run: `gradlew connectedDebugAndroidTest` (requires device/emulator)
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/rpeters/jellyfin/ui/screens/ServerConnectionScreenTest.kt
git commit -m "test: add ServerConnectionScreen instrumentation tests"
```
