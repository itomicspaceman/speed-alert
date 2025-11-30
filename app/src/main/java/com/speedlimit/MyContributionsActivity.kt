package com.speedlimit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.speedlimit.databinding.ActivityMyContributionsBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Shows the user's contribution history.
 * Both successful and failed attempts are logged locally.
 */
class MyContributionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyContributionsBinding
    private lateinit var contributionLog: ContributionLog
    private lateinit var osmContributor: OsmContributor
    private lateinit var adapter: ContributionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyContributionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contributionLog = ContributionLog(this)
        osmContributor = OsmContributor(this)

        setupUI()
        loadContributions()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        // View on OSM link
        if (osmContributor.isLoggedIn()) {
            binding.viewOnOsmLink.visibility = View.VISIBLE
            binding.viewOnOsmLink.setOnClickListener {
                val username = osmContributor.getUsername()
                if (username != null) {
                    val url = "https://www.openstreetmap.org/user/$username/history"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }

        // Setup RecyclerView
        adapter = ContributionAdapter()
        binding.contributionsList.layoutManager = LinearLayoutManager(this)
        binding.contributionsList.adapter = adapter
    }

    private fun loadContributions() {
        val attempts = contributionLog.getAttempts()
        
        // Update summary
        val successCount = contributionLog.getSuccessCount()
        val failedCount = contributionLog.getFailedCount()
        binding.summaryText.text = getString(R.string.contributions_total, successCount, failedCount)

        // Show empty state or list
        if (attempts.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.contributionsList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.contributionsList.visibility = View.VISIBLE
            adapter.setItems(attempts)
        }
    }

    /**
     * RecyclerView adapter for contribution items.
     */
    inner class ContributionAdapter : RecyclerView.Adapter<ContributionAdapter.ViewHolder>() {
        
        private var items: List<ContributionLog.Attempt> = emptyList()
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        fun setItems(newItems: List<ContributionLog.Attempt>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contribution, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val statusIcon: TextView = itemView.findViewById(R.id.statusIcon)
            private val speedLimitText: TextView = itemView.findViewById(R.id.speedLimitText)
            private val statusText: TextView = itemView.findViewById(R.id.statusText)
            private val locationText: TextView = itemView.findViewById(R.id.locationText)
            private val failureReasonText: TextView = itemView.findViewById(R.id.failureReasonText)
            private val timeText: TextView = itemView.findViewById(R.id.timeText)

            fun bind(attempt: ContributionLog.Attempt) {
                // Speed limit
                speedLimitText.text = "${attempt.speedLimit} ${attempt.unit}"

                // Status
                when (attempt.status) {
                    ContributionLog.Status.SUCCESS -> {
                        statusIcon.text = "✅"
                        statusText.text = "• ${getString(R.string.contribution_success)}"
                        statusText.setTextColor(0xFF4CAF50.toInt())
                        failureReasonText.visibility = View.GONE
                    }
                    ContributionLog.Status.SKIPPED_SAME_LIMIT -> {
                        statusIcon.text = "⏭️"
                        statusText.text = "• Skipped"
                        statusText.setTextColor(0xFF888888.toInt())
                        failureReasonText.visibility = View.GONE
                    }
                    else -> {
                        statusIcon.text = "❌"
                        statusText.text = "• ${getString(R.string.contribution_failed)}"
                        statusText.setTextColor(0xFFFF6B6B.toInt())
                        
                        // Show failure reason
                        if (attempt.failureReason != null) {
                            failureReasonText.visibility = View.VISIBLE
                            failureReasonText.text = attempt.failureReason
                        } else {
                            failureReasonText.visibility = View.GONE
                        }
                    }
                }

                // Location/Way ID
                if (attempt.wayName != null) {
                    locationText.visibility = View.VISIBLE
                    locationText.text = attempt.wayName
                } else if (attempt.wayId > 0) {
                    locationText.visibility = View.VISIBLE
                    locationText.text = "Way ${attempt.wayId}"
                } else {
                    locationText.visibility = View.GONE
                }

                // Time - always show date and time
                val date = Date(attempt.timestamp)
                val today = Calendar.getInstance()
                val attemptCal = Calendar.getInstance().apply { time = date }
                
                val isToday = today.get(Calendar.DAY_OF_YEAR) == attemptCal.get(Calendar.DAY_OF_YEAR) &&
                    today.get(Calendar.YEAR) == attemptCal.get(Calendar.YEAR)
                
                val isYesterday = today.get(Calendar.DAY_OF_YEAR) - 1 == attemptCal.get(Calendar.DAY_OF_YEAR) &&
                    today.get(Calendar.YEAR) == attemptCal.get(Calendar.YEAR)
                
                timeText.text = when {
                    isToday -> "Today, ${timeFormat.format(date)}"
                    isYesterday -> "Yesterday, ${timeFormat.format(date)}"
                    else -> "${dateFormat.format(date)}, ${timeFormat.format(date)}"
                }
            }
        }
    }
}

