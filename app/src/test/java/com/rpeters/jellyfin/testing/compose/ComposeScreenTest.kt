package com.rpeters.jellyfin.testing.compose

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.window.layout.WindowLayoutInfo
import com.rpeters.jellyfin.ui.adaptive.LocalAdaptiveLayoutConfig
import com.rpeters.jellyfin.ui.adaptive.LocalWindowSizeClass
import com.rpeters.jellyfin.ui.adaptive.rememberAdaptiveLayoutConfig
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rpeters.jellyfin.TestCinefinApplication
import com.rpeters.jellyfin.data.preferences.ThemePreferences
import com.rpeters.jellyfin.ui.theme.JellyfinAndroidTheme
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import org.junit.After
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/**
 * Hands out test ViewModels to `hiltViewModel()` calls inside composables.
 *
 * Register a (usually mocked) instance with [provide] before rendering. A ViewModel a screen asks
 * for that was not provided becomes a relaxed mock, which suits incidental ViewModels whose state
 * the test doesn't care about; real Hilt wiring is never used.
 */
object TestViewModels {
    private val instances = mutableMapOf<Class<*>, ViewModel>()

    fun <T : ViewModel> provide(type: Class<T>, instance: T) {
        instances[type] = instance
    }

    inline fun <reified T : ViewModel> provide(instance: T) = provide(T::class.java, instance)

    fun clear() = instances.clear()

    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = create(modelClass)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            instances.getOrPut(modelClass) { io.mockk.mockkClass(modelClass.kotlin, relaxed = true) } as T
    }
}

/**
 * A ComponentActivity that satisfies `HiltViewModelFactory` without a real Hilt component: it
 * reports no Hilt ViewModel keys, so every request goes to [TestViewModels.factory].
 */
class HiltTestHostActivity : ComponentActivity(), GeneratedComponentManager<Any> {

    private val component: Any = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(
            GeneratedComponent::class.java,
            Class.forName("dagger.hilt.android.internal.lifecycle.HiltViewModelFactory\$ActivityCreatorEntryPoint"),
        ),
    ) { proxy, method, args ->
        when (method.name) {
            "getViewModelKeys" -> emptyMap<Class<*>, Boolean>()
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "HiltTestHostActivity.component"
            else -> null
        }
    }

    override fun generatedComponent(): Any = component

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = TestViewModels.factory
}

/** Declares [HiltTestHostActivity] to Robolectric's package manager so it can be launched. */
private class RegisterHostActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val component = ComponentName(app, HiltTestHostActivity::class.java)
            val pm = shadowOf(app.packageManager)
            pm.addOrUpdateActivity(
                ActivityInfo().apply {
                    name = component.className
                    packageName = component.packageName
                    theme = android.R.style.Theme_Material_Light_NoActionBar
                },
            )
            pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN))
            base.evaluate()
        }
    }
}

/**
 * Base for Robolectric Compose tests that render a screen or component on the JVM.
 *
 * Subclasses call [render] with the composable under test; it is wrapped in the app theme so
 * MaterialTheme and the Cinefin expressive tokens resolve as they do in the app.
 *
 * Abstract on purpose although it has no abstract members: it carries @RunWith, so a concrete
 * class would be run by JUnit as a test class with no tests and fail with "No runnable methods".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestCinefinApplication::class, qualifiers = "w411dp-h2400dp-xxhdpi")
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
abstract class ComposeScreenTest { // skipcq

    val compose: AndroidComposeTestRule<ActivityScenarioRule<HiltTestHostActivity>, HiltTestHostActivity> =
        createAndroidComposeRule<HiltTestHostActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterHostActivityRule()).around(compose)

    @After
    fun clearTestViewModels() = TestViewModels.clear()

    protected fun render(
        theme: ThemePreferences = ThemePreferences.DEFAULT,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            JellyfinAndroidTheme(themePreferences = theme) {
                // Same locals JellyfinApp provides at its root.
                val windowSizeClass = calculateWindowSizeClass(activity = compose.activity)
                val adaptiveLayoutConfig = rememberAdaptiveLayoutConfig(windowSizeClass, WindowLayoutInfo(emptyList()))
                CompositionLocalProvider(
                    LocalWindowSizeClass provides windowSizeClass,
                    LocalAdaptiveLayoutConfig provides adaptiveLayoutConfig,
                ) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    /** Asserts something was composed; tolerates the extra root a dialog or popup adds. */
    protected fun assertRendered() {
        org.junit.Assert.assertTrue(
            "Nothing was composed",
            compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
