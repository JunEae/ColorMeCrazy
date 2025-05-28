package com.example.colormecrazy

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.larswerkman.holocolorpicker.ColorPicker
import java.io.File
import java.io.FileOutputStream


private const val REQUEST_CODE_PERMISSIONS = 101
private const val PREFS_NAME = "color_prefs"
private const val KEY_SELECTED_COLOR = "selected_color"

class MainActivity : AppCompatActivity(), ColorPicker.OnColorChangedListener {

    private lateinit var colorPickerButton: Button
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var layerButton: ImageButton
    private lateinit var rootView: View
    private lateinit var canvasView: CanvasView
    private lateinit var toolButton: ImageButton
    private var selectedColor: Int = 0xFF000000.toInt()
    lateinit var layersAdapter: LayerAdapter

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        rootView = findViewById(android.R.id.content)
        selectedColor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SELECTED_COLOR, 0xFF000000.toInt())
        canvasView = findViewById(R.id.canvas_view)
        colorPickerButton = findViewById(R.id.change_color_button)
        toolButton = findViewById(R.id.tool_button)
        val eraseButton = findViewById<ImageButton>(R.id.erase_button)
        val saveButton = findViewById<ImageButton>(R.id.save_button)
        undoButton = findViewById(R.id.undo_button)
        redoButton = findViewById(R.id.redo_button)
        layerButton = findViewById(R.id.layer_button)

        canvasView.setPaintColor(selectedColor)
        colorPickerButton.background.setTint(selectedColor)

        canvasView.post {
            val imagePath = intent.getStringExtra("image_path")

            if (imagePath != null) {
                // Режим редактирования — загружаем изображение
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    canvasView.loadBitmapAsLayer(bitmap)
                }
            } else {
                // Режим создания нового — очищаем холст
                canvasView.clearCanvas()
            }
        }



        colorPickerButton.setOnClickListener { showColorPickerDialog() }
        toolButton.setOnClickListener { showToolSelectionDialog() }

        eraseButton.setOnClickListener {
            canvasView.toggleEraseMode()
            val iconRes = if (canvasView.isErasing) R.drawable.ic_draw else R.drawable.ic_erase
            eraseButton.setImageResource(iconRes)
        }

        saveButton.setOnClickListener {
            saveImage()
        }

        undoButton.setOnClickListener {
            val message = canvasView.undo()
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            updateUndoRedoButtons()
        }

        redoButton.setOnClickListener {
            val message = canvasView.redo()
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            updateUndoRedoButtons()
        }

        layerButton.setOnClickListener {
            showLayerDialog()
        }

        updateUndoRedoButtons()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }


    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun updateUndoRedoButtons() {
        undoButton.isEnabled = canvasView.canUndo()
        redoButton.isEnabled = canvasView.canRedo()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun showToolSelectionDialog() {
        val tools = arrayOf("Кисть", "Ластик", "Заливка", "Пипетка")
        AlertDialog.Builder(this)
            .setTitle("Выберите инструмент")
            .setItems(tools) { _, which ->
                val tool = when (which) {
                    0 -> CanvasView.ToolType.DRAW
                    1 -> CanvasView.ToolType.ERASER
                    2 -> CanvasView.ToolType.FILL
                    3 -> CanvasView.ToolType.EYEDROPPER
                    else -> CanvasView.ToolType.DRAW
                }
                canvasView.setTool(tool)

                val iconRes = when (tool) {
                    CanvasView.ToolType.DRAW -> R.drawable.ic_draw
                    CanvasView.ToolType.ERASER -> R.drawable.ic_erase
                    CanvasView.ToolType.FILL -> R.drawable.ic_fill
                    CanvasView.ToolType.EYEDROPPER -> R.drawable.ic_eyedropper
                }
                toolButton.setImageResource(iconRes)

                if (tool == CanvasView.ToolType.EYEDROPPER) {
                    canvasView.eyedropperListener = { pickedColor ->
                        selectedColor = pickedColor
                        canvasView.setPaintColor(pickedColor)
                        colorPickerButton.background.setTint(pickedColor)
                        Toast.makeText(this, "Цвет выбран", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun showColorPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val picker = dialogView.findViewById<ColorPicker>(R.id.picker)
        picker.addSVBar(dialogView.findViewById(R.id.svbar))
        picker.addOpacityBar(dialogView.findViewById(R.id.opacitybar))
        picker.addSaturationBar(dialogView.findViewById(R.id.saturationbar))
        picker.addValueBar(dialogView.findViewById(R.id.valuebar))

        picker.color = selectedColor
        picker.oldCenterColor = selectedColor

        AlertDialog.Builder(this)
            .setTitle("Цвет")
            .setView(dialogView)
            .setPositiveButton("OK") { dialog, _ ->
                selectedColor = picker.color
                canvasView.setPaintColor(selectedColor)
                colorPickerButton.background.setTint(selectedColor)
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_SELECTED_COLOR, selectedColor).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Закрыть") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun saveImage() {
        val bitmap = canvasView.getBitmap()
        val savedImageURL = MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            "Изображение_${System.currentTimeMillis()}",
            "Мое изображение"
        )

        Toast.makeText(
            this,
            if (savedImageURL != null) "Изображение сохранено в галерею!"
            else "Невозможно сохранить изображение.",
            Toast.LENGTH_SHORT
        ).show()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun showLayerDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Слои")

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                layersAdapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        }

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

        layersAdapter = LayerAdapter(canvasView) { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }

        recyclerView.adapter = layersAdapter
        itemTouchHelper.attachToRecyclerView(recyclerView)

        builder.setView(recyclerView)
        builder.setPositiveButton("Добавить слой", null)
        builder.setNegativeButton("Закрыть", null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            canvasView.addLayer(canvasView.width, canvasView.height)
            layersAdapter.notifyDataSetChanged()
            updateUndoRedoButtons()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onColorChanged(color: Int) {
        canvasView.setPaintColor(color)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onSupportNavigateUp(): Boolean {
        saveAndReturnToGallery()
        return true
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onBackPressed() {
        super.onBackPressed()
        saveAndReturnToGallery()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun saveAndReturnToGallery() {
        val imagePath = intent.getStringExtra("image_path")
        val file = if (imagePath != null) {
            File(imagePath) // Сохраняем в тот же файл при редактировании
        } else {
            val drawingsDir = File(getExternalFilesDir("drawings"), "")
            if (!drawingsDir.exists()) drawingsDir.mkdirs()
            val fileName = "drawing_${System.currentTimeMillis()}.png"
            File(drawingsDir, fileName)
        }

        FileOutputStream(file).use { outputStream ->
            canvasView.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
        }

        val resultIntent = Intent()
        resultIntent.putExtra("imagePath", file.absolutePath)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

}



