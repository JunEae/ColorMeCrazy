package com.example.colormecrazy

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GalleryAdapter(
    private val imageFiles: List<File>,
    private val onItemClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.image_view)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)

        fun bind(file: File) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            imageView.setImageBitmap(bitmap)

            itemView.setOnClickListener {
                onItemClick(file)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(file)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = imageFiles.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(imageFiles[position])
    }
}
