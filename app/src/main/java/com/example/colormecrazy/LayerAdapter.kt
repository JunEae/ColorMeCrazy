package com.example.colormecrazy

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class LayerAdapter(
    private val canvasView: CanvasView,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<LayerAdapter.LayerViewHolder>() {

    inner class LayerViewHolder(val layout: LinearLayout) : RecyclerView.ViewHolder(layout)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayerViewHolder {
        val context = parent.context
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        return LayerViewHolder(layout)
    }

    fun createCheckerboardBackground(size: Int = 10): Drawable {
        val bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { style = Paint.Style.FILL }

        paint.color = Color.LTGRAY
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        canvas.drawRect(size.toFloat(), size.toFloat(), (size * 2).toFloat(), (size * 2).toFloat(), paint)

        paint.color = Color.WHITE
        canvas.drawRect(size.toFloat(), 0f, (size * 2).toFloat(), size.toFloat(), paint)
        canvas.drawRect(0f, size.toFloat(), size.toFloat(), (size * 2).toFloat(), paint)

        return BitmapDrawable(Resources.getSystem(), bitmap).apply {
            tileModeX = Shader.TileMode.REPEAT
            tileModeY = Shader.TileMode.REPEAT
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun getItemCount(): Int = canvasView.getLayersCount()

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: LayerViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val context = holder.layout.context
        val isActive = canvasView.getActiveLayerIndex() == position

        val layout = holder.layout
        layout.removeAllViews()

        layout.setBackgroundColor(
            if (isActive)
                ContextCompat.getColor(context, android.R.color.holo_blue_light)
            else
                Color.TRANSPARENT
        )

        layout.setOnClickListener {
            canvasView.setActiveLayer(position)
            notifyDataSetChanged()
        }

        // Миниатюра слоя
        val thumbnail = canvasView.getLayerThumbnail(position, 120, 120)
        val thumbnailView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(130, 130).apply { marginEnd = 16 }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            background = createCheckerboardBackground(8)
            setImageBitmap(thumbnail)
        }

        val label = TextView(context).apply {
            text = "Слой ${position + 1}"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        }

        val visibilityButton = ImageButton(context).apply {
            setImageResource(if (canvasView.isLayerVisible(position)) R.drawable.ic_eye_open else R.drawable.ic_eye_closed)
            background = null
            setOnClickListener {
                canvasView.setLayerVisibility(position, !canvasView.isLayerVisible(position))
                notifyItemChanged(position)
            }
        }

        val mergeButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_merge_layers)
            background = null
            setOnClickListener {
                val other = if (position > 0) position - 1 else position + 1
                if (other in 0 until canvasView.getLayersCount()) {
                    canvasView.mergeLayerInto(other, position)
                    notifyDataSetChanged()
                }
            }
        }

        val deleteButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_delete)
            background = null
            setOnClickListener {
                canvasView.deleteLayer(position)
                notifyDataSetChanged()
            }
        }

        val dragHandle = ImageButton(context).apply {
            setImageResource(R.drawable.ic_drag_handle)
            background = null
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                }
                false
            }
        }
        val opacitySeekBar = SeekBar(context).apply {
            max = 255
            progress = canvasView.getLayerOpacity(position)  // метод возвращает Int 0..255

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 16
                marginEnd = 16
                topMargin = 8
            }

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        canvasView.setLayerOpacity(position, progress)
                        // Можно сразу обновить слой и перерисовать канвас
                        notifyItemChanged(position)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(thumbnailView)
        layout.addView(label)
        layout.addView(visibilityButton)
        layout.addView(mergeButton)
        layout.addView(deleteButton)
        layout.addView(dragHandle)
        layout.addView(opacitySeekBar)
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun moveItem(fromPosition: Int, toPosition: Int) {
        canvasView.moveLayer(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        // Обновляем нумерацию слоев для всех элементов между fromPosition и toPosition
        val start = minOf(fromPosition, toPosition)
        val end = maxOf(fromPosition, toPosition)
        notifyItemRangeChanged(start, end - start + 1)
    }
}
