package moe.shizuku.manager.settings

import android.app.Dialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.BugReportDialogBinding
import moe.shizuku.manager.utils.shareAppLog
import moe.shizuku.manager.worker.AdbStartWorker
import rikka.core.util.ClipboardUtils

class BugReportDialog : DialogFragment() {

    private lateinit var binding: BugReportDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        binding = BugReportDialogBinding.inflate(layoutInflater)

        binding.apply {
            reportEmail.text = REPORT_EMAIL
            shareLog.setOnClickListener { shareLog() }
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_report_bug)
            .setView(binding.root)
            .setPositiveButton(R.string.bug_report_email_open) { _, _ ->
                openEmail(context)
            }
            .setNeutralButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .create()
    }

    private fun openEmail(context: Context) {
        val subject = context.getString(R.string.bug_report_email_subject, context.getString(R.string.app_name))
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$REPORT_EMAIL?subject=${Uri.encode(subject)}"))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val copied = ClipboardUtils.put(context, REPORT_EMAIL)
            val message = if (copied) R.string.bug_report_email_copied else R.string.bug_report_email_unavailable
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * The whole log, handed to whatever the reporter picks so they can attach it.
     *
     * [shareAppLog] does the writing and the sharing, and the same call is behind the Log screen's
     * own Share - one implementation, so what a report attaches and what the screen sends cannot
     * be two different things. What this adds is only what to say when it cannot: a button that
     * appears to do nothing is worse than one that reports it could not.
     */
    private fun shareLog() {
        lifecycleScope.launch {
            val context = context ?: return@launch
            if (!shareAppLog(context)) {
                Toast.makeText(context, R.string.log_share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(AdbStartWorker.NOTIFICATION_ID)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (activity is BugReportDialogActivity) activity?.finish()
    }

    private companion object {
        const val REPORT_EMAIL = "seandyadryan2@gmail.com"
    }
}
