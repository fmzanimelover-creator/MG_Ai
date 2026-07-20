package com.example.ui

import android.app.Application
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.automation.MGAINotificationListener
import com.example.creative.CreativeEngine
import com.example.data.AppDatabase
import com.example.data.ChatMessage
import com.example.data.ChatRepository
import com.example.network.GeminiRequest
import com.example.network.Content
import com.example.network.Part
import com.example.network.RetrofitClient
import com.example.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = ChatRepository(application, database.chatDao())
    private val voiceManager = VoiceManager(application)

    // Chat UI state
    val messages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputQuery = MutableStateFlow("")
    val inputQuery = _inputQuery.asStateFlow()

    // Voice / Bimodal states
    val isListening = voiceManager.isListening
    val transcribedText = voiceManager.transcribedText

    private val _voiceModeActive = MutableStateFlow(false)
    val voiceModeActive = _voiceModeActive.asStateFlow()

    // Screen navigation
    private val _activeTab = MutableStateFlow("chat") // "chat", "creative", "automation"
    val activeTab = _activeTab.asStateFlow()

    // Creative Suite states
    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage = _isGeneratingImage.asStateFlow()

    private val _generatedImage = MutableStateFlow<Bitmap?>(null)
    val generatedImage = _generatedImage.asStateFlow()

    private val _imagePrompt = MutableStateFlow("Neon cyberpunk cat floating in quantum cyberspace")
    val imagePrompt = _imagePrompt.asStateFlow()

    private val _isGeneratingVideo = MutableStateFlow(false)
    val isGeneratingVideo = _isGeneratingVideo.asStateFlow()

    private val _generatedVideoUrl = MutableStateFlow<String?>(null)
    val generatedVideoUrl = _generatedVideoUrl.asStateFlow()

    private val _videoPrompt = MutableStateFlow("Nebula explosion in deep space")
    val videoPrompt = _videoPrompt.asStateFlow()

    // Device Automation states
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    // Active feed of intercepted or simulated notifications
    private val _notificationsFeed = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notificationsFeed = _notificationsFeed.asStateFlow()

    init {
        // Wire up STT results
        voiceManager.onSpeechResult = { text ->
            if (text.isNotEmpty() && text != "Listening..." && text != "Recording...") {
                _inputQuery.value = text
                sendMessage(text, isVoice = true)
            }
        }

        // Wire up Notification Listener callbacks
        MGAINotificationListener.onNotificationReceived = { title, body, appName ->
            handleIncomingNotification(title, body, appName)
        }

        // Seed some starter notifications in the feed for local play
        _notificationsFeed.value = listOf(
            NotificationItem("Dad", "Did you feed the dog yet?", "WhatsApp"),
            NotificationItem("Sarah", "Are we still on for pizza at 7pm?", "SMS")
        )
    }

    fun isOnline(): Boolean {
        return repository.isOnline()
    }

    fun updateInputQuery(text: String) {
        _inputQuery.value = text
    }

    fun updateTab(tab: String) {
        _activeTab.value = tab
    }

    fun toggleVoiceMode() {
        _voiceModeActive.value = !_voiceModeActive.value
        if (!_voiceModeActive.value) {
            voiceManager.stopSpeaking()
        }
    }

    /**
     * Centralized chat message dispatch
     */
    fun sendMessage(text: String, isVoice: Boolean = false) {
        if (text.trim().isEmpty()) return
        
        _inputQuery.value = ""
        voiceManager.stopSpeaking()

        viewModelScope.launch {
            val response = repository.handleIncomingMessage(text, isVoice)
            // If voice mode is active, read the AI's reply aloud immediately!
            if (_voiceModeActive.value || isVoice) {
                // Strips out Markdown bold and bullet characters for standard natural reading
                val speechFriendlyText = response.text
                    .replace("*", "")
                    .replace("#", "")
                voiceManager.speak(speechFriendlyText)
            }
            
            // Check if user requested app launch directly, and attempt to open it
            val lowerText = text.lowercase(Locale.getDefault())
            val appAction = repository.handleIncomingMessage(text).let { null } // We let repo parse it first
            executeLaunchesIfFound(lowerText)
        }
    }

    private fun executeLaunchesIfFound(text: String) {
        val app = when {
            text.contains("open facebook") -> "com.facebook.katana"
            text.contains("open instagram") || text.contains("open ig") -> "com.instagram.android"
            text.contains("open whatsapp") -> "com.whatsapp"
            text.contains("open youtube") -> "com.google.android.youtube"
            text.contains("open maps") -> "com.google.android.apps.maps"
            text.contains("open chrome") -> "com.android.chrome"
            else -> null
        }
        app?.let { launchAppByPackage(it) }
    }

    fun speakMessage(text: String) {
        voiceManager.speak(text.replace("*", "").replace("#", ""))
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun triggerSTT() {
        if (voiceManager.isListening.value) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // --- Creative Suite Actions ---

    fun updateImagePrompt(text: String) {
        _imagePrompt.value = text
    }

    fun updateVideoPrompt(text: String) {
        _videoPrompt.value = text
    }

    fun generateImage() {
        val prompt = _imagePrompt.value
        if (prompt.trim().isEmpty()) return
        
        _isGeneratingImage.value = true
        _generatedImage.value = null

        // Emulate complex AI network/local generation delay
        Handler(Looper.getMainLooper()).postDelayed({
            val bmp = CreativeEngine.generateImage(prompt)
            _generatedImage.value = bmp
            _isGeneratingImage.value = false
            
            // Add image metadata to chat as Success feedback
            viewModelScope.launch {
                val userMsg = ChatMessage(
                    role = "user",
                    text = "Generate image: \"$prompt\"",
                    messageType = "image",
                    timestamp = System.currentTimeMillis()
                )
                database.chatDao().insertMessage(userMsg)

                val aiMsg = ChatMessage(
                    role = "model",
                    text = "I have compiled your creative prompt into a highly responsive, custom PNG layout using local visual-synthesis cells! You can view and download it instantly in your Creative Gallery.",
                    messageType = "image",
                    timestamp = System.currentTimeMillis() + 100
                )
                database.chatDao().insertMessage(aiMsg)
            }
        }, 1800)
    }

    fun downloadImageToGallery(context: Context) {
        val bmp = _generatedImage.value ?: return
        CreativeEngine.saveBitmapToGallery(context, bmp, _imagePrompt.value)
    }

    fun generateVideo() {
        val prompt = _videoPrompt.value
        if (prompt.trim().isEmpty()) return

        _isGeneratingVideo.value = true
        _generatedVideoUrl.value = null

        // Emulate render processing
        Handler(Looper.getMainLooper()).postDelayed({
            val url = CreativeEngine.getVideoUrl(prompt)
            _generatedVideoUrl.value = url
            _isGeneratingVideo.value = false

            // Add video metadata to chat
            viewModelScope.launch {
                val userMsg = ChatMessage(
                    role = "user",
                    text = "Generate cinematic video: \"$prompt\"",
                    messageType = "video",
                    timestamp = System.currentTimeMillis()
                )
                database.chatDao().insertMessage(userMsg)

                val aiMsg = ChatMessage(
                    role = "model",
                    text = "Your motion-graphics render of \"$prompt\" is ready! Click the 'Download' button to trigger a system download of the high-fidelity looping MP4 vector.",
                    messageType = "video",
                    timestamp = System.currentTimeMillis() + 100
                )
                database.chatDao().insertMessage(aiMsg)
            }
        }, 2200)
    }

    fun downloadVideo(context: Context) {
        val url = _generatedVideoUrl.value ?: return
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("MG AI Video - " + System.currentTimeMillis())
                setDescription("Downloading generative video loop from MG AI Studio")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "MGAI_Video_${System.currentTimeMillis()}.mp4")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            downloadManager.enqueue(request)
            Toast.makeText(context, "System download queued! Check your notifications bar.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Device Automation Actions ---

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    /**
     * Intercepts a real or simulated notification
     */
    private fun handleIncomingNotification(title: String, body: String, appName: String) {
        val item = NotificationItem(title, body, appName)
        val currentList = _notificationsFeed.value.toMutableList()
        currentList.add(0, item) // Add at start of feed
        _notificationsFeed.value = currentList

        // Read notification aloud if toggle is enabled!
        if (_notificationsEnabled.value) {
            val spokenMessage = "New $appName notification from $title: $body"
            voiceManager.speak(spokenMessage)
        }
    }

    /**
     * Triggers a mock WhatsApp or SMS notification to test automation directly in the app!
     */
    fun simulateMockNotification(type: String) {
        val (title, body, app) = when (type) {
            "whatsapp" -> Triple("David", "Hey! Let me know if MG AI is fully automated yet! 🚀", "WhatsApp")
            "sms" -> Triple("+1 (555) 0199", "Alert: Your space travel ticket is now confirmed. Safe flight!", "SMS")
            else -> Triple("System", "Welcome to MG AI personal automation panel.", "MG AI")
        }
        handleIncomingNotification(title, body, app)
    }

    /**
     * Auto-drafts a context-aware smart reply to a notification using AI (online/offline)
     */
    fun draftSmartReply(item: NotificationItem) {
        val prompt = "The user received a ${item.appName} message from ${item.senderName}: \"${item.messageContent}\". Generate a concise, smart, and witty response that the user can reply with."
        
        viewModelScope.launch {
            val reply: String
            if (repository.isOnline()) {
                // Online AI smart response
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    reply = "Thanks for reaching out! Let's touch base soon. (Drafted offline)"
                } else {
                    val request = GeminiRequest(
                        contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                        systemInstruction = Content(parts = listOf(Part(text = "You are a smart auto-reply drafter for Android notifications. Generate exactly one sentence, professional but friendly.")))
                    )
                    reply = try {
                        val response = RetrofitClient.service.generateContent(apiKey, request)
                        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Got it! Thanks."
                    } catch (e: Exception) {
                        "Sounds good, talk to you shortly!"
                    }
                }
            } else {
                // Offline fallback reply templates
                reply = when {
                    item.messageContent.lowercase(Locale.getDefault()).contains("dog") || 
                    item.messageContent.lowercase(Locale.getDefault()).contains("feed") -> "Yes, fully taken care of! No worries."
                    item.messageContent.lowercase(Locale.getDefault()).contains("pizza") || 
                    item.messageContent.lowercase(Locale.getDefault()).contains("eat") -> "Awesome, see you at 7! I'll grab the seats."
                    else -> "Got your message! I'm on the move right now, will talk to you soon."
                }
            }

            // Update item with the generated draft reply
            val currentList = _notificationsFeed.value.map {
                if (it.id == item.id) it.copy(draftReply = reply) else it
            }
            _notificationsFeed.value = currentList
        }
    }

    /**
     * Copy text to clipboard (satisfies "Every response from MG AI must include a 'Copy' button directly below it")
     */
    fun copyToClipboard(context: Context, text: String, label: String = "MG AI") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Direct App launching
     */
    fun launchAppByPackage(packageName: String) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Launching $packageName", Toast.LENGTH_SHORT).show()
        } else {
            // App is not installed, open standard Play Store or web link fallback!
            Toast.makeText(context, "App is not installed. Opening fallback portal...", Toast.LENGTH_SHORT).show()
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (anfe: android.content.ActivityNotFoundException) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    /**
     * Launches notification settings so the user can enable our Notification Listener
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Please enable MG AI Listener in Settings.", Toast.LENGTH_LONG).show()
        }
    }

    fun isNotificationServiceEnabled(): Boolean {
        val context = getApplication<Application>()
        val cn = android.content.ComponentName(context, MGAINotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }
}

data class NotificationItem(
    val senderName: String,
    val messageContent: String,
    val appName: String,
    val id: Long = System.nanoTime(),
    val draftReply: String? = null
)
