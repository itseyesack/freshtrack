package com.eyesack.freshlist

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

interface ImageHandlerListener {
    fun onImageCaptured(bitmap: Bitmap)
    fun onImagePicked(bitmap: Bitmap)
    fun onImageError(message: String)
}

class ImageHandler(private val activity: Activity, private val listener: ImageHandlerListener) {

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_PICK = 2
    private val CAMERA_PERMISSION_CODE = 100
    private val TAG = "Camera"
    private var photoUri: Uri? = null

    fun takePicture() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            dispatchTakePictureIntent()
        }
    }

    fun pickImageFromGallery() {
        dispatchPickImageIntent()
    }


    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            listener.onImageError("Error creating image file: ${ex.message}")
            return
        }
        photoFile?.also {
            photoUri = FileProvider.getUriForFile(activity, "com.eyesack.freshlist.fileprovider", it)
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            if (takePictureIntent.resolveActivity(activity.packageManager) != null) {
                activity.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
            } else {
                listener.onImageError("No camera app found")
            }
        }
    }

    private fun dispatchPickImageIntent() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivityForResult(intent, REQUEST_IMAGE_PICK)
        } else {
            listener.onImageError("No gallery app found")
        }
    }
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${System.currentTimeMillis()}", ".jpg", storageDir)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    photoUri?.let { uri ->
                        try {
                            var bitmap = MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
                            bitmap = rotateImageIfRequired(bitmap, uri)
                            listener.onImageCaptured(bitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            listener.onImageError("Failed to load image: ${e.message}")
                        }
                    }
                }
                REQUEST_IMAGE_PICK -> {
                    data?.data?.let { uri ->
                        try {
                            var bitmap = MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
                            bitmap = rotateImageIfRequired(bitmap, uri)
                            listener.onImagePicked(bitmap)
                        } catch (e: IOException) {
                            e.printStackTrace()
                            listener.onImageError("Failed to load image: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                dispatchTakePictureIntent()
            } else {
                listener.onImageError("Camera permission is required to use this feature")
            }
        }
    }

    private fun rotateImageIfRequired(img: Bitmap, uri: Uri): Bitmap {
        val input = activity.contentResolver.openInputStream(uri)
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
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    }
}