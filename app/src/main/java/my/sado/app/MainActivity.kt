package my.sado.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import my.sado.app.data.Store
import my.sado.app.ui.AiFoodScreen
import my.sado.app.ui.BackupScreen
import my.sado.app.ui.BarcodeScreen
import my.sado.app.ui.BodyScreen
import my.sado.app.ui.CalendarScreen
import my.sado.app.ui.CameraScreen
import my.sado.app.ui.CompareScreen
import my.sado.app.ui.DoneScreen
import my.sado.app.ui.ExerciseDetailScreen
import my.sado.app.ui.Feedback
import my.sado.app.ui.FitnessTestScreen
import my.sado.app.ui.GalleryScreen
import my.sado.app.ui.HealthScreen
import my.sado.app.ui.HomeScreen
import my.sado.app.ui.JourneyScreen
import my.sado.app.ui.LibraryScreen
import my.sado.app.ui.NutritionScreen
import my.sado.app.ui.PhotoViewerScreen
import my.sado.app.ui.PlanResultScreen
import my.sado.app.ui.PlannerScreen
import my.sado.app.ui.PrimaryButton
import my.sado.app.ui.ProfileSetupScreen
import my.sado.app.ui.SadoColors
import my.sado.app.ui.SadoTheme
import my.sado.app.ui.SessionScreen
import my.sado.app.ui.SettingsScreen
import my.sado.app.ui.SportScreen
import my.sado.app.ui.VoiceScreen
import my.sado.app.ui.WelcomeScreen
import my.sado.app.ui.WgerDetailScreen
import my.sado.app.ui.WorkoutListScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("workout", "Workout", Icons.Filled.PlayArrow),
    Tab("body", "Badan", Icons.Filled.Person),
    Tab("rekod", "Rekod", Icons.Filled.DateRange),
)

// FragmentActivity (a ComponentActivity) so BiometricPrompt can attach for the app lock
class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Feedback.init(applicationContext)
        Coach.init(applicationContext)
        Reminders.schedule(applicationContext)
        my.sado.app.data.AutoBackup.schedule(applicationContext)
        my.sado.app.data.AutoBackup.runIfDue(applicationContext)
        unlocked = !Store.state.value.settings.appLock
        enableEdgeToEdge()
        // Health Connect opens the app with these actions to show why Sado reads health data
        val fromHealth = intent?.action in setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE")
        val start = when {
            Store.state.value.profile == null -> "welcome"
            fromHealth -> "health"
            else -> "home"
        }
        setContent {
            SadoTheme {
                if (!unlocked) {
                    Column(
                        Modifier.fillMaxSize().background(SadoColors.Bg).safeDrawingPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        Image(painterResource(R.drawable.tier1), null, Modifier.height(160.dp))
                        Text("Sado dikunci", color = SadoColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        PrimaryButton("Buka dengan cap jari / PIN") { authenticate() }
                    }
                    return@SadoTheme
                }
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                Column(Modifier.fillMaxSize().background(SadoColors.Bg).safeDrawingPadding()) {
                    Box(Modifier.weight(1f)) {
                        SadoNav(nav, start)
                        my.sado.app.ui.VoiceReportChip(Modifier.align(Alignment.BottomCenter).padding(16.dp))
                        my.sado.app.ui.AchievementToast(route, onOpen = { nav.navigate("achievements") { launchSingleTop = true } }, Modifier.align(Alignment.TopCenter))
                    }
                    if (tabs.any { it.route == route }) TabBar(
                        route,
                        onSelect = { r -> nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                        // the flex button is today's session, same as START WORKOUT on Home
                        onFlex = { nav.navigate(if (Store.state.value.plan == null) "planner" else "preview/plan") },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!unlocked) authenticate()
        // the watch picked once reconnects by itself whenever the app comes back
        if (Store.state.value.settings.hrMode == 1) HeartRate.reconnectSaved(this, Store.state.value.settings.hrDevice)
    }

    override fun onStop() {
        super.onStop()
        if (Store.state.value.settings.appLock && !isChangingConfigurations) unlocked = false
    }

    private fun authenticate() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Sado")
            .setSubtitle("Gambar progress dan data kau dikunci")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
        runCatching { prompt.authenticate(info) }
    }
}

// floating tab bar: two tabs each side of a raised round flex button that starts today's workout
@androidx.compose.runtime.Composable
internal fun TabBar(route: String?, onSelect: (String) -> Unit, onFlex: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(SadoColors.Surface).border(1.dp, SadoColors.Outline, RoundedCornerShape(28.dp)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, t ->
                if (i == 2) Box(Modifier.width(76.dp))
                val on = route == t.route
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(role = Role.Tab) { Feedback.tap(); onSelect(t.route) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier.size(width = 52.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) SadoColors.Accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, contentDescription = null, tint = if (on) SadoColors.OnAccent else SadoColors.TextMuted) }
                    Text(t.label, color = if (on) SadoColors.Text else SadoColors.TextMuted, fontSize = 11.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
        // sits half above the bar, ringed in the page colour so it reads as cut out of it
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(70.dp).clip(CircleShape).background(SadoColors.Bg).padding(5.dp)
                .clip(CircleShape).background(SadoColors.Accent).clickable(role = Role.Button) { Feedback.tap(); onFlex() },
            contentAlignment = Alignment.Center,
        ) { Icon(my.sado.app.ui.SadoIcons.Flex, contentDescription = "Mula workout hari ni", tint = SadoColors.OnAccent, modifier = Modifier.size(32.dp)) }
    }
}

@androidx.compose.runtime.Composable
private fun SadoNav(nav: NavHostController, start: String) {
    fun home() = nav.navigate("home") { popUpTo(0) }
    NavHost(navController = nav, startDestination = start) {
        composable("welcome") { WelcomeScreen(onStart = { nav.navigate("profile") }) }
        composable("profile") {
            ProfileSetupScreen(onBack = { nav.popBackStack() }, onDone = {
                val st = Store.state.value
                if (st.health == null) nav.navigate("healthcheck") { if (st.plan != null) popUpTo("profile") { inclusive = true } } else if (st.plan == null) nav.navigate("planner") else nav.popBackStack()
            })
        }
        composable("healthcheck") {
            my.sado.app.ui.HealthCheckScreen(onBack = { nav.popBackStack() }, onDone = {
                if (Store.state.value.plan == null) nav.navigate("planner") { popUpTo("healthcheck") { inclusive = true } } else nav.popBackStack()
            })
        }
        composable("rest") { my.sado.app.ui.RestScreen(onBack = { nav.popBackStack() }) }
        composable("agegate") {
            my.sado.app.ui.AgeGateScreen(onFixAge = { nav.navigate("profile") }, onSettings = { nav.navigate("settings") }, onAdult = { home() })
        }
        composable("planner") {
            PlannerScreen(onBack = { nav.popBackStack() }, onDone = {
                val s = Store.state.value
                // first setup only: offer the placement test before the plan
                nav.navigate(if (!s.proveAsked && s.logs.isEmpty() && my.sado.app.data.Progress.proveOffered(s)) "prove" else "plan")
            })
        }
        composable("prove") { my.sado.app.ui.ProveScreen(onDone = { nav.navigate("plan") { popUpTo("prove") { inclusive = true } } }) }
        composable("plan") {
            PlanResultScreen(
                onExercise = { nav.navigate("exercise/$it") },
                onUse = { home() },
                onRedo = { nav.navigate("planner") { popUpTo("planner") { inclusive = true } } },
            )
        }
        composable("home") {
            // the only way in under 18 now is an old save or a restored backup
            androidx.compose.runtime.LaunchedEffect(Unit) { if (my.sado.app.data.Care.underAge(Store.state.value.profile)) nav.navigate("agegate") { popUpTo(0) } }
            HomeScreen(
                onStart = { nav.navigate("preview/plan") }, onPlanner = { nav.navigate("planner") }, onPlanView = { nav.navigate("plan") },
                onJourney = { nav.navigate("journey") }, onSport = { nav.navigate("sport") }, onTest = { nav.navigate("test") },
                onHealth = { nav.navigate("health") }, onSettings = { nav.navigate("settings") }, onPet = { nav.navigate("pet") },
                onRest = { nav.navigate("rest") }, onHealthCheck = { nav.navigate("healthcheck") }, onPetEvolve = { nav.navigate("pet-evolve") },
                onMini = { nav.navigate("session/mini:plan") },
            )
        }
        composable("pet") { my.sado.app.ui.PetScreen(onEvolve = { nav.navigate("pet-evolve") }, onBack = { nav.popBackStack() }) }
        composable("pet-evolve") { my.sado.app.ui.PetEvolveScreen(onHome = { if (!nav.popBackStack("home", false)) nav.navigate("home") }, onBack = { nav.popBackStack() }) }
        composable("journey") { JourneyScreen(onBack = { nav.popBackStack() }) }
        composable("rekod") { CalendarScreen(onTest = { nav.navigate("test") }, onSport = { nav.navigate("sport") }, onAchievements = { nav.navigate("achievements") }) }
        composable("achievements") { my.sado.app.ui.AchievementsScreen(onBack = { nav.popBackStack() }) }
        composable("test") { FitnessTestScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() }) }
        composable("sport") { SportScreen(onBack = { nav.popBackStack() }, onHealth = { nav.navigate("health") }) }
        composable("health") { HealthScreen(onBack = { if (!nav.popBackStack()) home() }) }
        composable("workout") { WorkoutListScreen(onStart = { nav.navigate("preview/$it") }, onLibrary = { nav.navigate("library") }, onEditTemplate = { id -> nav.navigate("mytpl?id=" + (id ?: "")) }) }
        composable("mytpl?id={id}", listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) {
            my.sado.app.ui.TemplateEditorScreen(id = it.arguments?.getString("id")?.ifBlank { null }, onBack = { nav.popBackStack() })
        }
        composable("library") { LibraryScreen(onOpen = { nav.navigate("wger/$it") }) }
        composable("body") { BodyScreen(onCamera = { nav.navigate("camera") }, onGallery = { nav.navigate("gallery") }, onFood = { nav.navigate("food") }) }
        composable("camera") { CameraScreen(onBack = { nav.popBackStack() }, onSaved = { nav.navigate("gallery") { popUpTo("body") } }) }
        composable("gallery") { GalleryScreen(onBack = { nav.popBackStack() }, onCompare = { a, b -> nav.navigate("compare/$a/$b") }, onViewer = { a, f -> nav.navigate("viewer/$a" + if (f != null) "?start=$f" else "") }) }
        composable(
            "viewer/{angle}?start={start}",
            listOf(navArgument("angle") { type = NavType.IntType }, navArgument("start") { type = NavType.StringType; nullable = true }),
        ) {
            PhotoViewerScreen(it.arguments?.getInt("angle") ?: 0, it.arguments?.getString("start"), onBack = { nav.popBackStack() })
        }
        composable("compare/{a}/{b}") { CompareScreen(it.arguments?.getString("a") ?: "", it.arguments?.getString("b") ?: "", onBack = { nav.popBackStack() }) }
        composable("food") { NutritionScreen(onBack = { nav.popBackStack() }, onAi = { nav.navigate("foodai") }, onBarcode = { nav.navigate("barcode") }) }
        composable("foodai") { AiFoodScreen(onBack = { nav.popBackStack() }) }
        composable("barcode") { BarcodeScreen(onBack = { nav.popBackStack() }) }
        composable("voice") { VoiceScreen(onBack = { nav.popBackStack() }, onLibrary = { nav.navigate("voicelib") }) }
        composable("voicelib") { my.sado.app.ui.VoiceLibraryScreen(onBack = { nav.popBackStack() }) }
        composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
        composable("settings") {
            SettingsScreen(
                onProfile = { nav.navigate("profile") }, onPlanner = { nav.navigate("planner") },
                onReset = { nav.navigate("welcome") { popUpTo(0) } }, onBackup = { nav.navigate("backup") },
                onHealth = { nav.navigate("health") }, onVoice = { nav.navigate("voice") }, onBack = { nav.popBackStack() },
                onRest = { nav.navigate("rest") }, onHealthCheck = { nav.navigate("healthcheck") },
            )
        }
        composable("preview/{source}", listOf(navArgument("source") { type = NavType.StringType })) {
            val source = it.arguments?.getString("source") ?: "plan"
            my.sado.app.ui.WorkoutPreviewScreen(
                source, onBack = { nav.popBackStack() }, onExercise = { id -> nav.navigate("exercise/$id") },
                onStart = { nav.navigate("session/$source") },
            )
        }
        composable("session/{source}", listOf(navArgument("source") { type = NavType.StringType })) {
            SessionScreen(
                source = it.arguments?.getString("source") ?: "plan",
                onExercise = { id -> nav.navigate("exercise/$id") },
                onFinished = { nav.navigate("done") { popUpTo("home") } },
                onExit = { nav.popBackStack() },
                onMini = { nav.navigate("session/mini:plan") { popUpTo("home") } },
            )
        }
        composable("done")
 { DoneScreen(onHome = { home() }) }
        composable("exercise/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
            ExerciseDetailScreen(it.arguments?.getString("id") ?: "push", onBack = { nav.popBackStack() })
        }
        composable("wger/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
            WgerDetailScreen(it.arguments?.getInt("id") ?: 0, onBack = { nav.popBackStack() })
        }
    }
}
