package com.riego.maceta

import android.content.Intent
import android.graphics.Color
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

    private val bd = FirebaseDatabase.getInstance()
    private val raiz = bd.reference
    private lateinit var local: DbLocal

    private lateinit var tvConexion: TextView
    private lateinit var tvPendientes: TextView
    private lateinit var tvDispositivo: TextView
    private lateinit var tvHumedad: TextView
    private lateinit var tvBomba: TextView
    private lateinit var tvModo: TextView
    private lateinit var tvRol: TextView
    private lateinit var btnBomba: Button
    private lateinit var btnAuto: Button

    private var conectado = false
    private var esAdmin = false
    private var bombaActual = false
    private var sincronizando = false
    private var escuchando = false

    private val refConexion = bd.getReference(".info/connected")
    private val refHumedad = raiz.child("riego/humedad")
    private val refBomba = raiz.child("riego/bomba")
    private val refModo = raiz.child("riego/modo")
    private val refDispositivo = raiz.child("riego/dispositivo")

    private fun ahoraSeg() = System.currentTimeMillis() / 1000

    // ---------------- Listeners ----------------
    private val lConexion = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            conectado = s.getValue(Boolean::class.java) ?: false
            actualizarBanner()
            if (conectado) sincronizarPendientes()
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val lHumedad = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            val v = (s.value as? Number)?.toDouble() ?: return
            tvHumedad.text = "$v%"
            local.guardarEstado("humedad", v.toString())
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val lBomba = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            bombaActual = s.value as? Boolean ?: false
            tvBomba.text = if (bombaActual) "Bomba: ON" else "Bomba: OFF"
            local.guardarEstado("bomba", bombaActual.toString())
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val lModo = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            val m = s.value?.toString() ?: "--"
            tvModo.text = "Modo: $m"
            local.guardarEstado("modo", m)
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    private val lDispositivo = object : ValueEventListener {
        override fun onDataChange(s: DataSnapshot) {
            tvDispositivo.text = "Dispositivo: ${s.value ?: "sin datos"}"
        }
        override fun onCancelled(e: DatabaseError) {}
    }

    // ---------------- Ciclo de vida ----------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel)
        local = DbLocal(this)

        tvConexion = findViewById(R.id.tvConexion)
        tvPendientes = findViewById(R.id.tvPendientes)
        tvDispositivo = findViewById(R.id.tvDispositivo)
        tvHumedad = findViewById(R.id.tvHumedad)
        tvBomba = findViewById(R.id.tvBomba)
        tvModo = findViewById(R.id.tvModo)
        tvRol = findViewById(R.id.tvRol)
        btnBomba = findViewById(R.id.btnBomba)
        btnAuto = findViewById(R.id.btnAuto)
        val btnHistorial = findViewById<Button>(R.id.btnHistorial)
        val btnSalir = findViewById<Button>(R.id.btnSalir)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Ultimos valores guardados en el telefono (se ven mientras llega el dato en vivo)
        local.leerEstado("humedad")?.let { tvHumedad.text = "$it% (guardado)" }
        local.leerEstado("bomba")?.let {
            bombaActual = it.toBoolean()
            tvBomba.text = if (bombaActual) "Bomba: ON (guardado)" else "Bomba: OFF (guardado)"
        }
        local.leerEstado("modo")?.let { tvModo.text = "Modo: $it (guardado)" }
        actualizarBanner()

        // Rol: primero el guardado, despues el de la nube
        local.leerEstado("rol_${user.uid}")?.let { aplicarRol(it, user.email) }
        raiz.child("users/${user.uid}/role").get().addOnSuccessListener { s ->
            val rol = s.value?.toString() ?: "sin rol"
            local.guardarEstado("rol_${user.uid}", rol)
            aplicarRol(rol, user.email)
        }

        btnBomba.setOnClickListener {
            val nuevo = !bombaActual
            bombaActual = nuevo
            enviarOrden("modo", "manual")
            enviarOrden("bomba", nuevo.toString())
        }
        btnAuto.setOnClickListener { enviarOrden("modo", "auto") }
        btnHistorial.setOnClickListener { startActivity(Intent(this, HistorialActivity::class.java)) }
        btnSalir.setOnClickListener {
            local.limpiarTodo()                       // no quedan datos del usuario en el telefono
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // Solo se escucha la nube mientras la pantalla esta visible (ahorra bateria y datos)
    override fun onStart() {
        super.onStart()
        if (escuchando || FirebaseAuth.getInstance().currentUser == null) return
        refConexion.addValueEventListener(lConexion)
        refHumedad.addValueEventListener(lHumedad)
        refBomba.addValueEventListener(lBomba)
        refModo.addValueEventListener(lModo)
        refDispositivo.addValueEventListener(lDispositivo)
        escuchando = true
    }

    override fun onStop() {
        super.onStop()
        if (!escuchando) return
        refConexion.removeEventListener(lConexion)
        refHumedad.removeEventListener(lHumedad)
        refBomba.removeEventListener(lBomba)
        refModo.removeEventListener(lModo)
        refDispositivo.removeEventListener(lDispositivo)
        escuchando = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::local.isInitialized) local.close()
    }

    // ---------------- Logica ----------------
    private fun aplicarRol(rol: String, email: String?) {
        tvRol.text = "$email (rol: $rol)"
        esAdmin = rol == "admin"
        val vis = if (esAdmin) View.VISIBLE else View.GONE
        btnBomba.visibility = vis
        btnAuto.visibility = vis
        if (esAdmin && conectado) sincronizarPendientes()
    }

    private fun actualizarBanner() {
        if (conectado) {
            tvConexion.text = "Conectado"
            tvConexion.setBackgroundColor(Color.parseColor("#2E7D32"))
        } else {
            tvConexion.text = "Sin conexión · se muestran los últimos datos guardados"
            tvConexion.setBackgroundColor(Color.parseColor("#C62828"))
        }
        tvConexion.setTextColor(Color.WHITE)
        val n = local.pendientes().size
        tvPendientes.visibility = if (n > 0) View.VISIBLE else View.GONE
        tvPendientes.text = "Órdenes pendientes de envío: $n"
    }

    private fun esPermiso(e: Exception) = e.message?.contains("ermission", ignoreCase = true) == true

    private fun escribir(tipo: String, valor: String, fin: (Exception?) -> Unit) {
        val dato: Any = if (tipo == "bomba") valor.toBoolean() else valor
        raiz.child("riego/$tipo").setValue(dato)
            .addOnSuccessListener { fin(null) }
            .addOnFailureListener { fin(it) }
    }

    private fun guardarOrden(tipo: String, valor: String) {
        local.encolar(tipo, valor, ahoraSeg())
        actualizarBanner()
        Toast.makeText(this, "Sin conexión: orden guardada, se enviará al reconectar", Toast.LENGTH_LONG).show()
    }

    private fun enviarOrden(tipo: String, valor: String) {
        if (!conectado) { guardarOrden(tipo, valor); return }
        escribir(tipo, valor) { error ->
            if (error != null) {
                if (esPermiso(error)) Toast.makeText(this, "Sin permiso", Toast.LENGTH_SHORT).show()
                else guardarOrden(tipo, valor)
            }
        }
    }

    // Al volver la conexion se envian las ordenes guardadas (maximo 10 min de antiguedad)
    private fun sincronizarPendientes() {
        if (sincronizando || !esAdmin || !conectado) return
        val lista = local.pendientes()
        if (lista.isEmpty()) return
        sincronizando = true
        var restantes = lista.size
        val ahora = ahoraSeg()
        for (p in lista) {
            if (ahora - p.creado > 600) {
                local.borrarPendiente(p.id)
                Toast.makeText(this, "Una orden guardada venció (más de 10 min) y se descartó", Toast.LENGTH_LONG).show()
                restantes--
                continue
            }
            escribir(p.tipo, p.valor) { error ->
                if (error == null || esPermiso(error)) local.borrarPendiente(p.id)
                restantes--
                if (restantes == 0) {
                    sincronizando = false
                    actualizarBanner()
                    if (local.pendientes().isEmpty())
                        Toast.makeText(this, "Órdenes pendientes enviadas", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (restantes == 0) { sincronizando = false; actualizarBanner() }
    }
}
