# Firebase Feature Flags Configuration Guide

This guide shows you how to manage immersive UI feature flags in the Firebase Console for granular A/B testing and rollout control.

---

## 🎛️ Available Feature Flags

### Master Toggle

| Flag Key | Type | Description | Default (Debug) | Default (Prod) |
|----------|------|-------------|-----------------|----------------|
| `enable_immersive_ui` | Boolean | Master toggle for all immersive UI features | ✅ `true` | ❌ `false` |

### Home & Main Screens

| Flag Key | Type | Description | Status | Default (Debug) | Default (Prod) |
|----------|------|-------------|--------|-----------------|----------------|
| `immersive_home_screen` | Boolean | Immersive home screen design | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_library_screen` | Boolean | Immersive library screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_search_screen` | Boolean | Immersive search screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_favorites_screen` | Boolean | Immersive favorites screen | ✅ Implemented | ✅ `true` | ❌ `false` |

### Detail Screens (Granular Control)

| Flag Key | Type | Description | Status | Default (Debug) | Default (Prod) |
|----------|------|-------------|--------|-----------------|----------------|
| `immersive_movie_detail` | Boolean | Immersive movie detail screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_tv_show_detail` | Boolean | Immersive TV show detail screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_tv_episode_detail` | Boolean | Immersive TV episode detail screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_tv_season` | Boolean | Immersive TV season screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_album_detail` | Boolean | Immersive album detail screen | ⏳ Not yet | ❌ `false` | ❌ `false` |

### Browse Screens

| Flag Key | Type | Description | Status | Default (Debug) | Default (Prod) |
|----------|------|-------------|--------|-----------------|----------------|
| `immersive_movies_browse` | Boolean | Immersive movies browse screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_tv_browse` | Boolean | Immersive TV browse screen | ✅ Implemented | ✅ `true` | ❌ `false` |
| `immersive_music_browse` | Boolean | Immersive music browse screen | ⏳ Not yet | ❌ `false` | ❌ `false` |

---

## 🔥 Firebase Console Setup

### Step 1: Access Remote Config

1. Go to **Firebase Console**: https://console.firebase.google.com/
2. Select your project: **Jellyfin Android App**
3. In the left sidebar, click **Remote Config** (under "Engage")

### Step 2: Add Parameters

For each feature flag you want to control remotely:

#### Example: Enable Immersive Home Screen for 10% of Users

1. Click **"Add parameter"**
2. **Parameter key**: `immersive_home_screen`
3. **Data type**: `Boolean`
4. **Default value**: `false`
5. Click **"Add condition"**
   - **Condition name**: `immersive_home_10_percent`
   - **"Applies if"**: Select **"User in random percentile"**
   - Set: `<= 10` (for 10% rollout)
   - **Value**: `true`
6. Click **"Save"**
7. Click **"Publish changes"** (top right)

#### Example: Enable for Specific Users (Beta Testers)

1. Click **"Add parameter"** or edit existing
2. **Parameter key**: `enable_immersive_ui`
3. Click **"Add condition"**
   - **Condition name**: `beta_testers`
   - **"Applies if"**: Select **"User property"**
   - Property: `user_type` (you need to set this in Analytics)
   - Operator: `Exactly matches`
   - Value: `beta`
   - **Value**: `true`
4. Click **"Save"** and **"Publish changes"**

#### Example: Enable for All Users

1. Edit parameter: `immersive_movie_detail`
2. Set **Default value**: `true`
3. Remove any conditions (or keep them for specific user segments)
4. Click **"Save"** and **"Publish changes"**

---

## 📊 Rollout Strategies

### Strategy 1: Gradual Rollout (Recommended)

**Goal**: Slowly increase percentage to monitor crashes/performance

1. **Week 1**: 5% of users → Monitor Firebase Crashlytics
2. **Week 2**: 25% of users → Check performance metrics
3. **Week 3**: 50% of users → Review user feedback
4. **Week 4**: 100% of users → Full rollout

**Firebase Setup**:
```
Parameter: immersive_home_screen
Condition: User in random percentile <= 5  → Value: true (Week 1)
Condition: User in random percentile <= 25 → Value: true (Week 2)
Condition: User in random percentile <= 50 → Value: true (Week 3)
Default value: true (Week 4)
```

### Strategy 2: Beta Testers First

**Goal**: Test with trusted users before public rollout

1. Set user property `user_type=beta` for beta testers (via Analytics)
2. Create condition: `user_type exactly matches beta` → `true`
3. Default value: `false`
4. After feedback, change default to `true` for all users

### Strategy 3: Per-Screen Rollout

**Goal**: Roll out each screen independently to isolate issues

1. Enable `enable_immersive_ui` globally: `true`
2. Start with **Home Screen**:
   - `immersive_home_screen` → 100% enabled
3. After 1 week, enable **Movie Detail**:
   - `immersive_movie_detail` → 100% enabled
4. Continue with other screens one by one

### Strategy 4: A/B Testing

**Goal**: Compare engagement metrics between old and new UI

1. Split users 50/50:
   - Condition: `User in random percentile <= 50` → `true`
   - Default: `false`
2. Track in Firebase Analytics:
   - Screen view duration
   - Click-through rates
   - Session length
3. After 2 weeks, keep the winning variant

---

## 🔍 Testing Feature Flags Locally

### Debug Builds (Auto-Enabled)

By default, **debug builds automatically enable all implemented immersive screens**:
- `enable_immersive_ui` → `true`
- `immersive_home_screen` → `true`
- `immersive_movie_detail` → `true`

### Override Local Defaults

To test with flags **disabled** in debug builds:

1. Edit `di/RemoteConfigModule.kt`:
   ```kotlin
   // Change this line
   val enableImmersiveUIDebug = BuildConfig.DEBUG // Currently true in debug

   // To force disable:
   val enableImmersiveUIDebug = false
   ```

2. Rebuild: `./gradlew installDebug`

### Test Firebase Remote Config Values

To test **actual Firebase values** (not local defaults):

1. **Clear app data** (to fetch fresh values):
   ```bash
   adb shell pm clear com.rpeters.jellyfin
   ```

2. **Launch app** and check logs:
   ```bash
   adb logcat | grep -E "DetailNavGraph|HomeScreen"
   ```

   You should see:
   ```
   DetailNavGraph: MovieDetail: enable_immersive_ui=true, immersive_movie_detail=true, using immersive: true
   HomeScreen: Feature flags - enable_immersive_ui: true, immersive_home_screen: true, using immersive: true
   ```

3. **Debug Mode** (instant fetch, no caching):
   - In Firebase Console → Remote Config → Settings
   - Enable **"Debug mode"** for your test device
   - Add your device's **Instance ID** (check logcat for `FirebaseInstanceId`)

---

## 🚨 Emergency Rollback

If immersive UI causes crashes or issues:

### Quick Disable (Immediate)

1. Go to **Firebase Console → Remote Config**
2. Edit parameter: `enable_immersive_ui`
3. Set **Default value**: `false`
4. Click **"Publish changes"**
5. Changes take effect **within 12 hours** (default fetch interval)

**For faster rollback**: Set fetch interval to 5 minutes in debug mode, but production defaults to 1 hour.

### Per-Screen Disable

If only one screen has issues (e.g., movie detail crashes):

1. Keep `enable_immersive_ui` → `true`
2. Disable specific screen: `immersive_movie_detail` → `false`
3. Publish changes
4. Only that screen reverts to old design

---

## 📱 Production Monitoring

### Key Metrics to Track

After enabling immersive UI, monitor these Firebase metrics:

1. **Crashlytics**:
   - Crash-free users percentage
   - Fatal exceptions related to immersive screens
   - Filter by screen: `ImmersiveHomeScreen`, `ImmersiveMovieDetailScreen`

2. **Performance Monitoring**:
   - Screen rendering time
   - Network request latency
   - Memory usage

3. **Analytics** (if set up):
   - Screen view duration
   - Engagement rate
   - User retention

### Setting Up Alerts

1. Go to **Firebase Console → Alerts**
2. Create alert:
   - **Metric**: Crash-free users
   - **Threshold**: Below 99%
   - **Action**: Email + Slack notification

---

## 🧪 Common Testing Scenarios

### Scenario 1: Test Home Screen Only

```
enable_immersive_ui = true
immersive_home_screen = true
immersive_movie_detail = false  ← Old design for movie detail
```

**Result**: Immersive home, classic detail screens

### Scenario 2: Test Movie Detail Only

```
enable_immersive_ui = true
immersive_home_screen = false  ← Old design for home
immersive_movie_detail = true
```

**Result**: Classic home, immersive movie detail

### Scenario 3: All Immersive

```
enable_immersive_ui = true
immersive_home_screen = true
immersive_movie_detail = true
immersive_tv_season = true  ← When implemented
```

**Result**: Full immersive experience

### Scenario 4: All Classic (Disable Everything)

```
enable_immersive_ui = false  ← Master kill switch
(All other flags ignored)
```

**Result**: 100% classic design, no immersive UI

---

## 📋 Checklist: Before Production Rollout

- [ ] All Firebase parameters created in Remote Config
- [ ] Default values set to `false` for production
- [ ] Gradual rollout conditions configured (5% → 25% → 50% → 100%)
- [ ] Crashlytics monitoring enabled
- [ ] Performance monitoring enabled
- [ ] Alert thresholds configured (crash-free users, performance)
- [ ] Rollback plan documented
- [ ] Team notified of rollout schedule
- [ ] Logcat logging verified (feature flag decisions visible)

---

## 🔧 Code References

### Where Flags Are Defined
- **Constants**: `app/src/main/java/com/rpeters/jellyfin/core/FeatureFlags.kt`
- **Defaults**: `app/src/main/java/com/rpeters/jellyfin/di/RemoteConfigModule.kt`
- **ViewModel**: `app/src/main/java/com/rpeters/jellyfin/ui/viewmodel/RemoteConfigViewModel.kt`

### Where Flags Are Used
- **Home Screen**: `app/src/main/java/com/rpeters/jellyfin/ui/navigation/HomeLibraryNavGraph.kt`
- **Movie Detail**: `app/src/main/java/com/rpeters/jellyfin/ui/navigation/DetailNavGraph.kt`

### Example Routing Code
```kotlin
// In navigation graph
val remoteConfigViewModel: RemoteConfigViewModel = hiltViewModel()
val useImmersiveUI = remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.ENABLE_IMMERSIVE_UI) &&
    remoteConfigViewModel.getBoolean(FeatureFlags.ImmersiveUI.IMMERSIVE_MOVIE_DETAIL)

if (useImmersiveUI) {
    ImmersiveMovieDetailScreen(...)
} else {
    MovieDetailScreen(...)  // Original screen
}
```

---

## 💡 Pro Tips

1. **Start Small**: Enable for 5% of users first, not 50%
2. **Monitor Daily**: Check Crashlytics every day during rollout
3. **Use Conditions**: Don't just flip default values—use conditions for control
4. **Document Changes**: Keep a changelog of when each flag was enabled
5. **Test Combinations**: Test flags together (e.g., home + detail both enabled)
6. **Cache Awareness**: Remote Config caches values—clear app data to test fresh fetch
7. **Debug Logs**: Always check logcat for feature flag decisions
8. **Rollback Plan**: Know how to disable quickly if issues arise
9. **Fetch Interval**: Production = 1 hour, Debug = 0 seconds
10. **User Segmentation**: Use user properties for beta testers, power users, etc.

---

## 🎯 Quick Reference: Flag Hierarchy

```
enable_immersive_ui (Master)
├── immersive_home_screen ✅
├── immersive_library_screen ✅
├── immersive_search_screen ✅
├── immersive_favorites_screen ✅
├── Detail Screens
│   ├── immersive_movie_detail ✅
│   ├── immersive_tv_show_detail ✅
│   ├── immersive_tv_episode_detail ✅
│   ├── immersive_tv_season ✅
│   └── immersive_album_detail ⏳
└── Browse Screens
    ├── immersive_movies_browse ✅
    ├── immersive_tv_browse ✅
    └── immersive_music_browse ⏳

Legend:
✅ Implemented and ready for rollout (8 screens)
⏳ Not yet implemented (ignore flag for now)
```

---

**Need Help?** Check `TESTING_IMMERSIVE_UI.md` for local testing, or `IMMERSIVE_UI_PROGRESS.md` for implementation status.
