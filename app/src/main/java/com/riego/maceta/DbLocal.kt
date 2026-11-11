package com.riego.maceta

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Pendiente(val id: Long, val tipo: String, val valor: String, val creado: Long)

/**
 * Almacenamiento temporal en el telefono (SQLite).
 *  - lecturas:   historial de humedad guardado hasta 7 dias (se ve aunque no haya red)
 *  - pendientes: ordenes del admin hechas sin conexion; se envian al reconectar
 *  - estado:     ultimos valores conocidos (humedad, bomba, modo, rol)
 */
class DbLocal(ctx: Context) : SQLiteOpenHelper(ctx, "riego_local.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE lecturas (ts INTEGER PRIMARY KEY, valor REAL NOT NULL)")
        db.execSQL("CREATE TABLE pendientes (id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, valor TEXT NOT NULL, creado INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE estado (clave TEXT PRIMARY KEY, valor TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS lecturas")
        db.execSQL("DROP TABLE IF EXISTS pendientes")
        db.execSQL("DROP TABLE IF EXISTS estado")
        onCreate(db)
    }

    // ---------- lecturas (maximo 7 dias) ----------
    fun guardarLectura(ts: Long, valor: Double) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("ts", ts); put("valor", valor) }
        db.insertWithOnConflict("lecturas", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        val limite = System.currentTimeMillis() / 1000 - 7L * 24 * 3600
        db.delete("lecturas", "ts < ?", arrayOf(limite.toString()))
    }

    fun ultimasLecturas(n: Int): List<Pair<Long, Double>> {
        val salida = mutableListOf<Pair<Long, Double>>()
        readableDatabase.rawQuery(
            "SELECT ts, valor FROM lecturas ORDER BY ts DESC LIMIT ?", arrayOf(n.toString())
        ).use { c ->
            while (c.moveToNext()) salida.add(Pair(c.getLong(0), c.getDouble(1)))
        }
        return salida
    }

    fun totalLecturas(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM lecturas", null).use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    // ---------- ordenes pendientes ----------
    fun encolar(tipo: String, valor: String, creado: Long) {
        val cv = ContentValues().apply { put("tipo", tipo); put("valor", valor); put("creado", creado) }
        writableDatabase.insert("pendientes", null, cv)
    }

    fun pendientes(): List<Pendiente> {
        val salida = mutableListOf<Pendiente>()
        readableDatabase.rawQuery(
            "SELECT id, tipo, valor, creado FROM pendientes ORDER BY id ASC", null
        ).use { c ->
            while (c.moveToNext()) salida.add(Pendiente(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        return salida
    }

    fun borrarPendiente(id: Long) {
        writableDatabase.delete("pendientes", "id = ?", arrayOf(id.toString()))
    }

    // ---------- ultimo estado conocido ----------
    fun guardarEstado(clave: String, valor: String) {
        val cv = ContentValues().apply { put("clave", clave); put("valor", valor) }
        writableDatabase.insertWithOnConflict("estado", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun leerEstado(clave: String): String? =
        readableDatabase.rawQuery("SELECT valor FROM estado WHERE clave = ?", arrayOf(clave)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    // Al cerrar sesion se borra todo lo guardado en el telefono (datos personales)
    fun limpiarTodo() {
        val db = writableDatabase
        db.delete("lecturas", null, null)
        db.delete("pendientes", null, null)
        db.delete("estado", null, null)
    }
}
