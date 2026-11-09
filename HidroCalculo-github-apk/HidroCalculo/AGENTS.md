# AGENTS.md — HidroCalculo

Calculadora de hidráulica de **un solo archivo** (`index.html`): HTML + CSS + JS vanilla, unidades SI, 100 % offline.
Idioma de la interfaz y de los comentarios: **español (Perú)**. Responde y documenta en español.

## Reglas inamovibles
- **Un solo archivo.** No agregar frameworks, bundlers, CDNs ni `fetch` a la red. Debe abrir con doble clic y funcionar sin internet.
- Los íconos son `data:` URIs base64 dentro del `<head>` y del `<header>`. **No los toques ni los imprimas** (son líneas enormes); al editar usa búsquedas/reemplazos puntuales, nunca reescribas el archivo completo.
- Colores solo con variables CSS (`--bg --card --tx --mu --pr --ok --wn --bd`); soporta tema claro/oscuro/auto. En canvas, lee las variables con `getComputedStyle`.
- Texto tipográfico en español: números con `fmt()` (usa `toLocaleString('es-PE')`).
- Resultados **referenciales**: se mantiene el aviso al pie. No quitarlo.
- Existe un puente opcional para Android: `window.HidroCalcAndroid.imprimir()` (botón PDF). Mantener el fallback `window.print()`.
- Persistencia solo con `localStorage` dentro de `try/catch` (claves `hc_u`, `hc_h`, `hc_t`, `hc_pl`).

## Estructura del `<script>` (en orden)
1. Constantes y utilidades: `G`, `fmt`, `ar` (área circular), `F/DS/DN` (unidades de diámetro), `PU` (presiones), `KS` (coeficientes K), `geo`, `bis` (bisección).
2. `mods`: un objeto por módulo. Cada uno tiene
   - `t` título, `h` fórmula,
   - `f`: campos `{i:id, l:etiqueta, d:valor por defecto, u:1 si es diámetro (se guarda siempre en mm), o:[opciones] si es select, s:fn(v) para mostrarlo condicionalmente}`,
   - `calc(v)` → `{k:[[etiqueta,valor],…], w:advertencia, n:{…numéricos opcionales}}` (o `bad` si la entrada es inválida),
   - `g(v,r)` (opcional): devuelve SVG del esquema.
   Módulos: `caudal fric pres dos tank canal bomba loc ari circ`.
3. `ini`, `sBtn`, `SND`: botones **➡️ enviar a otra pestaña** (`data-a="send"`), que rellenan `vals[destino]`.
4. Demanda de agua (`DT`, `DEM`, `dem()`, `demCalc()`, `impD()` tabla RNE IS.010).
5. Plano/mapa (`PL`, `plano()`, `pdraw()`, `plTap()`, `plAct()`, `pinit()`): lienzo `<canvas>` en **metros reales** (coordenadas mundo = m; pantalla = mundo·`PL.v.s`+offset). Herramientas: tubería, nodo, tanque, bomba, válvula, área, texto, medir, borrar, calibrar fondo.
6. UI: `render()`, `calc()`, pestañas, historial (`LS`), tema, **handlers globales de `click` e `input`** (los `data-a="p_*"` van a `plAct`, los `data-pl` a `plIn`, los `data-dm` a `demIn`).

## Cómo agregar un módulo
1. Añade una entrada en `mods` con `f`, `calc` y (opcional) `g`. `render()` y las pestañas se generan solas.
2. Valida entradas (`if(!(x>0)) return bad`). Los resultados van como `[etiqueta, fmt(valor)]`.
3. Muestra **áreas en m²** y **caudales en L/s** (convención del proyecto; m³/s solo como valor adicional).
4. Si su resultado alimenta a otro módulo, devuelve `n:{…}` y registra un botón en `SND`.
5. Agrega una prueba en `tests/smoke.test.js` con un valor conocido.

## Publicación (PWA + GitHub)
- `index.html` sigue siendo autosuficiente; `manifest.webmanifest`, `sw.js` e `icons/` solo añaden instalación y cache offline. El registro del service worker solo corre bajo `http(s)`, así que abrir el archivo con doble clic sigue funcionando.
- Al cambiar la app para publicar, sube `VERSION` en `sw.js`.
- Flujos en `.github/workflows/`: `ci.yml` (pruebas), `pages.yml` (GitHub Pages), `release.yml` (al etiquetar `vX.Y.Z` adjunta `HidroCalculo.html` y un zip web).

## Android (APK)
- `android/` es un proyecto Gradle (AGP 8.5.2, JDK 17, minSdk 24, Java). `MainActivity.java` carga `assets/index.html` con `WebViewAssetLoader` (origen `https://appassets.androidplatform.net`).
- `index.html` **no se duplica**: `app/build.gradle` (tarea `copyWebAssets`) lo copia a los assets al compilar. Edita siempre la raíz.
- Puente JS `HidroCalcAndroid.imprimir()` → `PrintManager`. Descargas `data:` (PNG) → `MediaStore.Downloads`. `confirm/alert/prompt` y el selector de archivos están implementados en `WebChromeClient`.
- No agregar permiso `INTERNET` ni librerías extra sin motivo: la app es 100 % offline.
- Compilar: `gradle -p android assembleDebug` (necesita Android SDK 34). CI: `.github/workflows/android.yml`; versiones: `release.yml` (tag `vX.Y.Z`).
- Si cambias APIs web usadas por la app (descargas, impresión, archivos), revisa también `MainActivity.java`.

## Pruebas
```
npm test
```
`tests/smoke.test.js` carga `index.html`, simula el DOM y verifica cálculos de referencia y el módulo Plano. No requiere dependencias. **Ejecútalo antes de terminar cualquier tarea** y agrega pruebas para lo que cambies.
Para revisar la interfaz real abre `index.html` en un navegador (móvil y escritorio, tema claro y oscuro) y prueba el lienzo con toque y mouse.

## Convenciones de código
- JS compacto ya existente: respeta el estilo (funciones cortas, nombres cortos en módulos). En código **nuevo** prioriza claridad.
- Diámetros: se muestran en la unidad elegida (`du`) pero se almacenan en mm.
- No uses `localStorage` fuera de `try/catch`; no uses `alert/prompt` salvo `confirm` puntual (el WebView Android puede no soportarlos; prefiere paneles en la propia UI).

## Pendientes sugeridos (roadmap)
- Plano: conectar tuberías con nodos (topología) para recorridos y redes; mover/rotar el fondo; exportar/importar el dibujo (JSON).
- Curva del sistema para bombas (el NPSH disponible ya está en el módulo Bomba).
- Red contra incendio y dimensionamiento de cisterna a partir del volumen.
- Exportar el historial a CSV.
