package com.speedlimit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles OpenStreetMap OAuth2 authentication and speed limit contributions.
 * 
 * Users contribute directly to OSM under their own account - we're just the interface.
 * This means:
 * - Zero cost to us (no backend needed)
 * - Follows OSM contribution guidelines
 * - User owns their contributions
 */
class OsmContributor(private val context: Context) {

    companion object {
        private const val TAG = "OsmContributor"
        
        // OSM OAuth2 endpoints
        private const val OSM_AUTH_URL = "https://www.openstreetmap.org/oauth2/authorize"
        private const val OSM_TOKEN_URL = "https://www.openstreetmap.org/oauth2/token"
        private const val OSM_API_URL = "https://api.openstreetmap.org/api/0.6"
        
        // OAuth2 credentials - TODO: Replace with actual registered app credentials
        // Register at: https://www.openstreetmap.org/oauth2/applications
        private const val CLIENT_ID = "YOUR_OSM_CLIENT_ID"
        private const val REDIRECT_URI = "speedlimit://oauth/callback"
        
        // Scopes needed for editing
        private const val SCOPES = "read_prefs write_api"
        
        // SharedPreferences keys
        private const val PREFS_NAME = "osm_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_CONTRIBUTION_COUNT = "contribution_count"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Check if user is logged in to OSM.
     */
    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_ACCESS_TOKEN, null) != null
    }

    /**
     * Get the logged-in username, if any.
     */
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    /**
     * Get the user's contribution count.
     */
    fun getContributionCount(): Int {
        return prefs.getInt(KEY_CONTRIBUTION_COUNT, 0)
    }

    /**
     * Start the OAuth2 login flow.
     * This opens the OSM website for the user to authorize our app.
     */
    fun startLogin(): Intent {
        val authUrl = Uri.parse(OSM_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .build()
        
        Log.d(TAG, "Starting OAuth flow: $authUrl")
        return Intent(Intent.ACTION_VIEW, authUrl)
    }

    /**
     * Handle the OAuth callback with the authorization code.
     * Exchange it for an access token.
     */
    suspend fun handleOAuthCallback(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exchanging auth code for token")
            
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", CLIENT_ID)
                .build()
            
            val request = Request.Builder()
                .url(OSM_TOKEN_URL)
                .post(formBody)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Token exchange failed: ${response.code}")
                return@withContext false
            }
            
            val json = JSONObject(response.body?.string() ?: "")
            val accessToken = json.getString("access_token")
            
            // Save the token
            prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
            
            // Fetch and save username
            fetchAndSaveUsername(accessToken)
            
            Log.i(TAG, "OAuth login successful")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "OAuth callback failed", e)
            return@withContext false
        }
    }

    /**
     * Fetch the user's OSM profile to get their username.
     */
    private suspend fun fetchAndSaveUsername(accessToken: String) {
        try {
            val request = Request.Builder()
                .url("$OSM_API_URL/user/details.json")
                .header("Authorization", "Bearer $accessToken")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val username = json.getJSONObject("user").getString("display_name")
                prefs.edit().putString(KEY_USERNAME, username).apply()
                Log.d(TAG, "Username: $username")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch username", e)
        }
    }

    /**
     * Submit a speed limit contribution to OSM.
     * 
     * @param wayId The OSM way ID to update
     * @param speedLimit The speed limit value (in local units)
     * @param unit The unit (mph or km/h)
     * @return true if successful
     */
    suspend fun submitSpeedLimit(wayId: Long, speedLimit: Int, unit: String): Boolean = withContext(Dispatchers.IO) {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (accessToken == null) {
            Log.e(TAG, "Not logged in")
            return@withContext false
        }
        
        try {
            // Step 1: Create a changeset
            val changesetId = createChangeset(accessToken)
            if (changesetId == null) {
                Log.e(TAG, "Failed to create changeset")
                return@withContext false
            }
            
            // Step 2: Get the current way data
            val wayData = getWay(wayId)
            if (wayData == null) {
                Log.e(TAG, "Failed to get way data")
                closeChangeset(accessToken, changesetId)
                return@withContext false
            }
            
            // Step 3: Update the way with new speed limit
            val success = updateWaySpeedLimit(accessToken, changesetId, wayId, wayData, speedLimit, unit)
            
            // Step 4: Close the changeset
            closeChangeset(accessToken, changesetId)
            
            if (success) {
                // Increment contribution count
                val count = prefs.getInt(KEY_CONTRIBUTION_COUNT, 0) + 1
                prefs.edit().putInt(KEY_CONTRIBUTION_COUNT, count).apply()
                Log.i(TAG, "Speed limit submitted successfully! Total contributions: $count")
            }
            
            return@withContext success
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit speed limit", e)
            return@withContext false
        }
    }

    /**
     * Create a new changeset for edits.
     */
    private fun createChangeset(accessToken: String): Long? {
        val changesetXml = """
            <osm>
              <changeset>
                <tag k="created_by" v="Speed/Limit Android App"/>
                <tag k="comment" v="Added/updated speed limit from Speed/Limit app"/>
              </changeset>
            </osm>
        """.trimIndent()
        
        val request = Request.Builder()
            .url("$OSM_API_URL/changeset/create")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/xml")
            .put(changesetXml.toRequestBody("application/xml".toMediaTypeOrNull()))
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        return if (response.isSuccessful) {
            response.body?.string()?.toLongOrNull()
        } else {
            Log.e(TAG, "Create changeset failed: ${response.code}")
            null
        }
    }

    /**
     * Get the current data for a way.
     */
    private fun getWay(wayId: Long): String? {
        val request = Request.Builder()
            .url("$OSM_API_URL/way/$wayId")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        return if (response.isSuccessful) {
            response.body?.string()
        } else {
            null
        }
    }

    /**
     * Update a way's speed limit tag.
     */
    private fun updateWaySpeedLimit(
        accessToken: String,
        changesetId: Long,
        wayId: Long,
        currentWayXml: String,
        speedLimit: Int,
        unit: String
    ): Boolean {
        // Parse and modify the way XML
        // The maxspeed tag format depends on the unit
        val maxspeedValue = if (unit == "mph") "$speedLimit mph" else "$speedLimit"
        
        // This is a simplified approach - in production, we'd use proper XML parsing
        // For now, we're demonstrating the concept
        val modifiedXml = addOrUpdateTag(currentWayXml, "maxspeed", maxspeedValue, changesetId)
        
        val request = Request.Builder()
            .url("$OSM_API_URL/way/$wayId")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/xml")
            .put(modifiedXml.toRequestBody("application/xml".toMediaTypeOrNull()))
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        return response.isSuccessful
    }

    /**
     * Close a changeset.
     */
    private fun closeChangeset(accessToken: String, changesetId: Long) {
        val request = Request.Builder()
            .url("$OSM_API_URL/changeset/$changesetId/close")
            .header("Authorization", "Bearer $accessToken")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        
        httpClient.newCall(request).execute()
    }

    /**
     * Add or update a tag in the way XML.
     */
    private fun addOrUpdateTag(wayXml: String, tagKey: String, tagValue: String, changesetId: Long): String {
        // Add changeset attribute and update/add the tag
        // This is simplified - production code would use proper XML parsing
        
        var modified = wayXml
        
        // Update changeset attribute
        modified = modified.replace(Regex("""changeset="[^"]*""""), """changeset="$changesetId"""")
        
        // Check if tag exists
        val tagPattern = Regex("""<tag k="$tagKey" v="[^"]*"/>""")
        val newTag = """<tag k="$tagKey" v="$tagValue"/>"""
        
        modified = if (tagPattern.containsMatchIn(modified)) {
            modified.replace(tagPattern, newTag)
        } else {
            // Add new tag before closing </way>
            modified.replace("</way>", "$newTag\n  </way>")
        }
        
        return modified
    }

    /**
     * Log out from OSM.
     */
    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Logged out from OSM")
    }
}

