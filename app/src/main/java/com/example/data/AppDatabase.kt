package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [ChatMessage::class, OfflineStory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mg_ai_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateStories(database.chatDao())
                }
            }
        }

        suspend fun populateStories(chatDao: ChatDao) {
            val stories = listOf(
                OfflineStory(
                    title = "The Clockwork Nightingale",
                    content = "In a quiet corner of a busy metropolis, hidden inside a dusty antique shop, sat a small golden clockwork bird. Most people passed it by, thinking it a broken toy. But once a year, at midnight on the summer solstice, it would wind itself up and sing a song of pure starlight. This song was so gentle and pure that only children—or those with the hearts of children—could hear it. It sang of ancient forests, of oceans made of starlight, and of a time when the world was filled with quiet wonder. A little girl named Lily once stayed awake to hear it, and from that night on, she carried a small spark of magic in her eyes, becoming a painter of beautiful, unseen worlds.",
                    category = "fantasy"
                ),
                OfflineStory(
                    title = "The Echo Tree",
                    content = "Deep in a forgotten valley, there stands a giant, silver-leaved willow known as the Echo Tree. It possesses a strange quality: if you whisper a secret or a wish into its roots, it does not make a sound. Instead, it holds your words close, absorbing them into its sap. Months later, when the autumn wind blows through its branches, the tree speaks your words back to the valley, but in a completely different voice—the rustle of leaves, the whistle of the wind, or the chirp of a cricket. A lonely traveler once whispered, 'I hope I find my way home.' When he returned in October, lost and tired, the tree rustled a melody that guided him safely over the hills to his family.",
                    category = "hope"
                ),
                OfflineStory(
                    title = "The Cloud Cartographer",
                    content = "Julian was a man who drew maps of things that did not stay. He was a cloud cartographer. Every morning, he would climb the highest peak in the valley with a leather-bound notebook and fine charcoal. While other cartographers mapped solid mountains and winding rivers, Julian sketched the soft contours of stratus, cumulus, and cirrus clouds. People laughed and said his work was useless because clouds dissolve in minutes. But Julian smiled and replied, 'The shape of a mountain is just a very slow cloud. The clouds show us how to be beautiful and let go.' Years later, his drawings were bound into a book of sky-sketches, helping thousands of people find comfort in the fleeting beauty of their own lives.",
                    category = "philosophy"
                ),
                OfflineStory(
                    title = "The Midnight Lighthouse",
                    content = "In the middle of the Great Plains, hundreds of miles from any ocean, stands an old lighthouse made of red brick. Built by an eccentric sailor in the 19th century, it has no ships to guide. Yet, every night, its massive lantern spins, casting a warm beam across the endless waves of sweetgrass. The locals say the lighthouse guides lost dreams. When people sleep, their wildest and most fragile thoughts wander through the dark night, sometimes getting lost in the shadows. But when they see the steady, spinning beam of the Midnight Lighthouse, they find their way back, waking up with a renewed sense of purpose and a smile upon their face.",
                    category = "mystery"
                ),
                OfflineStory(
                    title = "The Cosmic Jazz Cafe",
                    content = "On Asteroid 32-B, floating peacefully near the rings of Saturn, there sits a cozy glass-domed cafe. The only musician is a quiet robot named Sax-9, who plays a silver saxophone. Sax-9 doesn't play standard sheet music. Instead, his sensors detect the gentle hum of solar winds, the crackle of distant cosmic radiation, and the gravitational waves of passing comets. He translates these cosmic vibrations into smooth, late-night jazz. Space travelers from all across the galaxy stop by, sipping warm stardust tea, listening to the music of the universe, realizing that no matter how dark or empty space seems, it is always full of beautiful, silent songs.",
                    category = "sci-fi"
                )
            )
            for (story in stories) {
                chatDao.insertStory(story)
            }
        }
    }
}
