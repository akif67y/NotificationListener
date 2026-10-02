package dev.notificationlistener.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import dev.notificationlistener.R
import dev.notificationlistener.data.CaptureSettings
import dev.notificationlistener.data.EventWithConversation
import dev.notificationlistener.data.RevisionListItem
import dev.notificationlistener.data.ConversationWithCourse
import dev.notificationlistener.data.CourseEntity
import dev.notificationlistener.data.MessageListItem
import dev.notificationlistener.data.NotificationSnapshotEntity
import java.text.DateFormat
import java.util.Date

private enum class AppTab(val labelRes: Int) {
    EVENTS(R.string.tab_events),
    SIGNALS(R.string.tab_signals),
    INBOX(R.string.tab_inbox),
    CONVERSATIONS(R.string.tab_conversations),
    COURSES(R.string.tab_courses),
    CAPTURES(R.string.tab_captures),
    SETTINGS(R.string.tab_settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCollectorApp(
    notificationAccessGranted: Boolean,
    openNotificationSettings: () -> Unit,
    mainViewModel: MainViewModel = viewModel()
) {
    val onboardingComplete by mainViewModel.onboardingComplete.collectAsState()
    val context = LocalContext.current
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationPermissionGranted = it }
    MaterialTheme {
        if (!onboardingComplete) {
            OnboardingScreen(
                notificationAccessGranted,
                openNotificationSettings,
                mainViewModel::completeOnboarding
            )
            return@MaterialTheme
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.app_name)) })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                AccessBanner(notificationAccessGranted, openNotificationSettings)
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
                when (AppTab.entries[selectedTab]) {
                    AppTab.EVENTS -> EventsScreen(mainViewModel)
                    AppTab.SIGNALS -> SignalsScreen(mainViewModel)
                    AppTab.INBOX -> InboxScreen(mainViewModel)
                    AppTab.CONVERSATIONS -> ConversationsScreen(mainViewModel)
                    AppTab.COURSES -> CoursesScreen(mainViewModel)
                    AppTab.CAPTURES -> CapturesScreen(mainViewModel)
                    AppTab.SETTINGS -> SettingsScreen(
                        mainViewModel,
                        openNotificationSettings,
                        notificationPermissionGranted,
                        requestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventsScreen(viewModel: MainViewModel) {
    val events by viewModel.events.collectAsState(initial = emptyList())
    val now = System.currentTimeMillis()
    val upcoming = events.count {
        val relevantTime = it.dueAt ?: it.startsAt
        it.status in setOf("CONFIRMED", "CORRECTION") &&
            relevantTime != null &&
            relevantTime in now..(now + 7L * 24L * 60L * 60L * 1000L)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenListHeader(R.string.events_title, R.string.events_body)
        if (events.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.upcoming_count, upcoming, upcoming),
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.Bold
            )
        }
        if (events.isEmpty()) EmptyState(R.string.no_events)
        else LazyColumn(Modifier.fillMaxSize()) {
            items(events, key = EventWithConversation::eventKey) { event ->
                EventCard(event, viewModel::archiveEvent)
            }
        }
    }
}

@Composable
private fun EventCard(event: EventWithConversation, archive: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(event.courseCode, event.title).joinToString(" · "),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
                Text(event.status, style = MaterialTheme.typography.labelMedium)
            }
            Text(event.summary)
            event.dueAt?.let {
                Text(stringResource(R.string.due_time, formatDate(it)), fontWeight = FontWeight.Bold)
            }
            event.startsAt?.let {
                Text(stringResource(R.string.starts_time, formatDate(it)))
            }
            event.location?.let { Text(stringResource(R.string.location_value, it)) }
            event.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "${event.category} · ${event.urgency} · ${event.confidence.times(100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                stringResource(R.string.source_chat, event.conversationName),
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(onClick = { archive(event.eventKey) }) {
                Text(stringResource(R.string.archive))
            }
        }
    }
}

@Composable
private fun SignalsScreen(viewModel: MainViewModel) {
    val revisions by viewModel.revisions.collectAsState(initial = emptyList())
    ScreenListHeader(R.string.activity_title, R.string.activity_body)
    if (revisions.isEmpty()) EmptyState(R.string.no_activity)
    else LazyColumn(Modifier.fillMaxSize()) {
        items(revisions, key = RevisionListItem::id) { revision ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(revision.courseCode, revision.title).joinToString(" · "),
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(revision.action, style = MaterialTheme.typography.labelMedium)
                    }
                    revision.changeSummary?.let { Text(it) }
                    Text(formatDate(revision.createdAt), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    accessGranted: Boolean,
    openSettings: () -> Unit,
    complete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_privacy), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        AccessStatus(accessGranted)
        Spacer(Modifier.height(12.dp))
        if (!accessGranted) {
            OutlinedButton(onClick = openSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_access_settings))
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = complete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_label))
        }
    }
}

@Composable
private fun AccessBanner(accessGranted: Boolean, openSettings: () -> Unit) {
    if (accessGranted) return
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            AccessStatus(false)
            TextButton(onClick = openSettings) { Text(stringResource(R.string.open_access_settings)) }
        }
    }
}

@Composable
private fun AccessStatus(accessGranted: Boolean) {
    Text(
        stringResource(
            if (accessGranted) R.string.notification_access_ready
            else R.string.notification_access_required
        ),
        color = if (accessGranted) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error
    )
}

@Composable
private fun InboxScreen(viewModel: MainViewModel) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    ScreenListHeader(R.string.inbox_title, R.string.inbox_body)
    if (messages.isEmpty()) EmptyState(R.string.no_messages)
    else LazyColumn(Modifier.fillMaxSize()) {
        items(messages, key = MessageListItem::id) { MessageCard(it) }
    }
}

@Composable
private fun MessageCard(message: MessageListItem) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stringResource(R.string.platform_conversation, message.platform, message.conversationName),
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(
                    R.string.sender_message,
                    message.sender.ifBlank { stringResource(R.string.unknown_sender) },
                    message.text
                )
            )
            Text(formatDate(message.messageTimestamp), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (message.analysisEnabled) R.string.analysis_on else R.string.analysis_off
                    ),
                    style = MaterialTheme.typography.labelSmall
                )
                message.courseCode?.let { Text(" · $it", style = MaterialTheme.typography.labelSmall) }
            }
            Text(
                stringResource(
                    R.string.local_classification,
                    message.localCategory,
                    message.relevanceScore.times(100).toInt(),
                    message.analysisState
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ConversationsScreen(viewModel: MainViewModel) {
    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    val courses by viewModel.courses.collectAsState(initial = emptyList())
    ScreenListHeader(R.string.conversations_title, R.string.conversations_body)
    if (conversations.isEmpty()) EmptyState(R.string.no_conversations)
    else LazyColumn(Modifier.fillMaxSize()) {
        items(conversations, key = ConversationWithCourse::id) { conversation ->
            ConversationCard(conversation, courses, viewModel)
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: ConversationWithCourse,
    courses: List<CourseEntity>,
    viewModel: MainViewModel
) {
    var showCourseDialog by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(conversation.displayName, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.platform_conversation,
                            conversation.platform,
                            pluralStringResource(
                                R.plurals.message_count,
                                conversation.messageCount,
                                conversation.messageCount
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = conversation.enabled,
                    onCheckedChange = { viewModel.setConversationEnabled(conversation.id, it) }
                )
            }
            TextButton(onClick = { showCourseDialog = true }) {
                Text(
                    stringResource(
                        R.string.course_assignment,
                        conversation.courseCode ?: stringResource(R.string.no_course)
                    )
                )
            }
        }
    }
    if (showCourseDialog) {
        CourseAssignmentDialog(
            courses = courses,
            selectedId = conversation.defaultCourseId,
            dismiss = { showCourseDialog = false },
            select = {
                viewModel.setConversationCourse(conversation.id, it)
                showCourseDialog = false
            }
        )
    }
}

@Composable
private fun CourseAssignmentDialog(
    courses: List<CourseEntity>,
    selectedId: Long?,
    dismiss: () -> Unit,
    select: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.assign_course)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow(stringResource(R.string.no_course), selectedId == null) { select(null) }
                courses.forEach { course ->
                    ChoiceRow("${course.code} — ${course.title}", selectedId == course.id) {
                        select(course.id)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, choose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = choose)
        Text(label)
    }
}

@Composable
private fun CoursesScreen(viewModel: MainViewModel) {
    val courses by viewModel.courses.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenListHeader(R.string.courses_title, R.string.courses_body)
        Button(onClick = { showAdd = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.add_course))
        }
        if (courses.isEmpty()) EmptyState(R.string.no_courses)
        else LazyColumn {
            items(courses, key = CourseEntity::id) { course ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(course.code, fontWeight = FontWeight.Bold)
                            Text(course.title)
                            if (course.section.isNotBlank()) Text(course.section, style = MaterialTheme.typography.bodySmall)
                            if (course.aliases.isNotBlank()) Text(course.aliases, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { viewModel.deleteCourse(course.id) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddCourseDialog({ showAdd = false }, viewModel::addCourse)
}

@Composable
private fun AddCourseDialog(dismiss: () -> Unit, save: (String, String, String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.add_course)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(code, { code = it }, label = { Text(stringResource(R.string.course_code)) })
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.course_title)) })
                OutlinedTextField(section, { section = it }, label = { Text(stringResource(R.string.course_section)) })
                OutlinedTextField(aliases, { aliases = it }, label = { Text(stringResource(R.string.course_aliases)) })
            }
        },
        confirmButton = {
            Button(
                enabled = code.isNotBlank() && title.isNotBlank(),
                onClick = { save(code, title, section, aliases); dismiss() }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun CapturesScreen(viewModel: MainViewModel) {
    val captures by viewModel.snapshots.collectAsState(initial = emptyList())
    ScreenListHeader(R.string.raw_title, R.string.raw_body)
    if (captures.isEmpty()) EmptyState(R.string.no_captures)
    else LazyColumn(Modifier.fillMaxSize()) {
        items(captures, key = NotificationSnapshotEntity::id) { capture -> SnapshotCard(capture) }
    }
}

@Composable
private fun SnapshotCard(capture: NotificationSnapshotEntity) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(capture.title ?: capture.packageName, fontWeight = FontWeight.Bold)
            capture.text?.let { Text(it) }
            capture.bigText?.takeIf { it != capture.text }?.let { Text(it) }
            capture.textLines?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (capture.isGroupSummary) Text(stringResource(R.string.group_summary))
            Text(
                stringResource(R.string.captured_time, formatDate(capture.postedAt)),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    viewModel: MainViewModel,
    openNotificationSettings: () -> Unit,
    notificationPermissionGranted: Boolean,
    requestNotificationPermission: () -> Unit
) {
    val paused by viewModel.paused.collectAsState()
    val messenger by viewModel.messengerEnabled.collectAsState()
    val whatsapp by viewModel.whatsappEnabled.collectAsState()
    val retention by viewModel.retentionDays.collectAsState()
    val cloudEnabled by viewModel.cloudEnabled.collectAsState()
    val savedBackendUrl by viewModel.backendUrl.collectAsState()
    val tokenConfigured by viewModel.tokenConfigured.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()
    val eventAlerts by viewModel.eventAlertsEnabled.collectAsState()
    val dailyDigest by viewModel.dailyDigestEnabled.collectAsState()
    var backendUrl by remember(savedBackendUrl) { mutableStateOf(savedBackendUrl) }
    var deviceToken by remember { mutableStateOf("") }
    var showRetention by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        SettingSwitch(R.string.capture_messenger, messenger, viewModel::setMessengerEnabled)
        SettingSwitch(R.string.capture_whatsapp, whatsapp, viewModel::setWhatsappEnabled)
        SettingSwitch(
            R.string.collection_enabled,
            !paused,
            change = { viewModel.setPaused(!it) }
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.retention_title), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { showRetention = true }) {
            Text(pluralStringResource(R.plurals.retention_days, retention, retention))
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.cloud_analysis), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.cloud_analysis_body))
        OutlinedTextField(
            value = backendUrl,
            onValueChange = { backendUrl = it },
            label = { Text(stringResource(R.string.backend_url)) },
            placeholder = { Text("https://example.com/") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = deviceToken,
            onValueChange = { deviceToken = it },
            label = { Text(stringResource(R.string.device_token)) },
            placeholder = {
                Text(stringResource(if (tokenConfigured) R.string.token_saved else R.string.token_required))
            },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { viewModel.saveCloudConfiguration(backendUrl, deviceToken); deviceToken = "" },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.save_cloud_configuration)) }
        SettingSwitch(R.string.enable_cloud_analysis, cloudEnabled, viewModel::setCloudEnabled)
        Button(
            onClick = viewModel::analyzeNow,
            enabled = cloudEnabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.analyze_now)) }
        if (cloudStatus.isNotBlank()) Text(cloudStatus, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.alerts_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.alerts_body))
        if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
            OutlinedButton(
                onClick = requestNotificationPermission,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.allow_alerts)) }
        }
        SettingSwitch(
            R.string.immediate_alerts,
            eventAlerts,
            viewModel::setEventAlertsEnabled,
            enabled = notificationPermissionGranted
        )
        SettingSwitch(
            R.string.daily_digest,
            dailyDigest,
            viewModel::setDailyDigestEnabled,
            enabled = notificationPermissionGranted
        )
        Text(stringResource(R.string.digest_time), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = openNotificationSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_access_settings))
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.delete_all))
        }
        Text(stringResource(R.string.delete_data_body), style = MaterialTheme.typography.bodySmall)
    }

    if (showRetention) {
        AlertDialog(
            onDismissRequest = { showRetention = false },
            title = { Text(stringResource(R.string.retention_title)) },
            text = {
                Column {
                    CaptureSettings.RETENTION_OPTIONS.forEach { days ->
                        ChoiceRow(pluralStringResource(R.plurals.retention_days, days, days), retention == days) {
                            viewModel.setRetentionDays(days)
                            showRetention = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetention = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                Button(onClick = { viewModel.deleteCapturedData(); confirmDelete = false }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SettingSwitch(
    label: Int,
    checked: Boolean,
    change: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

@Composable
private fun ScreenListHeader(title: Int, body: Int) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState(text: Int) {
    Text(stringResource(text), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
