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

    private lateinit var local: DbLocal
    private lateinit var tvLista: TextView
    private lateinit var tvOrigen: TextView
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    private val consulta = FirebaseDatabase.getInstance().reference.child("historial").limitToLast(100)
    private var escuchando = false

    private val listener = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            for (fila in s.children) {
                val v = (fila.child("valor").value as? Number)?.toDouble() ?: continue
                val ts = (fila.child("ts").value as? Number)?.toLong() ?: continue
                local.guardarLectura(ts, v)           // copia en el telefono (hasta 7 dias)
            }
            mostrar("En línea")
        }
        override fun onCancelled(e: DatabaseError) {
            mostrar("Sin conexión")
        }
    }

    private fun mostrar(origen: String) {
        val filas = local.ultimasLecturas(20)
        tvOrigen.text = "$origen · ${local.totalLecturas()} lecturas guardadas en el teléfono (máx. 7 días)"
        tvLista.text = if (filas.isEmpty()) "Sin datos todavía" else
            filas.joinToString("\n") { "${fmt.format(Date(it.first * 1000))}   →   ${it.second} %" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)
        local = DbLocal(this)
        tvLista = findViewById(R.id.tvLista)
        tvOrigen = findViewById(R.id.tvOrigen)
        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
        mostrar("Datos guardados")                    // se ve al instante, aunque no haya red
    }

    override fun onStart() {
        super.onStart()
        if (!escuchando) { consulta.addValueEventListener(listener); escuchando = true }
    }

    override fun onStop() {
        super.onStop()
        if (escuchando) { consulta.removeEventListener(listener); escuchando = false }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::local.isInitialized) local.close()
    }
}
