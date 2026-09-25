# Mock GPS Pro (Android)

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.5.0-brightgreen.svg)](https://github.com/FranciscoGrandon/MockGPSPro)

**Mock GPS Pro** es una herramienta avanzada y ligera de simulación y fijación de telemetría GPS para Android con una interfaz moderna y limpia (**Light UI Concept**). Diseñada especialmente para desarrolladores y control de calidad (QA), permite probar aplicaciones sensibles a la ubicación mediante la inyección precisa de coordenadas, cinemática peatonal realista, síntesis completa de constelaciones satelitales GNSS y un visor de mapas interactivo basado en OpenStreetMap.

---

## ✨ Características Principales

* 🗺️ **Visor de Mapa Interactivo en Vivo:**
  * Integración de mapas mediante **Leaflet** y teselas oficiales de **OpenStreetMap**.
  * Cumplimiento estricto de la *OSM Tile Usage Policy* (User-Agent descriptivo con contacto y atribución de derechos visible).
  * Fijación de coordenadas al pulsar cualquier punto del mapa.
  * Visualización dinámica del radio de deriva mediante un círculo translúcido.
  * Botón de recentrado automático.

* 📡 **Síntesis Realista de Telemetría GNSS (Anti-Heurística):**
  * **Fase de Calentamiento (Warm-up TTFF):** Simula el tiempo de fijación satelital inicial (*Time To First Fix*), convergiendo gradualmente la precisión desde ~45 m hasta ~2 m para replicar el comportamiento de un receptor GPS de hardware real.
  * **Movimiento Continuo (Brownian Random Walk):** Fluctuación gaussiana sutil y desplazamiento de paso peatonal (0.2 a 0.75 m/s) confinado en un radio métrico configurable, evitando coordenadas estáticas idénticas.
  * **Inercia Angular:** Giro de rumbo suave (máx. ±25°/s), eliminando saltos vectoriales imposibles.
  * **Metadatos Satelitales Completos (`Bundle extras`):** Inyección de satélites activos (14 a 22), relación señal/ruido ($C/N_0$ de 39 a 43 dB-Hz), y dilución de precisión (`HDOP`, `VDOP`, `PDOP`).
  * **Inyección Multi-Proveedor:** Emisión simultánea en `GPS_PROVIDER`, `NETWORK_PROVIDER` y `fused`.

* ⚡ **Persistencia en Segundo Plano (Deep Background):**
  * Servicio de primer plano (`Foreground Service`) desacoplado (`stopWithTask="false"`).
  * Adquisición de `PowerManager.PARTIAL_WAKE_LOCK` para impedir la congelación de ciclos por Doze mode o pantallas apagadas.
  * Acceso directo a la exención de optimización de batería (*Sin restricciones* para Xiaomi/HyperOS/Samsung).
  * Notificación permanente con acceso rápido de retorno.

* ⚙️ **Integración Directa con Ajustes de Desarrollador:**
  * Detección automática del estado de `AppOpsManager.OPSTR_MOCK_LOCATION`.
  * Botón directo para abrir la configuración del sistema y seleccionar la app en *"Elegir aplicación para simular ubicación"*.

---

## 📱 Capturas y Arquitectura

```
┌──────────────────────────────────────────┐
│          🛰️ Mock GPS Pro v1.4.0          │
│  [⚙️ Desarrollador]  [🔋 Sin Batería Opt] │
├──────────────────────────────────────────┤
│ Latitud: -36.82699   Longitud: -73.04977 │
│ Radio: 15.0 m        Calentamiento: 15 s │
├──────────────────────────────────────────┤
│    [▶ INICIAR]           [⏹ DETENER]     │
│       [📍 Concepción]    [📍 Santiago]   │
├──────────────────────────────────────────┤
│ Estado: 🟢 SIMULACIÓN ACTIVA EN FONDO    │
├──────────────────────────────────────────┤
│ 🗺️ Mapa en Vivo (OpenStreetMap Leaflet)  │
│ [ Mapa interactivo con marcador y radio ]│
└──────────────────────────────────────────┘
```

---

## 🛠️ Requisitos de Compilación

La aplicación fue desarrollada para ser completamente autónoma y compilable de forma nativa sin depender obligatoriamente de Gradle o Android Studio pesado:

* **Android SDK:** Build Tools (`aapt`, `dx`/`d8`, `apksigner`, `zipalign`).
* **Java:** OpenJDK 8 o 17 (`javac`).
* **Target SDK:** Android 14 (API 34) / Compatible desde Android 7.0 (API 24).
* **Entornos Soportados:** Linux, macOS, Windows, Termux en Android.

### Compilación desde Terminal

```bash
# 1. Generar R.java
aapt package -f -m -J build/gen -S res -M AndroidManifest.xml -I $ANDROID_HOME/platforms/android-34/android.jar

# 2. Compilar fuentes Java
javac --release 8 -classpath $ANDROID_HOME/platforms/android-34/android.jar -d build/obj \
    build/gen/com/grandon/mockgpspro/R.java \
    src/com/grandon/mockgpspro/*.java

# 3. Convertir a DEX
dx --dex --min-sdk-version=26 --output=build/apk/classes.dex build/obj

# 4. Empaquetar APK base
aapt package -f -M AndroidManifest.xml -S res -I /system/framework/framework-res.apk -F build/app.unsigned.apk
cd build/apk && zip -q -u ../app.unsigned.apk classes.dex && cd ../..

# 5. Firmar APK
apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled true \
    --out MockGPSPro-v1.5.0-signed.apk build/app.unsigned.apk
```

---

## 🌟 Novedades v1.5.0 (Light UI Concept)
- **Rediseño Visual Integral:** Interfaz moderna y limpia basada en paleta clara (`#F4F7F8`), tarjetas individuales con bordes redondeados y sombras sutiles.
- **Acciones y Conmutadores:** Botones de inicio con gradiente verde vibrante, botón de detención suave, y conmutadores rápidos para ajustes de Desarrollador y Batería.
- **Mapa Leaflet Estilizado:** Pin de ubicación azul personalizado con aro de pulso animado, círculo de radio de dispersión integrado y leyenda visual en mapa.

---

## 🚀 Instalación y Uso

1. Descarga e instala `MockGPSPro-signed.apk` en tu dispositivo Android.
2. Habilita las **Opciones de desarrollador** en Android (pulsando 7 veces en *Número de compilación* en Información del teléfono).
3. Entra a *Opciones de desarrollador* → busca **"Seleccionar app para simular ubicación"** y elige **Mock GPS Pro**.
4. Abre la app:
   * Concede los permisos de ubicación solicitados.
   * *(Opcional)* Pulsa **"Sin Batería Opt"** para fijar en *Sin restricciones* y evitar que el sistema detenga el proceso en segundo plano.
   * Toca un punto en el mapa o introduce coordenadas manualmente.
   * Pulsa **"▶ INICIAR"**.

---

## 📄 Licencia

Este proyecto está bajo la Licencia [MIT](LICENSE).
Las teselas del mapa son provistas por [OpenStreetMap](https://www.openstreetmap.org/copyright) bajo la licencia ODbL.
