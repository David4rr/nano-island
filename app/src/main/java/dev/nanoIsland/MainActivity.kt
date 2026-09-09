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
            text = "Attach Overlay (10x10dp)"
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

        statusText.text = "Overlay Permission: ${if (hasPermission) "GRANTED" else "DENIED"}\n" +
            "Overlay Attached: ${if (isAttached) "YES" else "NO"}"

        requestPermissionButton.isEnabled = !hasPermission
        attachButton.isEnabled = hasPermission && !isAttached
        detachButton.isEnabled = isAttached
    }
}
