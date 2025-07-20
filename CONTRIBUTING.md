# Contributing to Jellyfin Android Client

We love your input! We want to make contributing to the Jellyfin Android Client as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## 🚀 Development Process

We use GitHub to host code, track issues and feature requests, as well as accept pull requests.

### **Pull Request Process**

1. **Fork the repo** and create your branch from `main`
2. **Follow the coding standards** outlined below
3. **Add tests** for any new functionality
4. **Ensure the test suite passes** (`./gradlew testDebugUnitTest`)
5. **Lint your code** (`./gradlew lintDebug`)
6. **Update documentation** as needed
7. **Create a pull request**

### **Branch Naming**
- `feature/description` - for new features
- `bugfix/description` - for bug fixes
- `hotfix/description` - for urgent fixes
- `docs/description` - for documentation updates

## 🐛 Bug Reports

We use GitHub issues to track public bugs. Report a bug by [opening a new issue](https://github.com/rpeters1430/JellyfinAndroid/issues/new).

### **Great Bug Reports Include:**

- **Clear summary** - what you expected vs. what happened
- **Environment details:**
  - Android version
  - Device model
  - App version
  - Jellyfin server version
- **Reproduction steps** - be specific!
- **Screenshots/logs** when applicable
- **Error messages** if any

**Example:**
```
**Expected Behavior:** Tapping a movie should open the detail screen
**Actual Behavior:** App crashes with NullPointerException
**Steps to Reproduce:**
1. Open the app
2. Navigate to Movies library
3. Tap on any movie poster
**Environment:** Android 13, Pixel 7, App v1.0.0, Jellyfin 10.8.0
```

## 💡 Feature Requests

Feature requests are welcome! Please provide:

- **Clear description** of the feature
- **Use case** - why is this needed?
- **Proposed solution** if you have ideas
- **Alternatives considered**

## 🏗️ Development Setup

### **Prerequisites**
- Android Studio Iguana or later
- JDK 17
- Android SDK 31+
- Git

### **Setup Steps**
```bash
# Clone your fork
git clone https://github.com/rpeters1430/JellyfinAndroid.git
cd JellyfinAndroid

# Add upstream remote
git remote add upstream https://github.com/originaluser/JellyfinAndroid.git

# Create a feature branch
git checkout -b feature/my-new-feature

# Make your changes and commit
git commit -am 'Add some feature'

# Push to your fork
git push origin feature/my-new-feature

# Create a Pull Request
```

## 📋 Coding Standards

### **Kotlin Style**
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use **4 spaces** for indentation
- **Line length:** 120 characters max
- **Naming:**
  - Classes: `PascalCase`
  - Functions/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`

### **Android Specific**
- Use **Jetpack Compose** for UI components
- Follow **Material 3** design guidelines
- Use **ViewModels** for state management
- Implement **proper lifecycle awareness**

### **Architecture**
- **MVVM pattern** with Repository
- **Single source of truth** principle
- **Unidirectional data flow**
- **Proper error handling**

### **Code Organization**
```kotlin
// Good: Clear, descriptive naming
class MovieDetailViewModel @Inject constructor(
    private val repository: JellyfinRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()
    
    fun loadMovieDetails(movieId: String) {
        // Implementation
    }
}

// Bad: Unclear naming, no documentation
class VM @Inject constructor(private val repo: Repo) : ViewModel() {
    var state = MutableStateFlow(State())
    fun load(id: String) { /* */ }
}
```

## 🧪 Testing Guidelines

### **Unit Tests**
- **Test business logic** in ViewModels and Repositories
- **Mock external dependencies** (network, database)
- **Use descriptive test names**
- **Follow AAA pattern** (Arrange, Act, Assert)

```kotlin
@Test
fun `loadMovieDetails should update uiState with movie data when successful`() {
    // Arrange
    val movieId = "123"
    val expectedMovie = createTestMovie()
    coEvery { repository.getMovie(movieId) } returns ApiResult.Success(expectedMovie)
    
    // Act
    viewModel.loadMovieDetails(movieId)
    
    // Assert
    assertEquals(expectedMovie, viewModel.uiState.value.movie)
}
```

### **UI Tests**
- **Test user interactions** and navigation
- **Verify UI state changes** based on data
- **Check accessibility** compliance

## 🎨 UI/UX Guidelines

### **Material 3 Compliance**
- Use **theme colors** from `MaterialTheme.colorScheme`
- Follow **elevation system** for depth
- Implement **motion and transitions** appropriately
- Ensure **accessibility standards**

### **Responsive Design**
- Support **different screen sizes** (phones, tablets)
- Use **adaptive layouts** where appropriate
- Test on **various devices** and orientations

### **Loading States**
- Show **progress indicators** for long operations
- Implement **skeleton screens** for content loading
- Provide **error states** with retry options

## 📝 Documentation

### **Code Documentation**
- **Document public APIs** with KDoc
- **Explain complex logic** with inline comments
- **Keep README updated** with new features

### **Commit Messages**
Follow [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add movie detail screen with cast information
fix: resolve crash when loading empty library
docs: update API integration documentation
refactor: simplify authentication flow
```

## 🚦 Code Review Process

### **What We Look For**
- **Functionality** - does it work as intended?
- **Code quality** - is it clean and maintainable?
- **Performance** - any potential bottlenecks?
- **Security** - proper handling of sensitive data?
- **Tests** - adequate coverage for changes?

### **Review Timeline**
- Most PRs are reviewed within **2-3 business days**
- Complex changes may take longer
- Feel free to **ping reviewers** if needed

## 🏷️ Issue Labels

- `bug` - Something isn't working
- `enhancement` - New feature or request
- `documentation` - Improvements or additions to docs
- `good first issue` - Good for newcomers
- `help wanted` - Extra attention is needed
- `priority: high` - Critical issues
- `priority: low` - Nice to have features

## 📄 License

By contributing, you agree that your contributions will be licensed under the same license as the project (MIT License).

## 🤔 Questions?

Don't hesitate to ask! You can:
- **Open an issue** for general questions
- **Start a discussion** for broader topics
- **Reach out** to maintainers directly

## 🙏 Recognition

Contributors will be recognized in:
- **README acknowledgments**
- **Release notes** for significant contributions
- **GitHub contributors** page

Thank you for helping make the Jellyfin Android Client better! 🎉
