package com.eyesack.freshlist

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class CameraActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val CAMERA_PERMISSION_CODE = 100
    private val TAG = "CameraActivity"
    private var photoUri: Uri? = null
    private lateinit var endpoint: String // Variable to hold the endpoint
    private val handler = Handler(Looper.getMainLooper()) // Handler for UI updates
    private val sharedPreferencesName = "pantry_prefs" // SharedPreferences for pantry
    private val pantryKey = "pantry_items"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Retrieve the endpoint from the intent
        endpoint = intent.getStringExtra("endpoint") ?: "http://10.0.0.116:8000/process-images/"

        imageView = findViewById(R.id.imageView)
        val useImagePicker = intent.getBooleanExtra("use_image_picker", false)
        if (useImagePicker) {
            // Launch the image picker
            dispatchPickImageIntent()
        } else {
            // Launch the camera
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            } else {
                dispatchTakePictureIntent()
            }
        }
    }

    private fun dispatchPickImageIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        } else {
            Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show()
            returnToMainActivity() // Return to MainActivity
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createImageFile()
        photoUri = FileProvider.getUriForFile(
            this,
            "com.eyesack.freshlist.fileprovider",
            photoFile
        )
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show()
            returnToMainActivity() // Return to MainActivity
        }
    }

    private fun createImageFile(): File {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${System.currentTimeMillis()}", ".jpg", storageDir)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to use this feature",
                    Toast.LENGTH_SHORT
                ).show()
                returnToMainActivity() // Return to MainActivity
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    photoUri?.let { uri ->
                        try {
                            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            bitmap = rotateImageIfRequired(bitmap, uri)
                            imageView.setImageBitmap(bitmap)
                            sendImageToServer(bitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                            returnToMainActivity() // Return to MainActivity on image load failure
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        try {
                            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            bitmap = rotateImageIfRequired(bitmap, uri)
                            imageView.setImageBitmap(bitmap)
                            sendImageToServer(bitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                            returnToMainActivity() // Return to MainActivity on image load failure
                        }
                    }
                }
            }
        } else {
            returnToMainActivity()
        }
    }

    private fun rotateImageIfRequired(img: Bitmap, uri: Uri): Bitmap {
        val input = contentResolver.openInputStream(uri)
        val ei = input?.let { ExifInterface(it) }
        val orientation = ei?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }

    private fun sendImageToServer(bitmap: Bitmap) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 1024, 1024, true)
        val stream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()

        val sharedPreferences = getSharedPreferences("shopping_list_prefs", Context.MODE_PRIVATE)
        val jsonList = sharedPreferences.getString("shopping_list", "[]")
        val listItems = try {
            Gson().fromJson(jsonList, Array<String>::class.java).toList()
        } catch (e: JsonSyntaxException) {
            // Handle JSON parsing error (e.g., invalid JSON string)
            Log.e(TAG, "Error parsing shopping list from SharedPreferences", e)
            returnToMainActivity() // Go back to MainActivity
            return // Important: Exit the function to prevent further execution
        }

        val listString = listItems.joinToString(separator = ",")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files", "image.jpg", RequestBody.create("image/jpeg".toMediaType(), byteArray))
            .addFormDataPart("reference_list", listString)
            .build()

        val client = OkHttpClient.Builder()
            .readTimeout(120, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send image", e)
                handler.post {
                    Toast.makeText(
                        this@CameraActivity,
                        "Failed to send image, is server up?",
                        Toast.LENGTH_SHORT
                    ).show()
                    returnToMainActivity() // Return to MainActivity on failure
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response code: ${response.code}")
                    handler.post {
                        Toast.makeText(
                            this@CameraActivity,
                            "Error: ${response.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        returnToMainActivity() // Return to main if the request fails
                    }
                } else {
                    val jsonResponse = response.body?.string()
                    // Try to handle the API response, return to main on any error
                    try {
                        val itemsToRemove = handleApiResponse(jsonResponse ?: "No response")  //get return

                        if (itemsToRemove.isEmpty()) {
                            handler.post{
                                Toast.makeText(this@CameraActivity, "No matching items found.", Toast.LENGTH_SHORT).show()
                                returnToMainActivity()
                            }
                        } else{
                            // Show popup first, *then* start the activity
                            if (!isFinishing && !isDestroyed) {
                                handler.post {
                                    showJsonPopup(jsonResponse ?: "No response") {
                                        val intent = Intent(this@CameraActivity, MainActivity::class.java)
                                        intent.putExtra("items_to_cross_off", ArrayList(itemsToRemove))
                                        startActivity(intent)
                                    }
                                }
                            }
                        }

                    } catch (e: Exception) {
                        handler.post {
                            Toast.makeText(this@CameraActivity, "Error processing response.", Toast.LENGTH_SHORT).show()
                            returnToMainActivity()  // Return to main on any error
                        }
                    } finally {
                        response.close() //ensure response is closed
                    }
                }

            }
        })
    }

    private fun handleApiResponse(response: String): List<String> {
        val itemsToRemove = mutableListOf<String>()
        try {
            val jsonResponse = JSONObject(response)
            val itemsForRemoval = jsonResponse.getJSONArray("items_for_removal")
            for (i in 0 until itemsForRemoval.length()) {
                itemsToRemove.add(itemsForRemoval.getString(i).toLowerCase())
            }

            // Update Pantry Items - Process *all* receipts, not just items for removal
            updatePantryItems(jsonResponse) // Extract and add all new items.

        } catch (e: JSONException) {
            e.printStackTrace()
            handler.post {
                Toast.makeText(this, "Error parsing API response: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            returnToMainActivity()
        }
        return itemsToRemove
    }

    private fun showJsonPopup(jsonResponse: String, onDismiss: () -> Unit) {
        val textView = TextView(this).apply {
            text = jsonResponse
            setPadding(16, 16, 16, 16)
        }

        AlertDialog.Builder(this)
            .setTitle("JSON Response")
            .setView(ScrollView(this).apply { addView(textView) })
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .show()
    }


    private fun returnToMainActivity() {
        handler.post {
            val intent = Intent(this@CameraActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP  //Very important
            startActivity(intent)
            finish() // Finish CameraActivity
        }
    }


    private fun updatePantryItems(jsonResponse: JSONObject) {
        val receipts = jsonResponse.optJSONArray("receipts") ?: return

        val newPantryItems = mutableListOf<PantryItem>()

        for (i in 0 until receipts.length()) {
            val receipt = receipts.getJSONObject(i)
            val items = receipt.optJSONArray("items") ?: continue

            for (j in 0 until items.length()) {
                val item = items.getJSONObject(j)
                val name = item.optString("friendly_name", item.optString("name", "Unknown"))
                val category = item.optString("category", "Unknown")
                val upc = item.optString("upc", "Not Found")

                // Extract all details into a map
                val details = mutableMapOf<String, String>()
                details["Store"] = receipt.optString("Store", "Unknown")
                details["Address"] = receipt.optString("Address", "Unknown")
                details["Date"] = receipt.optString("Date", "Unknown")
                details["Quantity"] = item.optString("quantity","Unknown")
                details["Price"] = item.optString("price", "Unknown")
                details["Unit Price"] = item.optString("unit_price", "Unknown")
                details["Unit"] = item.optString("unit", "Unknown")
                details["UPC"] = upc
                details["Last Purchased"] = receipt.optString("Date", "Unknown") // Initially set to current receipt date

                newPantryItems.add(PantryItem(name, category, details))
            }
        }
        // Load existing pantry items
        val sharedPreferences = getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val existingPantryJson = sharedPreferences.getString(pantryKey, null)
        val existingPantryItems = if (existingPantryJson != null) {
            Gson().fromJson(existingPantryJson, object : TypeToken<MutableList<PantryItem>>() {}.type)
        } else {
            mutableListOf<PantryItem>()
        }

        // Update or add items
        for (newItem in newPantryItems) {
            val existingItemIndex = existingPantryItems.indexOfFirst { existing ->
                existing.name == newItem.name ||
                        (newItem.details["UPC"] != "Not Found" && newItem.details["UPC"]?.matches(Regex("\\d+")) == true && existing.details["UPC"] == newItem.details["UPC"])
            }

            if (existingItemIndex != -1) {
                // Item exists, update "Last Purchased"
                // Create a mutable copy of the details map
                val updatedDetails = existingPantryItems[existingItemIndex].details.toMutableMap()
                // Update the "Last Purchased" field in the mutable copy
                updatedDetails["Last Purchased"] = newItem.details["Last Purchased"] ?: "Unknown"
                // Create a new PantryItem with the updated details
                existingPantryItems[existingItemIndex] = existingPantryItems[existingItemIndex].copy(details = updatedDetails)

            } else {
                // New item, add to the list
                existingPantryItems.add(newItem)
            }
        }

        // Save the updated list
        val editor = sharedPreferences.edit()
        editor.putString(pantryKey, Gson().toJson(existingPantryItems))
        editor.apply()
    }
}