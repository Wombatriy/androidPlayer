package com.wombatriy.androidplayer.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.widget.ScrollView
import android.widget.TextView
import com.wombatriy.androidplayer.BuildConfig

/**
 * Always reachable from the top navigation info button, even while playing.
 * Licence texts are bundled in the APK so this works fully offline.
 * Showing these dialogs does not interact with the MediaSession/ExoPlayer.
 */
internal object LegalInformation {
    fun show(context: Context) {
        val version = BuildConfig.VERSION_NAME
        val sourceRevision = BuildConfig.SOURCE_REVISION
        val legalNotice = """
            BPM Player $version
            © 2026 BPM Player contributors

            Free software — GPL-3.0-or-later.
            You may copy, modify and redistribute this program under the
            terms of the GNU General Public License, version 3 or later.

            WITHOUT ANY WARRANTY, including the implied warranties of
            merchantability and fitness for a particular purpose.

            Rubber Band R3 is used under GPL-2.0-or-later.
            Third-party libraries retain their own licences.

            Source revision: $sourceRevision
            https://github.com/Wombatriy/androidPlayer
        """.trimIndent()
        AlertDialog.Builder(context)
            .setTitle("About & licences")
            .setMessage(legalNotice)
            .setPositiveButton("Licence texts") { _, _ -> showChoices(context) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showChoices(context: Context) {
        val titles = arrayOf(
            "GNU GPL v3 — BPM Player / Rubber Band",
            "Apache 2.0 — AndroidX / Kotlin",
            "Third-party copyright notices",
            "Corresponding source / warranty"
        )
        val files = arrayOf(
            "licenses/GPL-3.0-or-later.txt",
            "licenses/Apache-2.0.txt",
            "licenses/THIRD_PARTY_NOTICES.txt",
            "licenses/SOURCE_AND_WARRANTY.txt"
        )
        AlertDialog.Builder(context)
            .setTitle("Licences and source")
            .setItems(titles) { _, selected ->
                showDocument(context, titles[selected], files[selected])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDocument(context: Context, title: String, asset: String) {
        val content = runCatching {
            context.assets.open(asset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { e ->
            "Unable to read bundled notice: ${e.localizedMessage ?: "unknown error"}"
        }
        val scroll = ScrollView(context)
        val text = TextView(context).apply {
            this.text = content
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            val margin = (context.resources.displayMetrics.density * 16).toInt()
            setPadding(margin, margin, margin, margin)
        }
        scroll.addView(text)
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show()
    }
}
