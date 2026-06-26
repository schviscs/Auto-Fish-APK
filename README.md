# Auto Fish NTE - Bot de Pesca Avanzado para Android

<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

Este proyecto ha sido transformado en un **bot de pesca avanzado para Android**, diseñado para automatizar la detección de picadas y la interacción en juegos de pesca, utilizando una combinación de Kotlin para la interfaz de usuario y la interacción con el sistema Android, y Python para la lógica de visión por computadora.

## Características Principales

*   **Motor de Bot Separado**: La lógica central del bot (`FishBotEngine`) ahora opera de forma independiente de la UI, permitiendo una mayor robustez y capacidad de respuesta.
*   **Visión por Computadora con Python (Chaquopy)**: Integración de un módulo Python (`vision_engine.py`) para el análisis de frames de pantalla en tiempo real, detectando señales de picada mediante heurísticas de color y patrones. Esto se logra gracias a [Chaquopy](https://chaquo.com/chaquopy/), que permite ejecutar código Python directamente en Android.
*   **Captura de Pantalla en Tiempo Real (MediaProjection)**: Utiliza la API `MediaProjection` de Android para capturar frames de la pantalla del dispositivo, alimentando el motor de visión Python con datos visuales en vivo.
*   **Servicio de Accesibilidad Mejorado**: El `BotAccessibilityService` ha sido refactorizado para ejecutar gestos (taps, swipes) de manera más controlada y con un sistema de cola, asegurando interacciones precisas con la aplicación de destino.
*   **Persistencia de Configuración (DataStore)**: La configuración del bot (umbrales de detección, coordenadas de toque, etc.) se guarda y carga automáticamente utilizando `Jetpack DataStore`, asegurando que tus ajustes persistan entre sesiones.
*   **Interfaz de Usuario con Jetpack Compose**: Una UI moderna y reactiva construida con Jetpack Compose que muestra el estado del bot en tiempo real, logs de eventos y permite ajustar la configuración fácilmente.
*   **Logs Detallados**: Registro de eventos y acciones del bot para depuración y monitoreo.

## Arquitectura

La arquitectura del proyecto se basa en la separación de responsabilidades:

*   **`MainActivity`**: Punto de entrada de la aplicación, gestiona los permisos de `MediaProjection` y la inicialización del `FishBotViewModel`.
*   **`FishBotViewModel`**: Conecta la UI con el motor del bot y la persistencia de configuración. Gestiona el ciclo de vida de la fuente de frames y el motor.
*   **`FishBotEngine`**: El corazón del bot. Contiene el bucle principal que captura frames, los envía al `VisionAnalyzer`, procesa las decisiones y ejecuta acciones a través del `BotAccessibilityService`.
*   **`FrameSource`**: Interfaz para la captura de frames. `ScreenCaptureFrameSource` implementa la captura real usando `MediaProjection`.
*   **`VisionAnalyzer`**: Interfaz para el análisis de visión. `PythonVisionAnalyzer` utiliza Chaquopy para invocar el módulo Python `vision_engine.py`.
*   **`vision_engine.py`**: Módulo Python que recibe frames RGBA y aplica algoritmos de visión por computadora (NumPy, Pillow) para detectar la señal de picada y devolver una acción.
*   **`BotAccessibilityService`**: Servicio de Android que ejecuta gestos de toque y deslizamiento en la pantalla, interactuando con otras aplicaciones.
*   **`BotConfigStore`**: Utiliza `Jetpack DataStore` para almacenar de forma persistente la configuración del bot.

## Cómo Configurar y Ejecutar

**Prerrequisitos:**

*   [Android Studio](https://developer.android.com/studio) (versión compatible con Kotlin 1.9.0+ y Gradle 8.0+)
*   Un dispositivo Android físico o emulador con Android 5.0 (API 21) o superior.

**Pasos:**

1.  **Clonar el Repositorio:**
    ```bash
    git clone https://github.com/schviscs/Auto-Fish-APK.git
    cd Auto-Fish-APK
    ```

2.  **Abrir en Android Studio:**
    *   Abre Android Studio y selecciona `Open`.
    *   Navega a la carpeta `Auto-Fish-APK` que acabas de clonar y ábrela.
    *   Permite que Android Studio sincronice el proyecto y descargue las dependencias de Gradle.

3.  **Configurar Chaquopy (si es necesario):**
    *   Asegúrate de que los archivos `build.gradle.kts` (del proyecto y del módulo `app`) y `settings.gradle.kts` contengan las configuraciones para Chaquopy como se muestra en la documentación oficial de [Chaquopy](https://chaquo.com/chaquopy/doc/current/android.html).
    *   Verifica que el repositorio Maven de Chaquopy esté declarado en `settings.gradle.kts`:
        ```kotlin
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
                maven { url "https://chaquo.com/maven" }
            }
        }
        ```

4.  **Permisos en Android:**
    *   **Permiso de Ventana Flotante (Overlay)**: Necesario para que la aplicación pueda mostrar controles sobre otras apps. Se solicitará al iniciar la app.
    *   **Permiso de Servicio de Accesibilidad**: Esencial para que el bot pueda realizar taps y swipes. Debes activarlo manualmente en la configuración de accesibilidad de Android.
    *   **Permiso de Captura de Pantalla (MediaProjection)**: Se solicitará al intentar iniciar el bot. Es crucial para que el motor de visión pueda ver la pantalla.

5.  **Ejecutar la Aplicación:**
    *   Conecta tu dispositivo Android o inicia un emulador.
    *   Haz clic en el botón `Run` (triángulo verde) en Android Studio para instalar y ejecutar la aplicación.

## Desarrollo y Contribución

*   **Módulo Python**: El código Python para la visión se encuentra en `app/src/main/python/vision_engine.py`. Puedes modificarlo y añadir nuevas heurísticas o modelos de ML.
*   **Kotlin**: La lógica de Android está en `app/src/main/java/com/example/`.
*   **Pruebas**: Se han añadido pruebas unitarias para el módulo Python (`tests/test_vision_engine.py`) y se han actualizado pruebas de UI para reflejar la nueva estructura.

¡Esperamos que este proyecto sirva como una base sólida para futuras mejoras y experimentación en la automatización de juegos en Android con Python!
