package com.aurizen.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import net.openid.appauth.AuthorizationService
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.TokenRequest
import com.aurizen.data.SecureStorage
import com.aurizen.settings.AuthConfig
import com.aurizen.core.InferenceModel
import com.aurizen.MainActivity

class OAuthCallbackActivity : Activity() {
  private lateinit var authService: AuthorizationService
  private val TAG = OAuthCallbackActivity::class.qualifiedName

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    authService = AuthorizationService(this)

    val data: Uri? = intent.data
    if (data != null) {
      // Manually extract the authorization code
      val authCode = data.getQueryParameter("code")
      // val authState = data.getQueryParameter("state")

      if (authCode != null) {
        // Retrieve the code verifier that was used in the initial request
        val codeVerifier = SecureStorage.getCodeVerifier(applicationContext)

        // Create a Token Request manually
        val tokenRequest = TokenRequest.Builder(
          AuthConfig.authServiceConfig, // Ensure this is properly set up
          AuthConfig.clientId
        )
          .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
          .setAuthorizationCode(authCode)
          .setRedirectUri(Uri.parse(AuthConfig.redirectUri))
          .setCodeVerifier(codeVerifier) // Required for PKCE
          .build()

        authService.performTokenRequest(tokenRequest) { response, ex ->
          if (response != null) {
            val accessToken = response.accessToken
            SecureStorage.saveToken(
              applicationContext,
              accessToken ?: ""
            )
            Toast.makeText(this, "Sign in succeeded", Toast.LENGTH_LONG).show()

            val licenseUrl = InferenceModel.model.licenseUrl
            if (licenseUrl.isEmpty()) {
              // Go to loading screen to download the model after successful authentication
              val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("NAVIGATE_TO", "load_screen")
              }
              startActivity(intent)
            } else {
              val intent = Intent(this, LicenseAcknowledgmentActivity::class.java)
              startActivity(intent)
            }
            finish()
          } else {
            Log.e(TAG,"OAuth Error: ${ex?.message}")
          }
          finish()
        }
      } else {
        Log.e(TAG,"No Authorization Code Found")
        finish()
      }
    } else {
      Log.e(TAG,"OAuth Failed: No Data in Intent")
      finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    authService.dispose()
  }
}
