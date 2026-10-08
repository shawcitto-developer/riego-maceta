package com.riego.maceta

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private val consulta = FirebaseDatabase.getInstance().reference
        .child("historial").limitToLast(20)
    private lateinit var tvLista: TextView

    private val listener = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
            val sb = StringBuilder()
            for (fila in s.children.toList().reversed()) {
                val v = (fila.child("valor").value as? Number)?.toDouble()
                val ts = (fila.child("ts").value as? Number)?.toLong() ?: 0L
                sb.append("${fmt.format(Date(ts * 1000))}   →   $v %\n")
            }
            tvLista.text = if (sb.isEmpty()) "Sin datos todavía" else sb.toString()
        }
        override fun onCancelled(e: DatabaseError) {
            tvLista.text = "Error: ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)
        tvLista = findViewById(R.id.tvLista)
        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
        consulta.addValueEventListener(listener)
    }

    override fun onDestroy() {
        super.onDestroy()
        consulta.removeEventListener(listener)
    }
}
