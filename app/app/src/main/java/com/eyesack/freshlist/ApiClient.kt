package com.eyesack.freshlist

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

interface ApiCallback {
    fun onSuccess(itemsToRemove: List<String>, jsonResponse: String)
    fun onFailure(message: String)
}

class ApiClient(private val context: Context) {

    private var endpoint: String = "http://10.0.0.116:8000/process-images/"  // Default endpoint
    private val sharedPreferencesName = "shopping_list_prefs"
    private val client = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())

    init {
        loadEndpoint()
    }

    private fun loadEndpoint() {
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        endpoint = sharedPreferences.getString("endpoint", endpoint) ?: endpoint
    }

    fun sendImage(bitmap: Bitmap, shoppingList: List<String>, callback: ApiCallback) {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 1024, 1024, true)
        val stream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()

        val listString = shoppingList.joinToString(separator = ",")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files", "image.jpg", RequestBody.create("image/jpeg".toMediaType(), byteArray))
            .addFormDataPart("reference_list", listString)
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ApiClient", "Failed to send image", e)
                handler.post { callback.onFailure("Failed to send image: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("ApiClient", "Unexpected response code: ${response.code}")
                    handler.post { callback.onFailure("Error: ${response.message}") }
                } else {
                    val jsonResponse = response.body?.string() ?: "No response"
                    try {
                        val itemsToRemove = handleApiResponse(jsonResponse)
                        handler.post { callback.onSuccess(itemsToRemove, jsonResponse) }
                    } catch (e: Exception) {
                        handler.post { callback.onFailure("Error processing response: ${e.message}") }
                    } finally {
                        response.close()
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
                itemsToRemove.add(itemsForRemoval.getString(i).lowercase())
            }
        } catch (e: JSONException) {
            throw e // Re-throw to be handled in the caller
        }
        return itemsToRemove
    }
}