package com.example.aicamera

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private val photoAdapter = PhotoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "← Галерея"
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(dp(20), dp(44), dp(20), dp(16))
            setOnClickListener { finish() }
        })
        root.addView(RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 3)
            adapter = photoAdapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        reload()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ProcessingQueue.changes.collect { reload() }
            }
        }
    }

    private fun reload() {
        photoAdapter.submit(PhotoStore.listRaw(this))
    }

    private fun showActions(raw: File) {
        val pending = PhotoStore.isPending(raw)
        val options = arrayOf<CharSequence>(
            "Открыть",
            "Открыть оригинал",
            if (pending) "Обработать сейчас" else "Обработать заново",
            "Удалить"
        )
        AlertDialog.Builder(this)
            .setTitle(raw.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showImage(PhotoStore.displayFile(raw))
                    1 -> showImage(raw)
                    2 -> {
                        ProcessingQueue.enqueue(this, raw)
                        Toast.makeText(this, "Отправлено на обработку", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        raw.delete()
                        PhotoStore.aiFileFor(raw).delete()
                        reload()
                    }
                }
            }
            .show()
    }

    private fun showImage(file: File) {
        val bmp = PhotoStore.decodeUpright(file.readBytes(), 2048) ?: return
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        AlertDialog.Builder(this)
            .setView(iv)
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    inner class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.VH>() {

        private var items: List<File> = emptyList()

        fun submit(list: List<File>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val size = ctx.resources.displayMetrics.widthPixels / 3

            val frame = FrameLayout(ctx).apply {
                layoutParams = RecyclerView.LayoutParams(size, size)
            }
            val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val badge = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                setBackgroundColor(0x99000000.toInt())
                setPadding(dp(4), dp(2), dp(4), dp(2))
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            }
            frame.addView(img, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            frame.addView(badge)
            return VH(frame, img, badge)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val raw = items[position]
            val pending = PhotoStore.isPending(raw)

            holder.img.tag = raw.path
            holder.badge.text = "⏳"
            holder.badge.visibility = if (pending) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { showActions(raw) }

            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    PhotoStore.decodeUpright(PhotoStore.displayFile(raw).readBytes(), 300)
                }
                if (holder.img.tag == raw.path) holder.img.setImageBitmap(bmp)
            }
        }

        inner class VH(
            view: View,
            val img: ImageView,
            val badge: TextView
        ) : RecyclerView.ViewHolder(view)
    }
}
