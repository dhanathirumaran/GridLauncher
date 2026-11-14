package tgo1014.gridlauncher.data.shortcut

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import tgo1014.gridlauncher.ui.PinShortcutActivity

class InstallShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val forwardIntent = Intent(context, PinShortcutActivity::class.java).apply {
            action = intent.action
            putExtras(intent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(forwardIntent)
    }
}
