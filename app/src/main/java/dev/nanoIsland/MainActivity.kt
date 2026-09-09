package dev.nanoIsland

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var attachButton: Button
    private lateinit var detachButton: Button

    private lateinit var punchHoleButton: Button
    private lateinit var pillButton: Button
    private lateinit var cardButton: Button

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

        attachButton = Button(this).apply {
            text = "Attach Overlay"
            setOnClickListener {
                IslandWindowManager.attach(this@MainActivity)
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
            text = "Punch Hole (10x10)"
            setOnClickListener {
                IslandWindowManager.morphTo(IslandShape.PUNCH_HOLE)
                updateUi()
            }
        }
        rootLayout.addView(punchHoleButton)

        pillButton = Button(this).apply {
            text = "Pill (120x35)"
            setOnClickListener {
                IslandWindowManager.morphTo(IslandShape.PILL)
                updateUi()
            }
        }
        rootLayout.addView(pillButton)

        cardButton = Button(this).apply {
            text = "Card (360x180)"
            setOnClickListener {
                IslandWindowManager.morphTo(IslandShape.CARD)
                updateUi()
            }
        }
        rootLayout.addView(cardButton)

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
        val isAttached = IslandWindowManager.isAttached
        val currentShape = IslandWindowManager.currentShape

        val shapeName = when (currentShape) {
            IslandShape.PUNCH_HOLE -> "PUNCH_HOLE (10x10dp)"
            IslandShape.PILL -> "PILL (120x35dp)"
            IslandShape.CARD -> "CARD (360x180dp)"
            else -> "${currentShape.widthDp}x${currentShape.heightDp}dp"
        }

        statusText.text = "Overlay Permission: ${if (hasPermission) "GRANTED" else "DENIED"}\n" +
            "Overlay Attached: ${if (isAttached) "YES" else "NO"}\n" +
            "Current Shape: $shapeName"

        requestPermissionButton.isEnabled = !hasPermission
        attachButton.isEnabled = hasPermission && !isAttached
        detachButton.isEnabled = isAttached

        punchHoleButton.isEnabled = isAttached
        pillButton.isEnabled = isAttached
        cardButton.isEnabled = isAttached
    }
}
