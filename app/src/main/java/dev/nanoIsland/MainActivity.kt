package dev.nanoIsland

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var requestNotifAccessButton: Button
    private lateinit var attachButton: Button
    private lateinit var detachButton: Button

    private lateinit var punchHoleButton: Button
    private lateinit var pillButton: Button
    private lateinit var cardButton: Button
    private lateinit var testNotifButton: Button
    private lateinit var postHeadsUpButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(statusText)

        requestPermissionButton = Button(this).apply {
            text = "Request Overlay Permission"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        rootLayout.addView(requestPermissionButton)
        requestNotifAccessButton = Button(this).apply {
            text = "Grant Notification Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        rootLayout.addView(requestNotifAccessButton)


        attachButton = Button(this).apply {
            text = "Attach Overlay"
            setOnClickListener {
                val attached = IslandWindowManager.attach(this@MainActivity)
                Log.d("MainActivity", "Attach button clicked, success=$attached")
                updateUi()
            }
        }
        rootLayout.addView(attachButton)

        detachButton = Button(this).apply {
            text = "Detach Overlay"
            setOnClickListener {
                IslandWindowManager.detach()
                updateUi()
            }
        }
        rootLayout.addView(detachButton)

        val shapesHeader = TextView(this).apply {
            text = "\nDebug Shapes:"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        rootLayout.addView(shapesHeader)

        punchHoleButton = Button(this).apply {
            text = "Punch Hole (34x34)"
            setOnClickListener {
                IslandWindowManager.animateTo(IslandShape.PUNCH_HOLE) {
                    updateUi()
                }
                updateUi()
            }
        }
        rootLayout.addView(punchHoleButton)

        pillButton = Button(this).apply {
            text = "Pill (126x36)"
            setOnClickListener {
                IslandWindowManager.animateTo(IslandShape.PILL) {
                    updateUi()
                }
                updateUi()
            }
        }
        rootLayout.addView(pillButton)

        cardButton = Button(this).apply {
            text = "Card (360x170)"
            setOnClickListener {
                IslandWindowManager.animateTo(IslandShape.CARD) {
                    updateUi()
                }
                updateUi()
            }
        }
        rootLayout.addView(cardButton)
        val testHeader = TextView(this).apply {
            text = "\nNotification Interception:"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        rootLayout.addView(testHeader)

        testNotifButton = Button(this).apply {
            text = "Simulate Notification"
            setOnClickListener {
                IslandWindowManager.onNotificationReceived(
                    NotificationPayload(
                        key = "debug_test_${System.currentTimeMillis()}",
                        title = "WhatsApp",
                        text = "Hey! Let's meet at the cafe.",
                        icon = null,
                        packageName = "com.whatsapp"
                    )
                )
                updateUi()
            }
        }
        rootLayout.addView(testNotifButton)
        postHeadsUpButton = Button(this).apply {
            text = "Post System Heads-Up Notification"
            setOnClickListener {
                Log.d("MainActivity", "Posting heads up notification...")
                val channelId = "heads_up_test_channel"
                val notifManager = getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    channelId,
                    "Heads Up Test",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notifManager.createNotificationChannel(channel)

                val notif = androidx.core.app.NotificationCompat.Builder(this@MainActivity, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("WhatsApp")
                    .setContentText("Hey! Meeting you at the island.")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                val id = (System.currentTimeMillis() % 10000).toInt()
                notifManager.notify("test_heads_up", id, notif)
                updateUi()
            }
        }
        rootLayout.addView(postHeadsUpButton)
        val rapidBurstButton = Button(this).apply {
            text = "Post 5 Rapid Chats (Burst)"
            setOnClickListener {
                val channelId = "heads_up_test_channel"
                val notifManager = getSystemService(NotificationManager::class.java)
                val channel = NotificationChannel(
                    channelId,
                    "Heads Up Test",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notifManager.createNotificationChannel(channel)

                val messages = listOf(
                    "Hey! Are you awake?",
                    "Did you see the new Island update? It's looking really slick.",
                    "Let's test if rapid messages double banner or shake.",
                    "Fourth message in the burst! Still clean.",
                    "Final message: Zero double banners, perfectly aligned."
                )

                messages.forEachIndexed { index, msg ->
                    postDelayed({
                        val notif = androidx.core.app.NotificationCompat.Builder(this@MainActivity, channelId)
                            .setSmallIcon(android.R.drawable.ic_dialog_email)
                            .setContentTitle("WhatsApp • Sarah")
                            .setContentText(msg)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)
                            .build()
                        notifManager.notify("test_heads_up", 7000 + index, notif)
                    }, (index * 400L))
                }
            }
        }
        rootLayout.addView(rapidBurstButton)

        setContentView(rootLayout)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        IslandWindowManager.detach()
    }

    private fun updateUi() {
        val hasPermission = Settings.canDrawOverlays(this)
        val hasNotifAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val isAttached = IslandWindowManager.isAttached
        val currentShape = IslandWindowManager.currentShape

        val shapeName = when (currentShape) {
            IslandShape.PUNCH_HOLE -> "PUNCH_HOLE (34x34dp, r17)"
            IslandShape.PILL -> "PILL (126x36dp, r18)"
            IslandShape.CARD -> "CARD (360x170dp, r32)"
            else -> "${currentShape.widthDp}x${currentShape.heightDp}dp"
        }

        statusText.text = "Overlay Permission: ${if (hasPermission) "GRANTED" else "DENIED"}\n" +
            "Notification Access: ${if (hasNotifAccess) "GRANTED" else "DENIED"}\n" +
            "Overlay Attached: ${if (isAttached) "YES" else "NO"}\n" +
            "Current Shape: $shapeName"

        requestPermissionButton.isEnabled = !hasPermission
        requestNotifAccessButton.isEnabled = !hasNotifAccess
        attachButton.isEnabled = hasPermission && !isAttached
        detachButton.isEnabled = isAttached

        punchHoleButton.isEnabled = isAttached
        pillButton.isEnabled = isAttached
        cardButton.isEnabled = isAttached
        postHeadsUpButton.isEnabled = isAttached
        testNotifButton.isEnabled = isAttached
    }
}
