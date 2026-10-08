# Riego Maceta: App Android para sistema de riego IoT

App Android (Kotlin) que monitorea la humedad de una maceta y controla la bomba de riego.
Proyecto de la Sumativa Unidad 2, TI3042 Aplicaciones Móviles para IoT.

## Arquitectura

```
App Android (admin) ──┐
                      ├── Firebase (Auth + Realtime Database) ── sensor.py (sensor y bomba simulados)
App Android (visor) ──┘
```

- **App Android (Kotlin):** login, panel con humedad en vivo, estado de la bomba, botón de control (solo admin) e historial.
- **Firebase Auth:** login con correo y contraseña.
- **Firebase Realtime Database:** punto central. La app y el dispositivo leen y escriben ahí, por eso los dos celulares ven los mismos datos al instante.
- **sensor.py:** simula el sensor de humedad y la bomba. En una Raspberry física se reemplazaría la simulación por GPIO y un conversor ADC.
- **Comunicación:** WiFi/Internet, elegida por alcance, estabilidad y porque permite usar la nube.

## Roles

| Rol | Permisos |
|---|---|
| admin | Ve datos, enciende/apaga la bomba, cambia modo y umbral |
| visor | Solo ve los datos |

## Modo automático y manual

El campo `riego/modo` define quién manda. En `auto`, `sensor.py` enciende la bomba cuando la humedad baja del umbral (`riego/umbral`, por defecto 30%). En `manual`, manda lo que la app escriba en `riego/bomba`.

## Seguridad (ISO 27400)

| Medida | Cómo se cumple |
|---|---|
| Credenciales cifradas | Firebase Auth gestiona las contraseñas, la app no las guarda |
| Control de acceso / permisos | Roles admin/visor en la app y reglas de la base de datos que solo permiten escribir `bomba`, `modo` y `umbral` al admin |
| HTTPS/TLS | Firebase usa TLS en todas las conexiones, tanto en la app como en `sensor.py` |
| Secretos fuera del repositorio | `.gitignore` excluye `google-services.json` y `serviceAccountKey.json` |
| Mínimo privilegio | Cada usuario solo lee su propio rol; el visor no puede escribir |

## Cómo ejecutarlo

Archivos que **no están en el repo** y hay que reemplazar con los propios:

1. `app/google-services.json`: se descarga en Firebase al registrar la app Android (package `com.riego.maceta`).
2. `sensor/serviceAccountKey.json`: Firebase → Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada.
3. En `sensor/sensor.py`, cambiar `databaseURL` por la de tu Realtime Database.

Pasos:

1. En Firebase, crear un proyecto con Authentication (correo/contraseña) y Realtime Database. Cargar los datos iniciales (`riego` y `users` con los UID) y pegar las reglas de seguridad.
2. Dispositivo simulado: `cd sensor`, `pip install firebase-admin` y `python sensor.py`.
3. App: abrir en Android Studio, sincronizar Gradle y ejecutar en 2 dispositivos, uno como admin y otro como visor.

## Pruebas realizadas

- Login correcto e incorrecto.
- Humedad actualizada en vivo en los dos dispositivos.
- El admin enciende la bomba y el cambio se ve en el otro dispositivo y en `sensor.py`.
- El visor no ve los controles, y aunque intentara escribir, las reglas de la base de datos lo bloquean.
- Historial consultable desde la app.