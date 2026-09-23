package com.rpeters.jellyfin.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Flushes any write to the shared `login_preferences` DataStore that is still parked on
 * [scheduler].
 *
 * DataStore runs `edit {}` transforms in the caller's context. ServerConnectionViewModel writes
 * credentials inside `withContext(dispatchers.io + NonCancellable)`, and tests map `io` to a test
 * dispatcher, so a test that ends mid-auto-login leaves that write waiting on a dispatcher that is
 * never advanced again. Cancelling viewModelScope does not help (NonCancellable), and DataStore
 * serializes writes, so every later `edit` in the same JVM hangs until `runTest` times out.
 *
 * A sentinel edit is queued from a real thread; because writes are serialized it can only
 * complete after any parked write, so the scheduler is advanced until the sentinel lands.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Context.drainLoginDataStore(scheduler: TestCoroutineScheduler, timeoutMs: Long = 5_000) {
    val done = CountDownLatch(1)
    thread(isDaemon = true, name = "login-datastore-drain") {
        runBlocking { dataStore.edit { } }
        done.countDown()
    }
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!done.await(5, TimeUnit.MILLISECONDS)) {
        scheduler.advanceUntilIdle()
        check(System.currentTimeMillis() < deadline) {
            "login_preferences DataStore did not become idle within ${timeoutMs}ms"
        }
    }
}
