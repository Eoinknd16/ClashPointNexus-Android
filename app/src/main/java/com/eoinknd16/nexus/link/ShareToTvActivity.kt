package com.eoinknd16.nexus.link

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.eoinknd16.nexus.NexusApp
import com.eoinknd16.nexus.core.describe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Play on TV" in Android's share sheet: share a YouTube video or any page
 * from any app and it opens on the PC in Nexus's browser. No screen of its
 * own -- just a short toast with the result.
 */
class ShareToTvActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(intent?.getStringExtra(Intent.EXTRA_TEXT), intent?.dataString).joinToString(" ")
        val url = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
        if (url == null) {
            Toast.makeText(this, "No link to send", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val c = (application as NexusApp).container
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val name = runCatching { c.pcStore.get().displayName }.getOrDefault("the PC")
            val msg = runCatching { withContext(Dispatchers.IO) { c.pc.openUrl(url) } }.fold(
                { r -> if (r.ok) "Opening on $name" else r.error ?: "PC refused" },
                { "Couldn't reach the PC (${it.describe()})" },
            )
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
