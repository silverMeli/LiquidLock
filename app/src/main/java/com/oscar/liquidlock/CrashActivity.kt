package com.oscar.liquidlock

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Muestra el último error (sin Compose, para que funcione aunque Compose falle). */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = App.crashFile(this)
        val text = if (file.exists()) file.readText() else "Sin errores guardados."
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            setText("La app se cerró. Copia este error y pégaselo a Claude:")
            setTextColor(Color.WHITE)
            textSize = 17f
            setPadding(0, pad * 2, 0, pad)
        }
        val copy = Button(this).apply {
            setText("Copiar error")
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("error", text))
                Toast.makeText(this@CrashActivity, "Copiado", Toast.LENGTH_SHORT).show()
            }
        }
        val dismiss = Button(this).apply {
            setText("Borrar y continuar")
            setOnClickListener {
                file.delete()
                startActivity(Intent(this@CrashActivity, MainActivity::class.java))
                finish()
            }
        }
        val log = TextView(this).apply {
            setText(text)
            setTextColor(Color.LTGRAY)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, pad, 0, pad * 3)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(copy)
            addView(dismiss)
            addView(log)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(column)
        })
    }
}
