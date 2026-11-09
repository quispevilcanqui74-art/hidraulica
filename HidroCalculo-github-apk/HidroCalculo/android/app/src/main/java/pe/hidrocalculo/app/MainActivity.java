package pe.hidrocalculo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * HidroCalculo: envuelve la app web (assets/index.html) en un WebView.
 * - Funciona sin internet y sin permiso INTERNET.
 * - Sirve la página desde https://appassets.androidplatform.net/ para que localStorage persista.
 * - Puente JS "HidroCalcAndroid.imprimir()" para el botón PDF.
 * - Soporta selector de archivos (imagen de fondo del plano), confirm/alert/prompt y descargas data: (PNG).
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";
    private static final int REQ_ARCHIVO = 1;

    private WebView web;
    private ValueCallback<Uri[]> selectorArchivos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);       // localStorage (historial, plano, tema)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u)); // enlaces externos: navegador
                } catch (Exception ignorada) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (selectorArchivos != null) selectorArchivos.onReceiveValue(null);
                selectorArchivos = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_ARCHIVO);
                } catch (Exception e) {
                    selectorArchivos = null;
                    return false;
                }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView v, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, i) -> result.confirm())
                        .setOnCancelListener(d -> result.confirm())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, i) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, i) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView v, String url, String message, String defaultValue,
                                      final JsPromptResult result) {
                final EditText campo = new EditText(MainActivity.this);
                campo.setText(defaultValue);
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(campo)
                        .setPositiveButton(android.R.string.ok, (d, i) -> result.confirm(campo.getText().toString()))
                        .setNegativeButton(android.R.string.cancel, (d, i) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        // Descargas: el botón PNG del plano genera un data: URL.
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url != null && url.startsWith("data:")) {
                guardarDataUrl(url, contentDisposition, mimeType);
            } else {
                Toast.makeText(this, "Descarga no disponible sin conexión.", Toast.LENGTH_SHORT).show();
            }
        });

        web.addJavascriptInterface(new Puente(), "HidroCalcAndroid");

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(START_URL);
        }
    }

    /** Puente para JavaScript: window.HidroCalcAndroid.imprimir() */
    private class Puente {
        @JavascriptInterface
        public void imprimir() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm == null) return;
                PrintDocumentAdapter adaptador = web.createPrintDocumentAdapter("HidroCalculo");
                pm.print("HidroCalculo", adaptador, new PrintAttributes.Builder().build());
            });
        }
    }

    private void guardarDataUrl(String url, String contentDisposition, String mimeType) {
        try {
            int coma = url.indexOf(',');
            String meta = url.substring(5, coma);
            String cuerpo = url.substring(coma + 1);
            byte[] datos = meta.contains(";base64")
                    ? Base64.decode(cuerpo, Base64.DEFAULT)
                    : URLDecoder.decode(cuerpo, "UTF-8").getBytes(StandardCharsets.UTF_8);

            String mime = (mimeType != null && !mimeType.isEmpty()) ? mimeType : meta.split(";")[0];
            String nombre = URLUtil.guessFileName(url, contentDisposition, mime);
            if (nombre == null || nombre.startsWith("downloadfile")) {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                nombre = "HidroCalculo-" + System.currentTimeMillis() + "." + (ext != null ? ext : "bin");
            }

            String destino;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IllegalStateException("No se pudo crear el archivo");
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(datos);
                }
                destino = "Descargas";
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File f = new File(dir, nombre);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(datos);
                }
                destino = f.getParent();
            }
            Toast.makeText(this, "Guardado en " + destino + ": " + nombre, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el archivo.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_ARCHIVO) {
            if (selectorArchivos != null) {
                selectorArchivos.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                selectorArchivos = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
