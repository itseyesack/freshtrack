package com.eyesack.freshlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInButton: SignInButton
    private lateinit var signOutButton: Button
    private lateinit var setEndpointButton: Button
    private lateinit var backButton: ImageButton // Add backButton
    private lateinit var debugToggle: SwitchCompat // Debug toggle
    private val RC_GOOGLE_SIGN_IN = 4926
    private var endpoint: String = "http://10.0.0.116:8000/process-images/" // Default endpoint
    private val sharedPreferencesName = "shopping_list_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()

        // Initialize UI elements
        signInButton = findViewById(R.id.btnSignIn)
        signOutButton = findViewById(R.id.btnProfile)
        setEndpointButton = findViewById(R.id.setEndpointButton)
        backButton = findViewById(R.id.backButton) // Find the back button
        debugToggle = findViewById(R.id.debugToggle) // Find the debug toggle

        // Google Sign-In setup
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Load the current endpoint
        loadEndpoint()
        loadDebugMode() // Load debug mode state

        // Set click listeners
        signInButton.setOnClickListener { signIn() }
        signOutButton.setOnClickListener { signOut() }
        setEndpointButton.setOnClickListener { showSetEndpointDialog() }
        backButton.setOnClickListener { finish() } // Finish the activity

        // Debug toggle listener
        debugToggle.setOnCheckedChangeListener { _, isChecked ->
            saveDebugMode(isChecked)
        }

        // Update UI based on user sign-in status
        updateUI(auth.currentUser)
    }

    private fun signIn() {
        googleSignInClient.signOut().addOnCompleteListener(this){
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
        }
    }

    private fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            updateUI(null) // Update UI after signing out
            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSetEndpointDialog() {
        val editText = EditText(this)
        editText.setText(endpoint)
        AlertDialog.Builder(this)
            .setTitle("Set Endpoint")
            .setMessage("Enter the server endpoint URL:")
            .setView(editText)
            .setPositiveButton("Save") { _,_ ->
                endpoint = editText.text.toString().trim()
                saveEndpoint()
                Toast.makeText(this, "Endpoint saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveEndpoint() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("endpoint", endpoint).apply()
    }

    private fun loadEndpoint() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        endpoint = sharedPreferences.getString("endpoint", endpoint) ?: endpoint // Load, fallback to default
    }


    private fun saveDebugMode(isDebug: Boolean) {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("debug_mode", isDebug).apply()
    }

    private fun loadDebugMode() {
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val isDebug = sharedPreferences.getBoolean("debug_mode", false) // Default to false
        debugToggle.isChecked = isDebug
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("ProfileActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                    updateUI(null)
                }
            }
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            // User is signed in
            signInButton.isEnabled = false
            signOutButton.isEnabled = true
            Toast.makeText(this, "Signed in as ${user.email}", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

        } else {
            // User is signed out
            signInButton.isEnabled = true
            signOutButton.isEnabled = false
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is already signed in
        updateUI(auth.currentUser)
    }
}