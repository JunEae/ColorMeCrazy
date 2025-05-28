package com.example.colormecrazy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class CanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    enum class ToolType { DRAW, ERASER, FILL, EYEDROPPER }
    enum class ActionType {
        GENERAL, ADD_LAYER, DELETE_LAYER, MERGE_LAYER, COLOR_CHANGE,
        ERASE_MODE_TOGGLE, SELECT_LAYER, CHANGE_VISIBILITY, CHANGE_OPACITY, MOVE_LAYER
    }

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val erasePaint = Paint().apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeWidth = 40f
        isAntiAlias = true
    }

    private val path = Path()
    private var isDrawing = false
    private var lastX = 0f
    private var lastY = 0f
    private var skipNextDraw = false

    private val layers = mutableListOf<Bitmap>()
    private val visibilityFlags = mutableListOf<Boolean>()
    private val layerAlphas = mutableListOf<Int>()
    private var activeLayerIndex = 0

    private val undoStack = mutableListOf<CanvasState>()
    private val redoStack = mutableListOf<CanvasState>()

    var currentTool: ToolType = ToolType.DRAW
    var eyedropperListener: ((Int) -> Unit)? = null
    val isErasing: Boolean get() = currentTool == ToolType.ERASER

    data class CanvasState(
        val layers: List<Bitmap>,
        val visibilityFlags: List<Boolean>,
        val layerAlphas: List<Int>,
        val activeLayerIndex: Int,
        val description: String,
        val actionType: ActionType = ActionType.GENERAL
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (layers.isEmpty()) {
            // создаём первый слой и сразу сохраняем «Начальное состояние» через saveStateForUndo
            layers.add(createEmptyBitmap(w, h))
            visibilityFlags.add(true)
            layerAlphas.add(255)
            activeLayerIndex = 0
            undoStack.clear()
            redoStack.clear()
            saveStateForUndo("Начальное состояние", ActionType.GENERAL)
            (context as? MainActivity)?.updateUndoRedoButtons()
        }
    }
    private fun createEmptyBitmap(w: Int, h: Int) =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in layers.indices) {
            if (visibilityFlags[i]) {
                val p = Paint().apply { alpha = getLayerOpacity(i) }
                canvas.drawBitmap(layers[i], 0f, 0f, p)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (skipNextDraw) {
            skipNextDraw = false
            return true
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                when (currentTool) {
                    ToolType.DRAW, ToolType.ERASER -> {
                        isDrawing = true
                        path.reset()
                        path.moveTo(x, y)
                        lastX = x
                        lastY = y
                    }

                    ToolType.FILL -> {
                        saveStateForUndo("Заливка", ActionType.GENERAL)
                        floodFill(layers[activeLayerIndex], x.toInt(), y.toInt(), paint.color)
                        invalidate()
                    }

                    ToolType.EYEDROPPER -> {
                        val composed = getBitmap()
                        if (x in 0f..composed.width.toFloat() && y in 0f..composed.height.toFloat()) {
                            val pickedColor = composed.getPixel(x.toInt(), y.toInt())
                            eyedropperListener?.invoke(pickedColor)
                            skipNextDraw = true
                            postDelayed({ setTool(ToolType.DRAW) }, 100)
                        }
                        return true
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    val dx = abs(x - lastX)
                    val dy = abs(y - lastY)
                    if (dx >= 4 || dy >= 4) {
                        path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                        lastX = x
                        lastY = y
                        Canvas(layers[activeLayerIndex])
                            .drawPath(path, if (isErasing) erasePaint else paint)
                        invalidate()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    path.lineTo(x, y)
                    Canvas(layers[activeLayerIndex])
                        .drawPath(path, if (isErasing) erasePaint else paint)
                    path.reset()
                    isDrawing = false
                    saveStateForUndo("Рисование завершено", ActionType.GENERAL)
                    invalidate()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    fun setTool(tool: ToolType) {
        currentTool = tool
    }

    fun toggleEraseMode() {
        val toEraser = currentTool != ToolType.ERASER
        saveStateForUndo("Режим ${if (toEraser) "стирания" else "рисования"}", ActionType.ERASE_MODE_TOGGLE)
        currentTool = if (toEraser) ToolType.ERASER else ToolType.DRAW
        invalidate()
    }

    fun setPaintColor(color: Int, saveToHistory: Boolean = true) {
        if (paint.color != color) {
            if (saveToHistory) saveStateForUndo("Цвет изменён", ActionType.COLOR_CHANGE)
            paint.color = color
            invalidate()
        }
    }

    fun canUndo() = undoStack.size > 1
    fun canRedo() = redoStack.isNotEmpty()

    fun undo(): String {
        if (undoStack.size <= 1) {
            // Откат невозможен — в стеке только начальное состояние
            (context as? MainActivity)?.updateUndoRedoButtons()
            return "Откат невозможен"
        }

        // Сохраняем текущее состояние в redo
        val currentState = undoStack.last()
        redoStack.add(currentState)

        // Удаляем текущее состояние и откатываемся к предыдущему
        undoStack.removeAt(undoStack.lastIndex)

        val prevState = undoStack.last()
        restoreState(prevState)
        invalidate()

        (context as? MainActivity)?.updateUndoRedoButtons()
        return "Откат: ${currentState.description}"
    }



    fun redo(): String {
        if (redoStack.isEmpty()) {
            (context as? MainActivity)?.updateUndoRedoButtons()
            return "Повтор невозможен"
        }

        val stateToRestore = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(stateToRestore)

        restoreState(stateToRestore)
        invalidate()

        (context as? MainActivity)?.updateUndoRedoButtons()
        return "Повтор: ${stateToRestore.description}"
    }


    private fun saveStateForUndo(description: String, actionType: ActionType = ActionType.GENERAL) {
        val snapshot = captureCurrentState(description, actionType)
        undoStack.add(snapshot)
        redoStack.clear()
        if (undoStack.size > 50) undoStack.removeAt(0)
        (context as? MainActivity)?.updateUndoRedoButtons()
    }
    private fun CanvasState.isContentEqual(other: CanvasState): Boolean {
        if (activeLayerIndex != other.activeLayerIndex) return false
        if (visibilityFlags != other.visibilityFlags) return false
        if (layerAlphas != other.layerAlphas)         return false
        // проверим биты каждого слоя
        return layers.zip(other.layers).all { (a, b) -> a.sameAs(b) }
    }
    private fun captureCurrentState(description: String, actionType: ActionType): CanvasState {
        val layerCopies = layers.map { it.deepCopy() }
        return CanvasState(
            layerCopies,
            visibilityFlags.toList(),
            layerAlphas.toList(),
            activeLayerIndex,
            description,
            actionType
        )
    }

    private fun restoreState(state: CanvasState) {
        layers.clear(); layers.addAll(state.layers.map { it.deepCopy() })
        visibilityFlags.clear(); visibilityFlags.addAll(state.visibilityFlags)
        layerAlphas.clear(); layerAlphas.addAll(state.layerAlphas)
        activeLayerIndex = state.activeLayerIndex
    }

    fun getBitmap(): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        for (i in layers.indices) {
            if (visibilityFlags[i]) {
                val p = Paint().apply { alpha = layerAlphas[i] }
                canvas.drawBitmap(layers[i], 0f, 0f, p)
            }
        }
        return result
    }

    private fun Bitmap.deepCopy(): Bitmap {
        val copy = Bitmap.createBitmap(width, height, config ?: Bitmap.Config.ARGB_8888)
        Canvas(copy).drawBitmap(this, 0f, 0f, null)
        return copy
    }


    fun floodFill(bitmap: Bitmap, startX: Int, startY: Int, targetColor: Int) {
        if (startX !in 0 until bitmap.width || startY !in 0 until bitmap.height) return

        val width = bitmap.width
        val height = bitmap.height
        val oldColor = bitmap.getPixel(startX, startY)
        if (oldColor == targetColor) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(startX to startY)

        while (stack.isNotEmpty()) {
            val (x, y) = stack.removeLast()
            if (x !in 0 until width || y !in 0 until height) continue
            val index = y * width + x
            if (pixels[index] != oldColor) continue

            pixels[index] = targetColor
            stack.addAll(listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1))
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }


    // ===== Layer Management =====

    fun addLayer(width: Int, height: Int) {
        layers.add(createEmptyBitmap(width, height))
        visibilityFlags.add(true)
        layerAlphas.add(255)
        activeLayerIndex = layers.lastIndex
        saveStateForUndo("Добавлен новый слой", ActionType.ADD_LAYER)
        invalidate()
    }


    fun setActiveLayer(index: Int) {
        if (index in layers.indices) {
            saveStateForUndo("Выбран слой $index", ActionType.SELECT_LAYER)
            activeLayerIndex = index
            invalidate()
        }
    }

    fun deleteLayer(index: Int) {
        if (layers.size <= 1 || index !in layers.indices) return
        saveStateForUndo("Удалён слой $index", ActionType.DELETE_LAYER)  // Сохраняем после удаления
        layers.removeAt(index)
        visibilityFlags.removeAt(index)
        layerAlphas.removeAt(index)
        activeLayerIndex = activeLayerIndex.coerceAtMost(layers.lastIndex)
        invalidate()
    }

    fun mergeLayerInto(targetIndex: Int, sourceIndex: Int) {
        if (targetIndex in layers.indices && sourceIndex in layers.indices && targetIndex != sourceIndex) {
            Canvas(layers[targetIndex]).drawBitmap(layers[sourceIndex], 0f, 0f, null)
            layers.removeAt(sourceIndex)
            visibilityFlags.removeAt(sourceIndex)
            layerAlphas.removeAt(sourceIndex)
            activeLayerIndex = when {
                activeLayerIndex == sourceIndex -> targetIndex
                activeLayerIndex > sourceIndex -> activeLayerIndex - 1
                else -> activeLayerIndex
            }
            saveStateForUndo("Объединены $sourceIndex → $targetIndex", ActionType.MERGE_LAYER)
            invalidate()
        }
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in layers.indices || toIndex !in 0..layers.size) return
        saveStateForUndo("Слой перемещён с $fromIndex на $toIndex", ActionType.MOVE_LAYER)
        val layer = layers.removeAt(fromIndex)
        val vis = visibilityFlags.removeAt(fromIndex)
        val alpha = layerAlphas.removeAt(fromIndex)

        layers.add(toIndex, layer)
        visibilityFlags.add(toIndex, vis)
        layerAlphas.add(toIndex, alpha)

        activeLayerIndex = when {
            activeLayerIndex == fromIndex -> toIndex
            fromIndex < activeLayerIndex && toIndex >= activeLayerIndex -> activeLayerIndex - 1
            fromIndex > activeLayerIndex && toIndex <= activeLayerIndex -> activeLayerIndex + 1
            else -> activeLayerIndex
        }
        invalidate()
    }

    fun setLayerVisibility(index: Int, visible: Boolean) {
        if (index in visibilityFlags.indices) {
            saveStateForUndo("Изменена видимость слоя $index", ActionType.CHANGE_VISIBILITY)
            visibilityFlags[index] = visible
            invalidate()
        }
    }

    fun setLayerOpacity(index: Int, opacity: Int) {
        if (index in layerAlphas.indices) {
            saveStateForUndo("Изменена прозрачность слоя $index", ActionType.CHANGE_OPACITY)
            layerAlphas[index] = opacity.coerceIn(0, 255)
            invalidate()
        }
    }

    fun getLayerOpacity(index: Int): Int = layerAlphas.getOrElse(index) { 255 }
    fun getLayersCount(): Int = layers.size
    fun getActiveLayerIndex(): Int = activeLayerIndex

    fun getLayerThumbnail(index: Int, maxWidth: Int = 100, maxHeight: Int = 100): Bitmap? {
        if (index !in layers.indices) return null
        val original = layers[index]
        val ratio = minOf(maxWidth.toFloat() / original.width, maxHeight.toFloat() / original.height)
        val width = (original.width * ratio).toInt()
        val height = (original.height * ratio).toInt()
        return Bitmap.createScaledBitmap(original, width, height, true)
    }
    fun isLayerVisible(index: Int): Boolean = visibilityFlags.getOrNull(index) ?: true

    fun loadBitmapAsLayer(bitmap: Bitmap) {
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        resized.config?.let { resized.copy(it, true) }?.let { layers.add(it) }
        visibilityFlags.add(true)
        layerAlphas.add(255)
        activeLayerIndex = layers.lastIndex
        saveStateForUndo("Загружен слой из файла", ActionType.ADD_LAYER)
        invalidate()
    }
    fun saveProjectToFile(file: File): Boolean {
        return try {
            val json = JSONObject()
            json.put("activeLayerIndex", activeLayerIndex)
            json.put("visibilityFlags", JSONArray(visibilityFlags))
            json.put("layerAlphas", JSONArray(layerAlphas))
            json.put("width", width)
            json.put("height", height)

            val layerDir = File(file.parentFile, file.nameWithoutExtension + "_layers")
            layerDir.mkdirs()

            val layerFilenames = JSONArray()
            for ((i, layer) in layers.withIndex()) {
                val layerFilename = "layer_$i.png"
                val layerFile = File(layerDir, layerFilename)
                FileOutputStream(layerFile).use { out ->
                    layer.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                layerFilenames.put(layerFilename)
            }

            json.put("layerFiles", layerFilenames)

            FileWriter(file).use { it.write(json.toString()) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    fun loadProjectFromFile(file: File): Boolean {
        return try {
            val json = JSONObject(file.readText())

            val w = json.getInt("width")
            val h = json.getInt("height")
            val activeIndex = json.getInt("activeLayerIndex")
            val visFlags = json.getJSONArray("visibilityFlags")
            val alphas = json.getJSONArray("layerAlphas")
            val layerFiles = json.getJSONArray("layerFiles")

            val layerDir = File(file.parentFile, file.nameWithoutExtension + "_layers")
            if (!layerDir.exists() || !layerDir.isDirectory) return false

            val loadedLayers = mutableListOf<Bitmap>()
            for (i in 0 until layerFiles.length()) {
                val filename = layerFiles.getString(i)
                val layerFile = File(layerDir, filename)
                if (!layerFile.exists()) return false
                val bmp = BitmapFactory.decodeFile(layerFile.absolutePath)
                loadedLayers.add(bmp.copy(Bitmap.Config.ARGB_8888, true))
            }

            layers.clear(); layers.addAll(loadedLayers)
            visibilityFlags.clear(); visibilityFlags.addAll((0 until visFlags.length()).map { visFlags.getBoolean(it) })
            layerAlphas.clear(); layerAlphas.addAll((0 until alphas.length()).map { alphas.getInt(it) })
            activeLayerIndex = activeIndex

            saveStateForUndo("Проект загружен", ActionType.GENERAL)
            invalidate()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearCanvas() {
        layers.clear()
        addLayer(width, height)
        invalidate()
    }

}
