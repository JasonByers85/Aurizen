package com.aurizen.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.aurizen.core.InferenceModel
import com.aurizen.MainActivity
import com.aurizen.R

class LicenseAcknowledgmentActivity : AppCompatActivity() {
  private lateinit var acknowledgeButton: Button
  private lateinit var continueButton: Button

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_license_acknowledgment)

    val licenseUrl = InferenceModel.model.licenseUrl
    if (licenseUrl.isEmpty()) {
      Toast.makeText(this, "Missing license URL, please try again", Toast.LENGTH_LONG).show()
      startActivity(Intent(this, MainActivity::class.java))
      finish()
    }

    acknowledgeButton = findViewById(R.id.btnAcknowledge)
    continueButton = findViewById(R.id.btnContinue)

    // Disable "Continue" button initially
    continueButton.isEnabled = false

    acknowledgeButton.setOnClickListener {
      val customTabsIntent = CustomTabsIntent.Builder().build()
      customTabsIntent.launchUrl(this, Uri.parse(licenseUrl))

      // Enable "Continue" if user viewed license
      continueButton.isEnabled = true
    }

    continueButton.setOnClickListener {
      // Go to loading screen to download the model after license acknowledgment
      val intent = Intent(this, MainActivity::class.java).apply {
        putExtra("NAVIGATE_TO", "load_screen")
      }
      startActivity(intent)
      finish()
    }

  }

  override fun onResume() {
    super.onResume()
    // Enable "Continue" if user viewed license
    // continueButton.isEnabled = true
  }
}
