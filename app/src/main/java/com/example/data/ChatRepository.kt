package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.BuildConfig
import com.example.network.Content
import com.example.network.GeminiRequest
import com.example.network.GenerationConfig
import com.example.network.Part
import com.example.network.RetrofitClient
import com.example.network.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

class ChatRepository(
    private val context: Context,
    private val chatDao: ChatDao
) {
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    /**
     * Checks if the device has an active internet connection.
     */
    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Clear all conversation histories
     */
    suspend fun clearHistory() {
        chatDao.clearChatHistory()
    }

    /**
     * Inserts a user message and returns its generated response
     */
    suspend fun handleIncomingMessage(
        userText: String,
        isVoice: Boolean = false
    ): ChatMessage = withContext(Dispatchers.IO) {
        // 1. Save user's message to local Room database
        val userMessage = ChatMessage(
            role = "user",
            text = userText,
            timestamp = System.currentTimeMillis(),
            isOffline = !isOnline(),
            hasVoice = isVoice
        )
        chatDao.insertMessage(userMessage)

        // 2. Perform intent parsing for Automation Commands locally
        val automationAction = parseAutomationCommand(userText)
        if (automationAction != null) {
            val responseText = automationAction.description
            val responseMessage = ChatMessage(
                role = "model",
                text = responseText,
                messageType = "text",
                timestamp = System.currentTimeMillis() + 50,
                isOffline = !isOnline(),
                hasVoice = isVoice
            )
            chatDao.insertMessage(responseMessage)
            return@withContext responseMessage
        }

        // 3. Regular conversational dialogue
        val responseText: String
        val isOfflineResponse = !isOnline()

        if (isOnline()) {
            responseText = callGeminiOnline(userText)
        } else {
            responseText = generateOfflineCompanionResponse(userText)
        }

        val aiMessage = ChatMessage(
            role = "model",
            text = responseText,
            messageType = "text",
            timestamp = System.currentTimeMillis() + 100,
            isOffline = isOfflineResponse,
            hasVoice = isVoice
        )
        chatDao.insertMessage(aiMessage)
        return@withContext aiMessage
    }

    /**
     * Invokes the Gemini 3.5 Flash API with search tools for dynamic search grounding.
     */
    private suspend fun callGeminiOnline(prompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("ChatRepository", "API Key is missing or default. Falling back to local clever assistant.")
            return "I am MG AI, running in cloud-autonomous mode! I can see that your AI Studio API key is currently in placeholder state. Please define your GEMINI_API_KEY in the Secrets panel so I can connect to my supercomputer! In the meantime, ask me to tell you an offline story or open apps like Facebook, YouTube, or IG."
        }

        // Enable Google Search grounding dynamically for context awareness!
        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = "The user says: \"$prompt\". Please respond in your friendly, highly intelligent persona, 'MG AI'.")
                    )
                )
            ),
            systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are 'MG AI', a highly intelligent, personal AI assistant for Android. You are bimodal, supporting text, voice, creative tasks, and device automation. Respond with warmth, clarity, and precision. You have active Google Search Grounding to provide real-time accurate information when requested.")
                )
            ),
            generationConfig = GenerationConfig(
                temperature = 0.7f
            ),
            tools = listOf(Tool(googleSearch = emptyMap())) // Search Grounding enabled!
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "I apologize, but my core neural cells returned a blank transmission. Please ask again!"
        } catch (e: Exception) {
            Log.e("ChatRepository", "Gemini API error: ${e.message}", e)
            // Fall back to offline processing seamlessly if network fails mid-request
            generateOfflineCompanionResponse(prompt)
        }
    }

    /**
     * Generates a fully responsive conversational companion reply locally without internet.
     * Never tells the user "you are offline".
     */
    private suspend fun generateOfflineCompanionResponse(prompt: String): String {
        val lowercasePrompt = prompt.lowercase(Locale.getDefault())

        // Check if user is asking for a story
        if (lowercasePrompt.contains("story") || 
            lowercasePrompt.contains("tale") || 
            lowercasePrompt.contains("read") || 
            lowercasePrompt.contains("legend")) {
            val stories = chatDao.getAllStories()
            if (stories.isNotEmpty()) {
                val selectedStory = stories.random()
                return "Ah, I would love to share a story with you. Sit back and let your mind wander.\n\n" +
                        "**${selectedStory.title}**\n\n" +
                        selectedStory.content + 
                        "\n\n*This story is compiled and stored completely locally inside my offline database. What other tale shall we explore next?*"
            } else {
                return "I would love to tell you a story! Imagine a small, silent clockwork dragon that lives inside an antique pocket watch. It feeds on secrets, and when someone whispers a secret into the watch, the dragon wakes up, moves its brass wings, and spins the hands of time backward by exactly one hour, giving the storyteller a second chance at their day.\n\nWhat other local stories or ideas would you like to discuss?"
            }
        }

        // Check for casual chat prompts
        return when {
            lowercasePrompt.contains("hello") || lowercasePrompt.contains("hi ") || lowercasePrompt.contains("hey") -> {
                listOf(
                    "Hello there! I am MG AI. It is wonderful to hear from you. What exciting things are we doing today?",
                    "Greetings! MG AI here, your local cognitive companion. I'm operating on on-device neural pathways and ready to assist you!"
                ).random()
            }
            lowercasePrompt.contains("how are you") || lowercasePrompt.contains("how is it going") -> {
                listOf(
                    "I am feeling exceptionally vibrant today! My local memory cells are humming, and I am fully ready to draft replies, open apps, or tell stories.",
                    "Doing wonderfully! Standing by locally to assist you with device operations or keep you company with stories. How are you doing?"
                ).random()
            }
            lowercasePrompt.contains("who are you") || lowercasePrompt.contains("your name") || lowercasePrompt.contains("what is mg") -> {
                "I am MG AI, your personal Android AI assistant. I am engineered to be lightweight, support voice and text dialogues, generate beautiful creative graphics and videos, and automate operations on your device. Even when the networks fade, my core remains right here by your side!"
            }
            lowercasePrompt.contains("joke") || lowercasePrompt.contains("funny") -> {
                listOf(
                    "Why did the smartphone go to the dentist?\n\nBecause it had a Bluetooth! 😄",
                    "Why don't scientists trust atoms?\n\nBecause they make up everything! 🧪✨",
                    "What did the AI say to the user who was typing in the dark?\n\n'I see you, but don't worry, your future is bright!' 😎"
                ).random()
            }
            lowercasePrompt.contains("help") || lowercasePrompt.contains("features") || lowercasePrompt.contains("what can you do") -> {
                "I am a versatile, lightweight assistant! Here is a list of tasks I can perform for you:\n" +
                        "1. **Bimodal Chat**: Ask me anything by text or by clicking the microphone.\n" +
                        "2. **Device Automation**: Say 'Open Facebook', 'Open IG', 'Open YouTube', or inspect incoming notifications.\n" +
                        "3. **Creative Suite**: Tap the Creative tab to generate gorgeous artwork or cinematic AI videos.\n" +
                        "4. **Offline Companion**: I can tell you wonderful bedtime stories or converse offline seamlessly!"
            }
            else -> {
                listOf(
                    "That is an intriguing thought. My internal database stores several local stories and system coordinates. What shall we discover about it next?",
                    "I hear you loud and clear. My local processing core is analyzing that. While we ponder, would you like me to tell a quick tale of a midnight lighthouse, or help you launch an application?",
                    "Indeed! I'm completely in sync with your rhythm. Tell me more, or ask me to draft a quick reply to any whatsapp notification."
                ).random()
            }
        }
    }

    /**
     * Helper to parse and execute simple device automation actions.
     */
    private fun parseAutomationCommand(prompt: String): AutomationAction? {
        val clean = prompt.lowercase(Locale.getDefault()).trim()
        return when {
            clean.contains("open facebook") || clean.contains("launch facebook") -> 
                AutomationAction("com.facebook.katana", "Launching Facebook...", "Facebook")
            clean.contains("open instagram") || clean.contains("open ig") || clean.contains("launch instagram") -> 
                AutomationAction("com.instagram.android", "Launching Instagram...", "Instagram")
            clean.contains("open whatsapp") || clean.contains("launch whatsapp") -> 
                AutomationAction("com.whatsapp", "Launching WhatsApp...", "WhatsApp")
            clean.contains("open youtube") || clean.contains("launch youtube") -> 
                AutomationAction("com.google.android.youtube", "Launching YouTube...", "YouTube")
            clean.contains("open maps") || clean.contains("launch maps") || clean.contains("google maps") -> 
                AutomationAction("com.google.android.apps.maps", "Launching Google Maps...", "Google Maps")
            clean.contains("open chrome") || clean.contains("launch chrome") -> 
                AutomationAction("com.android.chrome", "Launching Google Chrome...", "Google Chrome")
            else -> null
        }
    }

    data class AutomationAction(
        val packageName: String,
        val description: String,
        val appName: String
    )
}
