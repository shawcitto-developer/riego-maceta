import time, random
import firebase_admin
from firebase_admin import credentials, db

cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred, {
    "databaseURL": "https://riego-maceta-default-rtdb.firebaseio.com"
})
riego = db.reference("riego")
historial = db.reference("historial")

humedad = 50.0
while True:
    d = riego.get() or {}
    modo = d.get("modo", "auto")
    umbral = d.get("umbral", 30)

    if modo == "manual":
        bomba = bool(d.get("bomba", False))      # manda la app
    else:
        bomba = humedad < umbral                 # lógica local de respaldo
        riego.child("bomba").set(bomba)

    # simulación: con la bomba sube, sin bomba el suelo se seca
    humedad += 3 if bomba else -1
    humedad = max(0, min(100, humedad + random.uniform(-0.5, 0.5)))

    riego.child("humedad").set(round(humedad, 1))
    historial.push({"valor": round(humedad, 1), "ts": int(time.time())})
    print(f"Humedad {humedad:.1f}% | bomba {'ON' if bomba else 'OFF'} | modo {modo}")
    time.sleep(5)

