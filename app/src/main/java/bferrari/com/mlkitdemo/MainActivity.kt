package bferrari.com.mlkitdemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Pair
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 1
        val TAG = MainActivity::class.java.simpleName

        const val REQUEST_FROM_LIBRARY = 99
    }

    private var selectedImage: Bitmap? = null
    // Max width (portrait mode)
    private var imageMaxWidth: Int? = null
    // Max height (portrait mode)
    private var imageMaxHeight: Int? = null

    private var completeText = ""

    private lateinit var imageFilePath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textRecognitionBtn.setOnClickListener {
            completeText = ""
            selectedImage?.let { runTextRecognition(it) }
        }
        fab.setOnClickListener { dispatchCameraIntent() }
        setupSpinner()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.from_gallery -> dispatchPickFromLibraryIntent()
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupSpinner() {
        val items = arrayOf("Ignus Hiring", "Ignus Logo", "Image 1", "Image 2", "Image 3")
        val adapter = ArrayAdapter(this, android.R.layout
                .simple_spinner_dropdown_item, items)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = this
    }

    private fun runTextRecognition(selectedImage: Bitmap) {
        val image = FirebaseVisionImage.fromBitmap(selectedImage)
        val detector = FirebaseVision.getInstance().visionTextDetector

        detector.detectInImage(image)
                .addOnSuccessListener { texts ->
                    processTextRecognitionResult(texts)
                    reconText.text = completeText
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
    }

    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.blocks
        if (blocks.size == 0) {
            Toast.makeText(this, "No Text Found.", Toast.LENGTH_LONG).show()
            return
        }
        graphicOverlay.clear()
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
                for (k in elements.indices) {
                    val textGraphic = TextGraphic(graphicOverlay, elements[k])
                    completeText = completeText.plus(elements[k].text + " ")
                    graphicOverlay.add(textGraphic)
                    Log.d(TAG, k.toString())
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            data?.let {
                selectedImage = it.extras.get("data") as Bitmap

                Glide.with(this)
                        .load(selectedImage)
                        .apply {
                            RequestOptions().override(640,0)
                        }
                        .into(imageRecon)
            }
        } else if (requestCode == REQUEST_FROM_LIBRARY && resultCode == Activity.RESULT_OK) {
            data?.let {
                Glide.with(this)
                        .load(it.data)
                        .into(imageRecon)

                graphicOverlay.clear()

                selectedImage = MediaStore.Images.Media.getBitmap(this.contentResolver, it.data)
            }
        }
    }

    private fun getBitmapFromAsset(context: Context, filePath: String): Bitmap? {
        val assetManager = context.assets

        var bitmap: Bitmap? = null
        try {
            val inputStream = assetManager.open(filePath)
            bitmap = BitmapFactory.decodeStream(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return bitmap
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onItemSelected(parent: AdapterView<*>, v: View, position: Int, id: Long) {
        graphicOverlay.clear()

        selectedImage = when(position) {
            0 -> getBitmapFromAsset(this, "ignus_hiring.jpg")
            1 -> getBitmapFromAsset(this, "ignus.jpg")
            2 -> getBitmapFromAsset(this, "non-latin.jpg")
            3 -> getBitmapFromAsset(this, "nl2.jpg")
            4 -> getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg")
            else -> null
        }

        selectedImage?.let {
            val targetedSize = getTargetedWidthHeight()

            val targetWidth = targetedSize.first
            val maxHeight = targetedSize.second

            // Determine how much to scale down the image
            val scaleFactor = Math.max(
                    it.width.toFloat() / targetWidth.toFloat(),
                    it.height.toFloat() / maxHeight.toFloat())

            val resizedBitmap = Bitmap.createScaledBitmap(
                    it,
                    (it.width / scaleFactor).toInt(),
                    (it.height / scaleFactor).toInt(),
                    true)

            Glide.with(this).load(resizedBitmap).into(imageRecon)
            selectedImage = resizedBitmap
        }
    }

    private fun getTargetedWidthHeight(): Pair<Int, Int> {
        val targetWidth: Int
        val targetHeight: Int
        val maxWidthForPortraitMode = getImageMaxWidth()!!
        val maxHeightForPortraitMode = getImageMaxHeight()!!
        targetWidth = maxWidthForPortraitMode
        targetHeight = maxHeightForPortraitMode
        return Pair(targetWidth, targetHeight)
    }

    private fun getImageMaxWidth(): Int? {
        if (imageMaxWidth == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            imageMaxWidth = imageRecon.width
        }

        return imageMaxWidth
    }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private fun getImageMaxHeight(): Int? {
        if (imageMaxHeight == null) {
            // Calculate the max width in portrait mode. This is done lazily since we need to
            // wait for
            // a UI layout pass to get the right values. So delay it to first time image
            // rendering time.
            imageMaxHeight = imageRecon.height
        }

        return imageMaxHeight
    }

    private fun dispatchPickFromLibraryIntent() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }, REQUEST_FROM_LIBRARY)
    }

    private fun dispatchCameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.resolveActivity(packageManager)?.let {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        }
    }

}
