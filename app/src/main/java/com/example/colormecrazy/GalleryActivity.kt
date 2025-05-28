package com.example.colormecrazy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GalleryAdapter
    private val imageFiles = mutableListOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
//        val userId = FirebaseAuth.getInstance().currentUser?.uid
//        val dir = File(getExternalFilesDir("drawings"), userId ?: "unknown")
//        if (!dir.exists()) dir.mkdirs()
//
//        val files = dir.listFiles()?.filter { it.extension == "png" }
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // Сетка из 2 столбцов
        adapter = GalleryAdapter(imageFiles,
            onItemClick = { imageFile ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("image_path", imageFile.absolutePath)
                }
                startActivityForResult(intent, REQUEST_CODE_EDIT)
            },
            onDeleteClick = { imageFile ->
                imageFile.delete()
                loadImages()
            }
        )

        recyclerView.adapter = adapter

        val createButton = findViewById<Button>(R.id.create_button)
        createButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_NEW)
        }

        loadImages()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && (requestCode == REQUEST_CODE_NEW || requestCode == REQUEST_CODE_EDIT)) {
            loadImages() // Обновить галерею после возврата
        }
    }

    private fun loadImages() {
        imageFiles.clear()
        val dir = getExternalFilesDir("drawings") ?: return
        val files = dir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }
        if (!files.isNullOrEmpty()) {
            imageFiles.addAll(files)
        }
        adapter.notifyDataSetChanged()
    }

    companion object {
        const val REQUEST_CODE_NEW = 1
        const val REQUEST_CODE_EDIT = 2
    }
}

