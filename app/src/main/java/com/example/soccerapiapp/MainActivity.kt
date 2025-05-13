package com.example.soccerapiapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// This is the main activity. It loads the UI when the app starts.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This sets the UI layout
        setContent {
            // Keeps track of which tab is selected
            var selectedTab by remember { mutableStateOf(0) }

            // Apply a dark theme to the app
            MaterialTheme(colorScheme = darkColorScheme()) {
                // Show bottom nav and the selected screen
                Scaffold(
                    bottomBar = { BottomNavBar(selectedTab) { selectedTab = it } }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> HomeScreen() // Show home
                        1 -> MatchListScreen(Modifier.padding(innerPadding)) // Show games
                    }
                }
            }
        }
    }
}

// Bottom navigation bar with Home and Games
@Composable
fun BottomNavBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        // Home tab
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        // Games tab
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Games") },
            label = { Text("Games") }
        )
    }
}

// This screen shows the first live match if one is available
@Composable
fun HomeScreen() {
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }

    // Load all matches and filter for ones that have started but not finished
    LaunchedEffect(Unit) {
        val allMatches = fetchWeekMatches()
        matches = allMatches.filter {
            it.status != "SCHEDULED" && it.status != "FINISHED"
        }
    }

    // Show live match or a message
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (matches.isNotEmpty()) {
            Text("Live Match", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            MatchCard(matches.first()) // show the first match
        } else {
            Text("No live matches right now.", color = Color.White)
        }
    }
}

// This screen shows matches for the selected day
@Composable
fun MatchListScreen(modifier: Modifier = Modifier) {
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val dates = generateDateTabs() // creates Yesterday, Today, etc.
    var selectedIndex by remember { mutableStateOf(1) } // default to Today

    // Load matches from API
    LaunchedEffect(Unit) {
        try {
            matches = fetchWeekMatches()
        } catch (e: Exception) {
            error = e.message
        }
    }

    // Show date tabs and match list
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Date tabs (Yesterday, Today, Tomorrow, etc.)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(dates) { index, date ->
                val isSelected = index == selectedIndex
                Button(
                    onClick = { selectedIndex = index },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color.DarkGray else Color.Gray,
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(date.label, fontSize = 14.sp)
                }
            }
        }

        // Show error if something went wrong
        if (error != null) {
            Text("Error: $error", color = Color.Red, modifier = Modifier.padding(16.dp))
        } else {
            // Filter matches for selected date
            val selectedDate = dates[selectedIndex].dateString
            val filtered = matches.filter { it.date.startsWith(selectedDate) }
            val grouped = filtered.groupBy { it.competition }

            // Show matches grouped by competition
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                grouped.forEach { (competition, matchList) ->
                    item { LeagueHeader(competition) }
                    items(matchList) { match ->
                        MatchCard(match)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// Show the league name and "See Table" text
@Composable
fun LeagueHeader(competition: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(competition, color = Color.White, fontWeight = FontWeight.Bold)
        Text("See Table >", color = Color.Gray, fontSize = 12.sp)
    }
}

// Show a card for a match: teams, score or time, and LIVE or minute
@Composable
fun MatchCard(match: Match) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show home and away team logos and names
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = match.homeBadge, contentDescription = "Home Team", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(match.homeTeam, color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = match.awayBadge, contentDescription = "Away Team", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(match.awayTeam, color = Color.White, fontSize = 14.sp)
                    }
                }

                // Show score, time, or minute label
                Column(horizontalAlignment = Alignment.End) {
                    val displayText = when (match.status) {
                        "FINISHED", "IN_PLAY", "PAUSED", "LIVE" -> "${match.homeScore} - ${match.awayScore}"
                        else -> getFormattedTime(match.date)
                    }

                    Text(displayText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    // If match is in progress, show LIVE label
                    if (match.status == "IN_PLAY") {
                        Text("LIVE", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Holds match info
data class Match(
    val homeTeam: String,
    val awayTeam: String,
    val date: String,
    val status: String,
    val homeScore: Int,
    val awayScore: Int,
    val homeBadge: String,
    val awayBadge: String,
    val competition: String
)

// Load match data from the football-data.org API
suspend fun fetchWeekMatches(): List<Match> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val token = "8a96e1554da64e8882441b0bcef8e365" // API key

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -1)
    val dateFrom = sdf.format(cal.time)
    cal.add(Calendar.DAY_OF_MONTH, 7)
    val dateTo = sdf.format(cal.time)

    val url = "https://api.football-data.org/v4/matches?dateFrom=$dateFrom&dateTo=$dateTo"
    val request = Request.Builder()
        .url(url)
        .addHeader("X-Auth-Token", token)
        .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
    val body = response.body?.string() ?: throw Exception("Empty response")

    val json = JSONObject(body)
    val matchesArray = json.getJSONArray("matches")

    val result = mutableListOf<Match>()
    for (i in 0 until matchesArray.length()) {
        val match = matchesArray.getJSONObject(i)
        val home = match.getJSONObject("homeTeam")
        val away = match.getJSONObject("awayTeam")
        val score = match.getJSONObject("score").getJSONObject("fullTime")
        val competition = match.getJSONObject("competition")

        result.add(
            Match(
                homeTeam = home.getString("name"),
                awayTeam = away.getString("name"),
                date = match.getString("utcDate").replace("T", " ").replace("Z", ""),
                status = match.getString("status"),
                homeScore = score.optInt("home", 0),
                awayScore = score.optInt("away", 0),
                homeBadge = home.optString("crest", ""),
                awayBadge = away.optString("crest", ""),
                competition = competition.getString("name")
            )
        )
    }
    result
}

// Tab for Yesterday, Today, Tomorrow, etc.
data class DateTab(val label: String, val dateString: String)

// Create tab list for dates
fun generateDateTabs(): List<DateTab> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()

    return listOf(-1, 0, 1, 2, 3, 4).map {
        val tempCal = cal.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_YEAR, it)
        val label = when (it) {
            -1 -> "Yesterday"
            0 -> "Today"
            1 -> "Tomorrow"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(tempCal.time)
        }
        DateTab(label, sdf.format(tempCal.time))
    }
}

// Converts UTC time to readable format like 02:00 PM
fun getFormattedTime(utcDate: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(utcDate)
        val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        outputFormat.format(date!!)
    } catch (e: Exception) {
        ""
    }
}
