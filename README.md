# Transportes Ferreira GPS — v0.8

## Qué cambia en v0.8
- Ningún campo del formulario bloquea el inicio del viaje: camión, destino, cliente, carga, kilos y remitos son opcionales. Sin permiso GPS se guarda el viaje y se puede activar la ubicación después.
- Remitos de salida y llegada: número, foto de cámara/galería y datos opcionales. Se pueden agregar varios antes, durante o después del viaje.
- Historial propio por chofer, aunque cambie de camión; mapa del recorrido registrado, origen y llegada GPS. Destino previsto opcional con búsqueda.
- Combustible dentro o fuera del viaje: fecha y estación, litros y total opcionales, foto opcional. La estación es obligatoria al guardar combustible, nunca para iniciar un viaje.
- Catálogos de clientes, cargas y estaciones desde el panel. Los cambios completados en la web se consultan al actualizar el historial.

## Funciones conservadas
- Login con correo y contraseña. Sesión cifrada con Android Keystore; no guarda la contraseña.
- Inicio con nombre del chofer, selección del camión y viaje con carga o sin carga / retorno vacío.
- Pantalla del viaje con kilómetros GPS, pausar/reanudar, finalizar y adjuntar boletas.
- Cámara en resolución completa o foto de la galería, vista previa y guardado por viaje.
- Envío al panel: Combustible → boleta de App GPS → Editar carga. Ahí se completan litros, importe, estación y demás datos.
- Los registros se guardan primero en el teléfono; los envíos pendientes se reintentan con conexión. No borres los datos de la app ni la desinstales con registros pendientes.
- Pausar suspende el GPS y los kilómetros. Reanudar no suma el desplazamiento de la pausa. El viaje se conserva tras cerrar/reabrir la app. Android puede interrumpir servicios por batería o cierre forzado: reabrí la app para recuperar el viaje.
- Los kilómetros son una estimación GPS, no una lectura del odómetro del camión. Se filtran posiciones imprecisas, antiguas y saltos imposibles. Los tramos sin lecturas no se inventan.

## Instalar / actualizar
Abrí esta carpeta en Android Studio, sincronizá Gradle y compilá con Java 17 y Android SDK 35. El proyecto incluye Gradle Wrapper 8.9. En Windows: `gradlew.bat assembleDebug`; en Linux/macOS: `./gradlew assembleDebug`.

Para actualizar la app que ya está instalada, compilá con la MISMA firma/keystore que utilizaste para instalarla. El APK de prueba adjunto usa una firma de desarrollo de este entorno; Android puede rechazarlo como actualización si la firma anterior es distinta. No desinstales la app anterior para resolver esto si tiene boletas pendientes: usá la firma anterior.

`APK/TransportesFerreiraGPS-v0.8-prueba.apk` es el APK compilado de prueba, Android 8 o posterior. Para distribución definitiva usá tu propia firma de publicación. No se incluye una clave privada de firma en este proyecto.

## Panel
https://transportes-ferreira-gestion.carlosdiegofc2001.chatgpt.site/
La base y el panel se actualizaron para recibir boletas y múltiples remitos por viaje. Las fotos son privadas; se abren con acceso autenticado. Cada boleta conserva un identificador estable del viaje, incluso antes de que este finalice.

Al finalizar el viaje, aparece en Viajes para completar los datos pendientes. La distancia y la condición sin carga llegan desde la app. Las boletas aparecen en Combustible sin importes inventados ni OCR automático.

Las boletas locales de versiones anteriores a v0.8 no tenían identificador del viaje y no se vinculan automáticamente. Los archivos antiguos no se eliminan durante la actualización.

## Verificación realizada
- Compilación Android y APK: correcta.
- Pruebas unitarias: desplazamiento, pausas, ruido GPS, posiciones antiguas y saltos imposibles.
- Panel: compilación y pruebas de importación, asociación de boletas, distancia y estados.
- Base: prueba transaccional de inserción/lectura del chofer, lectura/archivo del administrador y aislamiento entre usuarios; sin dejar registros de prueba.

Pendiente: prueba en un teléfono físico con un viaje real, permisos, cámara/galería y pérdida/recuperación de conexión. No se afirma haber realizado esa prueba.
