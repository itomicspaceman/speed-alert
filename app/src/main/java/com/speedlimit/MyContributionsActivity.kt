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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Shows the user's contribution history.
 * Two sections:
 * 1. OSM Contributions - fetched from OpenStreetMap API
 * 2. Submission Log - local log including failed attempts
 */
class MyContributionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyContributionsBinding
    private lateinit var contributionLog: ContributionLog
    private lateinit var osmContributor: OsmContributor
    private lateinit var localAdapter: LocalContributionAdapter
    private lateinit var osmAdapter: OsmContributionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyContributionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contributionLog = ContributionLog(this)
        osmContributor = OsmContributor(this)

        setupUI()
        loadLocalContributions()
        loadOsmContributions()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        // View on OSM link
        binding.viewOnOsmLink.setOnClickListener {
            val username = osmContributor.getUsername()
            if (username != null) {
                val url = "https://www.openstreetmap.org/user/$username/history"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        // Setup RecyclerViews
        localAdapter = LocalContributionAdapter()
        binding.contributionsList.layoutManager = LinearLayoutManager(this)
        binding.contributionsList.adapter = localAdapter

        osmAdapter = OsmContributionAdapter()
        binding.osmContributionsList.layoutManager = LinearLayoutManager(this)
        binding.osmContributionsList.adapter = osmAdapter
    }

    private fun loadLocalContributions() {
        val attempts = contributionLog.getAttempts()
        
        // Update summary
        val totalCount = attempts.size
        val failedCount = contributionLog.getFailedCount()
        binding.summaryText.text = getString(R.string.contributions_total, totalCount, failedCount)

        // Show empty state or list
        if (attempts.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.contributionsList.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.contributionsList.visibility = View.VISIBLE
            localAdapter.setItems(attempts)
        }
    }

    private fun loadOsmContributions() {
        if (!osmContributor.isLoggedIn()) {
            binding.osmCountText.visibility = View.GONE
            binding.notConnectedText.visibility = View.VISIBLE
            binding.viewOnOsmLink.visibility = View.GONE
            binding.osmContributionsList.visibility = View.GONE
            return
        }

        // Show loading state
        binding.osmCountText.text = getString(R.string.contributions_loading)
        binding.notConnectedText.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            val changesets = osmContributor.fetchUserChangesets()
            
            if (changesets.isEmpty()) {
                binding.osmCountText.text = getString(R.string.contributions_osm_count, 0)
            } else {
                binding.osmCountText.text = if (changesets.size == 1) {
                    getString(R.string.contributions_osm_count_one)
                } else {
                    getString(R.string.contributions_osm_count, changesets.size)
                }
                osmAdapter.setItems(changesets)
                binding.osmContributionsList.visibility = View.VISIBLE
            }
            
            binding.viewOnOsmLink.visibility = View.VISIBLE
        }
    }

    /**
     * RecyclerView adapter for OSM changesets.
     */
    inner class OsmContributionAdapter : RecyclerView.Adapter<OsmContributionAdapter.ViewHolder>() {
        
        private var items: List<OsmContributor.OsmChangeset> = emptyList()
        private val dateParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val displayFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        fun setItems(newItems: List<OsmContributor.OsmChangeset>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_osm_changeset, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val statusIcon: TextView = itemView.findViewById(R.id.statusIcon)
            private val changesetText: TextView = itemView.findViewById(R.id.changesetText)
            private val timeText: TextView = itemView.findViewById(R.id.timeText)

            fun bind(changeset: OsmContributor.OsmChangeset) {
                statusIcon.text = "✅"
                changesetText.text = "#${changeset.id}"
                
                // Parse and format the date
                try {
                    val date = dateParser.parse(changeset.createdAt)
                    if (date != null) {
                        timeText.text = displayFormat.format(date)
                    } else {
                        timeText.text = changeset.createdAt
                    }
                } catch (e: Exception) {
                    timeText.text = changeset.createdAt
                }

                // Click to open on OSM
                itemView.setOnClickListener {
                    val url = "https://www.openstreetmap.org/changeset/${changeset.id}"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }

    /**
     * RecyclerView adapter for local contribution attempts.
     */
    inner class LocalContributionAdapter : RecyclerView.Adapter<LocalContributionAdapter.ViewHolder>() {
        
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
