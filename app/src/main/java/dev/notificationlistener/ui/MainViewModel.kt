package dev.notificationlistener.ui

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.notificationlistener.analysis.AnalysisScheduler
import dev.notificationlistener.analysis.AnalysisWorker
import dev.notificationlistener.alerts.DigestScheduler
import dev.notificationlistener.data.AppDatabase
import dev.notificationlistener.data.CaptureRepository
import dev.notificationlistener.data.CaptureSettings
import dev.notificationlistener.data.CourseEntity
import dev.notificationlistener.data.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val dao = database.dao()
    private val repository = CaptureRepository(database)
    private val workManager = WorkManager.getInstance(application)
    private var analysisWork: LiveData<WorkInfo?>? = null
    private var analysisObserver: Observer<WorkInfo?>? = null

    val messages = dao.observeRecentMessages()
    val conversations = dao.observeConversations()
    val courses = dao.observeCourses()
    val snapshots = dao.observeSnapshots()
    val signals = dao.observeSignals()
    val events = dao.observeEvents()
    val revisions = dao.observeRevisions()

    private val _paused = MutableStateFlow(CaptureSettings.isPaused(application))
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _messengerEnabled = MutableStateFlow(CaptureSettings.messengerEnabled(application))
    val messengerEnabled: StateFlow<Boolean> = _messengerEnabled.asStateFlow()

    private val _whatsappEnabled = MutableStateFlow(CaptureSettings.whatsappEnabled(application))
    val whatsappEnabled: StateFlow<Boolean> = _whatsappEnabled.asStateFlow()

    private val _retentionDays = MutableStateFlow(CaptureSettings.retentionDays(application))
    val retentionDays: StateFlow<Int> = _retentionDays.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(CaptureSettings.onboardingComplete(application))
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    private val _cloudEnabled = MutableStateFlow(CaptureSettings.cloudEnabled(application))
    val cloudEnabled: StateFlow<Boolean> = _cloudEnabled.asStateFlow()

    private val _backendUrl = MutableStateFlow(CaptureSettings.backendUrl(application))
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _tokenConfigured = MutableStateFlow(SecretStore.backendToken(application).isNotBlank())
    val tokenConfigured: StateFlow<Boolean> = _tokenConfigured.asStateFlow()

    private val _cloudStatus = MutableStateFlow("")
    val cloudStatus: StateFlow<String> = _cloudStatus.asStateFlow()

    private val _eventAlertsEnabled = MutableStateFlow(CaptureSettings.eventAlertsEnabled(application))
    val eventAlertsEnabled: StateFlow<Boolean> = _eventAlertsEnabled.asStateFlow()

    private val _dailyDigestEnabled = MutableStateFlow(CaptureSettings.dailyDigestEnabled(application))
    val dailyDigestEnabled: StateFlow<Boolean> = _dailyDigestEnabled.asStateFlow()

    fun completeOnboarding() {
        CaptureSettings.setOnboardingComplete(getApplication())
        _onboardingComplete.value = true
    }

    fun setPaused(value: Boolean) {
        CaptureSettings.setPaused(getApplication(), value)
        _paused.value = value
    }

    fun setMessengerEnabled(value: Boolean) {
        CaptureSettings.setMessengerEnabled(getApplication(), value)
        _messengerEnabled.value = value
    }

    fun setWhatsappEnabled(value: Boolean) {
        CaptureSettings.setWhatsappEnabled(getApplication(), value)
        _whatsappEnabled.value = value
    }

    fun setRetentionDays(value: Int) {
        CaptureSettings.setRetentionDays(getApplication(), value)
        _retentionDays.value = value
        viewModelScope.launch { repository.removeExpired(value) }
    }

    fun setConversationEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setConversationEnabled(id, enabled) }
    }

    fun setConversationCourse(id: Long, courseId: Long?) {
        viewModelScope.launch { repository.setConversationCourse(id, courseId) }
    }

    fun saveCloudConfiguration(url: String, token: String) {
        val normalizedUrl = url.trim().trimEnd('/') + "/"
        val effectiveToken = token.ifBlank { SecretStore.backendToken(getApplication()) }
        if (!normalizedUrl.startsWith("https://") || effectiveToken.length < 32) {
            _cloudStatus.value = "Use an HTTPS URL and a device token of at least 32 characters."
            return
        }
        CaptureSettings.setBackendUrl(getApplication(), normalizedUrl)
        if (token.isNotBlank()) SecretStore.saveBackendToken(getApplication(), token)
        _backendUrl.value = normalizedUrl
        _tokenConfigured.value = true
        _cloudStatus.value = "Configuration saved securely on this phone."
        if (_cloudEnabled.value) AnalysisScheduler.enqueue(getApplication())
    }

    fun setCloudEnabled(value: Boolean) {
        if (value && (!_backendUrl.value.startsWith("https://") || !_tokenConfigured.value)) {
            _cloudStatus.value = "Save a valid HTTPS backend and device token first."
            return
        }
        CaptureSettings.setCloudEnabled(getApplication(), value)
        _cloudEnabled.value = value
        AnalysisScheduler.setPeriodic(getApplication(), value)
        _cloudStatus.value = if (value) "Cloud analysis enabled." else "Cloud analysis disabled."
    }

    fun analyzeNow() {
        if (_cloudEnabled.value) {
            val workId = AnalysisScheduler.enqueueNow(getApplication())
            observeAnalysis(workId)
            _cloudStatus.value = "Analysis queued; waiting for network."
        } else {
            _cloudStatus.value = "Enable cloud analysis first."
        }
    }

    private fun observeAnalysis(workId: java.util.UUID) {
        stopObservingAnalysis()
        val liveData = workManager.getWorkInfoByIdLiveData(workId)
        lateinit var observer: Observer<WorkInfo?>
        observer = Observer { info ->
            if (info == null) return@Observer
            val reportedStatus = info.outputData.getString(AnalysisWorker.OUTPUT_STATUS)
                ?: info.progress.getString(AnalysisWorker.OUTPUT_STATUS)
            _cloudStatus.value = when (info.state) {
                WorkInfo.State.BLOCKED -> "Analysis is waiting for its requirements."
                WorkInfo.State.ENQUEUED -> reportedStatus ?: "Analysis queued; waiting for network."
                WorkInfo.State.RUNNING -> "Analyzing pending messages…"
                WorkInfo.State.SUCCEEDED -> reportedStatus ?: "Analysis complete."
                WorkInfo.State.FAILED -> reportedStatus ?: "Analysis failed. Check the backend settings."
                WorkInfo.State.CANCELLED -> "Analysis was cancelled."
            }
            if (info.state.isFinished) {
                liveData.removeObserver(observer)
                analysisWork = null
                analysisObserver = null
            }
        }
        analysisWork = liveData
        analysisObserver = observer
        liveData.observeForever(observer)
    }

    private fun stopObservingAnalysis() {
        val liveData = analysisWork
        val observer = analysisObserver
        if (liveData != null && observer != null) liveData.removeObserver(observer)
        analysisWork = null
        analysisObserver = null
    }

    override fun onCleared() {
        stopObservingAnalysis()
        super.onCleared()
    }

    fun setEventAlertsEnabled(value: Boolean) {
        CaptureSettings.setEventAlertsEnabled(getApplication(), value)
        _eventAlertsEnabled.value = value
    }

    fun setDailyDigestEnabled(value: Boolean) {
        CaptureSettings.setDailyDigestEnabled(getApplication(), value)
        _dailyDigestEnabled.value = value
        DigestScheduler.setEnabled(getApplication(), value)
    }

    fun archiveEvent(eventKey: String) {
        viewModelScope.launch { dao.setEventArchived(eventKey, true) }
    }

    fun addCourse(code: String, title: String, section: String, aliases: String) {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.isBlank() || title.isBlank()) return
        viewModelScope.launch {
            dao.insertCourse(
                CourseEntity(
                    code = normalizedCode,
                    title = title.trim(),
                    section = section.trim(),
                    aliases = aliases.trim()
                )
            )
        }
    }

    fun deleteCourse(id: Long) {
        viewModelScope.launch { dao.deleteCourse(id) }
    }

    fun deleteCapturedData() {
        viewModelScope.launch { repository.deleteCapturedData() }
    }
}
