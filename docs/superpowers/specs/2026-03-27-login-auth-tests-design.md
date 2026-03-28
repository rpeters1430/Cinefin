# Design Spec: Login and Authentication Tests Improvement

**Date:** 2026-03-27
**Status:** Approved
**Topic:** Improving test coverage for `AuthenticationViewModel`, `ServerConnectionViewModel`, and `ServerConnectionScreen`.

## 1. Objective
To improve the reliability and maintainability of the login and authentication flows by implementing a balanced test pyramid consisting of comprehensive ViewModel unit tests and contract-based UI instrumentation tests.

## 2. Approach: Balanced Test Pyramid
We will follow a "Balanced Test Pyramid" approach:
- **ViewModel Unit Tests (Logic):** Exhaustive coverage of all state transitions, error handling, and business logic.
- **UI Instrumentation Tests (Verification):** Verification that the UI correctly reflects the ViewModel state and that user interactions trigger the expected logic.

## 3. Architecture & Components

### 3.1 ViewModel Unit Tests (JVM/Robolectric)
- **Target Classes:** `AuthenticationViewModel`, `ServerConnectionViewModel`.
- **Dependencies:** `JellyfinAuthRepository`, `JellyfinUserRepository`, `SecureCredentialManager`, `CertificatePinningManager`, `ConnectivityChecker`.
- **Mocking Strategy:** Use `MockK` for all dependencies.

#### Test Cases for `AuthenticationViewModelTest`:
- **Initialization:** Verify `AuthenticationState` reflects `JellyfinAuthRepository` values.
- **`ensureValidTokenWithWait`:**
    - Success with valid token.
    - Success with re-authentication after expiry.
    - Failure with re-authentication after expiry.
- **`refreshAuthentication`:**
    - State transitions (Loading -> Success/Error).
    - Error message propagation.
- **`logout`:**
    - Repository/CredentialManager calls.
    - State reset.

#### Test Cases for `ServerConnectionViewModelTest`:
- **URL Normalization/Validation:** Test various input formats.
- **Connection Phases:** `None` -> `Testing` -> `Authenticating` -> `Connected`.
- **Error Handling:** Network errors, timeouts, server not found.
- **Biometric Integration:** Auto-login behavior based on availability.

### 3.2 UI Instrumentation Tests (Compose)
- **Target Screens:** `ServerConnectionScreen`.
- **Mocking Strategy:** Use `Hilt` to inject mock repositories/ViewModels.

#### Test Cases for `ServerConnectionScreenTest`:
- **Initial State:** URL input visibility and pre-population.
- **Connection Progress:** Progress indicator visibility and input disabling during `Testing`/`Authenticating`.
- **Error Handling:** Error dialog visibility and message correctness.
- **Success Path:** Navigation trigger on `Connected` state.

## 4. Implementation Plan Summary
1.  Create `AuthenticationViewModelTest.kt` in `app/src/test`.
2.  Enhance `ServerConnectionViewModelTest.kt` with missing scenarios.
3.  Create `ServerConnectionScreenTest.kt` in `app/src/androidTest`.
4.  Verify all tests pass locally and in CI.

## 5. Success Criteria
- `AuthenticationViewModel` has 90%+ branch coverage.
- `ServerConnectionScreen` has 100% coverage of its primary state transitions (Loading, Error, Success).
- No regressions in the login/auth flow.
