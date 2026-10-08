package com.riego.maceta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class PanelActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val refHumedad by lazy { db.child("riego/humedad") }
    private val refBomba by lazy { db.child("riego/bomba") }
    private val refModo by lazy { db.child("riego/modo") }

    private lateinit var tvHumedad: TextView
    private lateinit var tvBomba: TextView
    private lateinit var tvModo: TextView

    private val lHumedad = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            val v = (s.value as? Number)?.toDouble()
            tvHumedad.text = if (v != null) "$v%" else "--%"
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val lBomba = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            val on = s.value as? Boolean ?: false
            tvBomba.text = if (on) "Bomba: ON" else "Bomba: OFF"
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val lModo = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            tvModo.text = "Modo: ${s.value ?: "--"}"
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel)

        tvHumedad = findViewById(R.id.tvHumedad)
        tvBomba = findViewById(R.id.tvBomba)
        tvModo = findViewById(R.id.tvModo)
        val tvRol = findViewById<TextView>(R.id.tvRol)
        val btnBomba = findViewById<Button>(R.id.btnBomba)
        val btnAuto = findViewById<Button>(R.id.btnAuto)
        val btnHistorial = findViewById<Button>(R.id.btnHistorial)
        val btnSalir = findViewById<Button>(R.id.btnSalir)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Datos en vivo
        refHumedad.addValueEventListener(lHumedad)
        refBomba.addValueEventListener(lBomba)
        refModo.addValueEventListener(lModo)

        // Rol: solo el admin ve los botones de control
        db.child("users/${user.uid}/role").get().addOnSuccessListener { s ->
            val rol = s.value?.toString() ?: "sin rol"
            tvRol.text = "${user.email} (rol: $rol)"
            val visible = if (rol == "admin") View.VISIBLE else View.GONE
            btnBomba.visibility = visible
            btnAuto.visibility = visible
        }

        // Encender/apagar: pasa a manual y alterna la bomba
        btnBomba.setOnClickListener {
            refBomba.get().addOnSuccessListener { s ->
                val nuevo = !(s.value as? Boolean ?: false)
                refModo.setValue("manual")
                refBomba.setValue(nuevo).addOnFailureListener {
                    Toast.makeText(this, "Sin permiso", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAuto.setOnClickListener {
            refModo.setValue("auto").addOnFailureListener {
                Toast.makeText(this, "Sin permiso", Toast.LENGTH_SHORT).show()
            }
        }

        btnHistorial.setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }

        btnSalir.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        refHumedad.removeEventListener(lHumedad)
        refBomba.removeEventListener(lBomba)
        refModo.removeEventListener(lModo)
    }
}
