package com.example.creative

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object CreativeEngine {

    /**
     * Hashing function to get a deterministic integer from prompt
     */
    private fun hashPrompt(prompt: String): Int {
        return prompt.hashCode()
    }

    /**
     * Dynamically generates a beautiful, high-contrast generative art Bitmap based on a text prompt.
     * Supports various styles depending on keywords detected in the prompt.
     */
    fun generateImage(prompt: String): Bitmap {
        val width = 1024
        val height = 1024
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val seed = hashPrompt(prompt)
        val style = detectStyle(prompt)

        // Draw Background
        val (color1, color2, color3) = getPalette(style, seed)
        val bgGradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(color1, color2),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = bgGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw Ambient Radial Glow
        val radialGlow = RadialGradient(
            (width / 2).toFloat() + (seed % 100 - 50),
            (height / 2).toFloat() + (seed % 150 - 75),
            (width * 0.6f),
            color3, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        paint.shader = radialGlow
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null // Reset

        // Draw Generative Geometric/Fluid Structures
        val r = java.util.Random(seed.toLong())
        val shapesCount = 12 + r.nextInt(15)

        for (i in 0 until shapesCount) {
            paint.style = Paint.Style.FILL
            paint.color = color3
            // Set alpha for glassmorphism layered effect
            paint.alpha = 30 + r.nextInt(60)

            val centerX = r.nextFloat() * width
            val centerY = r.nextFloat() * height
            val radius = 80f + r.nextFloat() * 220f

            when (r.nextInt(4)) {
                0 -> { // Layered Glass Circle
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                1 -> { // Glowing Ring
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f + r.nextFloat() * 12f
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                2 -> { // Abstract Bezier Ribbon
                    val path = Path()
                    path.moveTo(centerX - radius, centerY + r.nextInt(100) - 50)
                    path.cubicTo(
                        centerX - radius / 2, centerY - radius,
                        centerX + radius / 2, centerY + radius,
                        centerX + radius, centerY + r.nextInt(100) - 50
                    )
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 6f + r.nextFloat() * 18f
                    canvas.drawPath(path, paint)
                }
                3 -> { // Starburst Lattice
                    val spikes = 5 + r.nextInt(8)
                    val innerRadius = radius / 3
                    val outerRadius = radius
                    val path = Path()
                    var angle = 0.0
                    val angleIncrement = Math.PI * 2 / spikes
                    for (s in 0..spikes * 2) {
                        val currentRadius = if (s % 2 == 0) outerRadius else innerRadius
                        val x = centerX + cos(angle) * currentRadius
                        val y = centerY + sin(angle) * currentRadius
                        if (s == 0) {
                            path.moveTo(x.toFloat(), y.toFloat())
                        } else {
                            path.lineTo(x.toFloat(), y.toFloat())
                        }
                        angle += angleIncrement / 2
                    }
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }
        }

        // Add 60 glowing micro-particles / stars
        paint.style = Paint.Style.FILL
        paint.shader = null
        for (i in 0..70) {
            val px = r.nextFloat() * width
            val py = r.nextFloat() * height
            val pSize = 1.5f + r.nextFloat() * 5f
            paint.color = Color.WHITE
            paint.alpha = 100 + r.nextInt(155)
            canvas.drawCircle(px, py, pSize, paint)
        }

        // Draw elegant watermarked prompt label
        paint.color = Color.WHITE
        paint.alpha = 180
        paint.textSize = 28f
        paint.textAlign = Paint.Align.LEFT
        val displayPrompt = if (prompt.length > 50) prompt.substring(0, 47) + "..." else prompt
        canvas.drawText("MG AI Creative Studio", 40f, height - 80f, paint)
        
        paint.textSize = 22f
        paint.color = Color.LTGRAY
        paint.alpha = 140
        canvas.drawText("\"$displayPrompt\"", 40f, height - 45f, paint)

        return bitmap
    }

    /**
     * Saves a generated image Bitmap to the Android device's Gallery
     */
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, prompt: String): String? {
        val fileName = "MGAI_" + System.currentTimeMillis() + ".png"
        var fos: OutputStream? = null
        var imageUri: String? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MG_AI")
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    fos = resolver.openOutputStream(uri)
                    imageUri = uri.toString()
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/MG_AI"
                val dir = File(imagesDir)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, fileName)
                fos = FileOutputStream(file)
                imageUri = file.absolutePath
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(context, "Saved to Gallery: Pictures/MG_AI/", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        return imageUri
    }

    /**
     * Detects creative styles from prompt strings
     */
    private fun detectStyle(prompt: String): CreativeStyle {
        val lower = prompt.lowercase(Locale.getDefault())
        return when {
            lower.contains("neon") || lower.contains("cyberpunk") || lower.contains("laser") || lower.contains("synthwave") -> CreativeStyle.NEON_CYBER
            lower.contains("sunset") || lower.contains("dawn") || lower.contains("gold") || lower.contains("fire") || lower.contains("autumn") -> CreativeStyle.SOLAR_SUNSET
            lower.contains("forest") || lower.contains("nature") || lower.contains("emerald") || lower.contains("jungle") || lower.contains("green") -> CreativeStyle.EMERALD_FOREST
            lower.contains("ocean") || lower.contains("sea") || lower.contains("water") || lower.contains("sky") || lower.contains("blue") -> CreativeStyle.OCEAN_DREAM
            lower.contains("dark") || lower.contains("space") || lower.contains("minimal") || lower.contains("cosmic") || lower.contains("galaxy") -> CreativeStyle.COSMIC_DEEP
            else -> CreativeStyle.CLASSIC_VIBRANT
        }
    }

    /**
     * Returns a 3-color palette matching a given style and hashed seed
     */
    private fun getPalette(style: CreativeStyle, seed: Int): Triple<Int, Int, Int> {
        val r = java.util.Random(seed.toLong())
        return when (style) {
            CreativeStyle.NEON_CYBER -> Triple(
                Color.rgb(18, 10, 36),     // Dark Purple
                Color.rgb(38, 12, 64),     // Midnight Violet
                Color.rgb(255, 0, 128)     // Hot Neon Pink
            )
            CreativeStyle.SOLAR_SUNSET -> Triple(
                Color.rgb(56, 12, 4),      // Deep Crimson
                Color.rgb(120, 34, 12),    // Autumn Orange
                Color.rgb(255, 190, 45)    // Radiant Golden Gold
            )
            CreativeStyle.EMERALD_FOREST -> Triple(
                Color.rgb(4, 24, 12),      // Jungle Shadow
                Color.rgb(12, 56, 24),     // Deep Fern
                Color.rgb(72, 219, 137)    // Glowing Emerald Green
            )
            CreativeStyle.OCEAN_DREAM -> Triple(
                Color.rgb(2, 28, 48),      // Abyssal Blue
                Color.rgb(12, 52, 86),     // Marine Slate
                Color.rgb(0, 206, 201)     // Neon Aquamarine
            )
            CreativeStyle.COSMIC_DEEP -> Triple(
                Color.rgb(8, 8, 16),       // Void Black
                Color.rgb(20, 18, 42),     // Galactic Deep Purple
                Color.rgb(138, 43, 226)    // Glowing Violet Cluster
            )
            CreativeStyle.CLASSIC_VIBRANT -> Triple(
                Color.rgb(15, 32, 67),     // Sapphire Dark Blue
                Color.rgb(84, 58, 180),    // Electric Royal Blue
                Color.rgb(0, 242, 254)     // Glowing Cyan Accent
            )
        }
    }

    /**
     * Resolves high-fidelity, looping videos matching their prompt.
     * Provides free, beautiful abstract stock loops that can be saved.
     */
    fun getVideoUrl(prompt: String): String {
        val lower = prompt.lowercase(Locale.getDefault())
        return when {
            lower.contains("cyber") || lower.contains("neon") || lower.contains("tech") ->
                "https://assets.mixkit.co/videos/preview/mixkit-tunnel-of-futuristic-neon-lights-42588-large.mp4"
            lower.contains("space") || lower.contains("galaxy") || lower.contains("star") || lower.contains("cosmic") ->
                "https://assets.mixkit.co/videos/preview/mixkit-nebula-in-space-40615-large.mp4"
            lower.contains("forest") || lower.contains("nature") || lower.contains("river") || lower.contains("rain") ->
                "https://assets.mixkit.co/videos/preview/mixkit-forest-stream-in-the-sunlight-41804-large.mp4"
            lower.contains("ocean") || lower.contains("sea") || lower.contains("water") || lower.contains("wave") ->
                "https://assets.mixkit.co/videos/preview/mixkit-slow-motion-of-water-waves-33005-large.mp4"
            lower.contains("abstract") || lower.contains("art") || lower.contains("color") ->
                "https://assets.mixkit.co/videos/preview/mixkit-swirling-pink-and-purple-ink-41808-large.mp4"
            else ->
                "https://assets.mixkit.co/videos/preview/mixkit-rotating-planet-earth-in-the-starry-space-41618-large.mp4"
        }
    }

    enum class CreativeStyle {
        NEON_CYBER,
        SOLAR_SUNSET,
        EMERALD_FOREST,
        OCEAN_DREAM,
        COSMIC_DEEP,
        CLASSIC_VIBRANT
    }
}
