package com.iborn.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReminderItem(val id: Int, val title: String, val at: Long, val done: Boolean = false)

class MainActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        ReminderReceiver.createChannel(this)
        setContent { ReminderApp() }
    }
}

private fun loadItems(context: Context): List<ReminderItem> {
    val raw = context.getSharedPreferences("reminders", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
    val arr = JSONArray(raw)
    return (0 until arr.length()).mapNotNull { i ->
        runCatching {
            val o = arr.getJSONObject(i)
            ReminderItem(o.getInt("id"), o.getString("title"), o.getLong("at"), o.optBoolean("done"))
        }.getOrNull()
    }.sortedBy { it.at }
}
private fun saveItems(context: Context, items: List<ReminderItem>) {
    val arr = JSONArray()
    items.forEach { item ->
        arr.put(JSONObject().put("id", item.id).put("title", item.title).put("at", item.at).put("done", item.done))
    }
    context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().putString("items", arr.toString()).apply()
}
private fun schedule(context: Context, item: ReminderItem) {
    val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", item.id).putExtra("title", item.title)
    val pi = PendingIntent.getBroadcast(context, item.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.set(AlarmManager.RTC_WAKEUP, item.at, pi)
    else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
    else am.setExact(AlarmManager.RTC_WAKEUP, item.at, pi)
}
private fun cancel(context: Context, id: Int) {
    val pi = PendingIntent.getBroadcast(context, id, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    if (pi != null) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderApp() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(loadItems(context)) }
    var dark by remember { mutableStateOf(context.getSharedPreferences("settings", 0).getBoolean("dark", true)) }
    var showAdd by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    val colors = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF9B8CFF), background = androidx.compose.ui.graphics.Color(0xFF0D0B16), surface = androidx.compose.ui.graphics.Color(0xFF191629))
                 else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6750A4))
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("یادآور", style = MaterialTheme.typography.titleLarge) },
                        actions = { IconButton(onClick = { dark = !dark; context.getSharedPreferences("settings", 0).edit().putBoolean("dark", dark).apply() }) {
                            Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "تغییر تم")
                        } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                },
                floatingActionButton = { FloatingActionButton(onClick = { title = ""; dateTime = ""; showAdd = true }) { Icon(Icons.Default.Add, "افزودن") } }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                    Text("کارهایت را به‌موقع انجام بده", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("هنوز یادآوری‌ای ثبت نکردی", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                            items(items, key = { it.id }) { item ->
                                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                                            Text(SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault()).format(Date(item.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = {
                                            val updated = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                                            items = updated; saveItems(context, updated); if (!item.done) cancel(context, item.id) else schedule(context, item)
                                        }) { Icon(Icons.Default.Done, "انجام شد") }
                                        IconButton(onClick = {
                                            cancel(context, item.id)
                                            items = items.filterNot { it.id == item.id }; saveItems(context, items)
                                        }) { Icon(Icons.Default.Delete, "حذف") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showAdd) {
                AlertDialog(
                    onDismissRequest = { showAdd = false },
                    title = { Text("یادآوری جدید") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان") }, singleLine = true)
                            OutlinedTextField(value = dateTime, onValueChange = { dateTime = it }, label = { Text("تاریخ و ساعت (yyyy-MM-dd HH:mm)") }, singleLine = true, placeholder = { Text("2026-12-01 18:30") })
                            Text("تاریخ را به میلادی وارد کن؛ انتخاب‌گر شمسی در نسخه بعدی اضافه می‌شود.", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(dateTime)?.time }.getOrNull()
                            if (title.isNotBlank() && parsed != null && parsed > System.currentTimeMillis()) {
                                val item = ReminderItem((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), title.trim(), parsed)
                                items = (items + item).sortedBy { it.at }; saveItems(context, items); schedule(context, item); showAdd = false
                            }
                        }) { Text("ذخیره") }
                    },
                    dismissButton = { TextButton(onClick = { showAdd = false }) { Text("انصراف") } }
                )
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        createChannel(context)
        val title = intent.getStringExtra("title") ?: "یادآوری"
        val notification = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("یادآوری")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            NotificationManagerCompat.from(context).notify(intent.getIntExtra("id", 1), notification)
    }
    companion object {
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(android.app.NotificationChannel("reminder_channel", "یادآوری‌ها", android.app.NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) loadItems(context).filter { !it.done && it.at > System.currentTimeMillis() }.forEach { schedule(context, it) }
    }
}
