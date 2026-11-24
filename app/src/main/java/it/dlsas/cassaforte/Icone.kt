package it.dlsas.cassaforte

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Loghi dei siti, come in RoboForm. Il logo si scarica UNA volta direttamente dal sito (nessun servizio esterno,
 * nessuna password coinvolta: solo l'indirizzo del sito) e poi resta salvato nel telefono.
 * Per gli accessi a un'app installata si usa l'icona dell'app.
 */
object Icone {
    private val mem = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(k: String, b: Bitmap) = b.byteCount }
    private val nessuna = HashSet<String>()                 // siti senza logo (non si riprova fino al riavvio)
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    private fun cartella(c: Context) = File(c.filesDir, "icone").apply { mkdirs() }
    private fun pacchetto(s: String) = !s.contains("://") && !s.contains("/") && Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+){2,}$").matches(s.trim().lowercase())

    /** Mette il logo nella ImageView; se non c'è chiama [manca] (si mostra l'iniziale). */
    fun carica(c: Context, sito: String, iv: ImageView, manca: () -> Unit, trovata: () -> Unit) {
        val chiave = sito.trim().lowercase().let { if (pacchetto(it)) "app:$it" else Vault.host(it) }
        iv.tag = chiave
        if (chiave.isBlank() || chiave == "app:" || chiave in nessuna) { manca(); return }
        mem.get(chiave)?.let { iv.setImageBitmap(it); trovata(); return }
        manca()
        pool.execute {
            val b = runCatching { prendi(c, chiave) }.getOrNull()
            main.post {
                if (b == null) { nessuna += chiave; return@post }
                mem.put(chiave, b)
                if (iv.tag == chiave) { iv.setImageBitmap(b); trovata() }
            }
        }
    }

    private fun prendi(c: Context, chiave: String): Bitmap? {
        val f = File(cartella(c), chiave.replace(Regex("[^a-z0-9._-]"), "_") + ".png")
        if (f.exists()) return if (f.length() == 0L) null else BitmapFactory.decodeFile(f.absolutePath)
        val b = if (chiave.startsWith("app:")) iconaApp(c, chiave.removePrefix("app:")) else scarica(chiave)
        if (b == null) { f.writeBytes(ByteArray(0)); return null }      // ricorda che non c'è (si riprova cancellando la cache)
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return b
    }

    private fun iconaApp(c: Context, p: String): Bitmap? = try {
        val d = c.packageManager.getApplicationIcon(p)
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { val cv = android.graphics.Canvas(it); d.setBounds(0, 0, 128, 128); d.draw(cv) }
    } catch (e: Exception) { null }

    private fun leggi(u: String, max: Int): ByteArray? {
        val con = URL(u).openConnection() as HttpURLConnection
        con.connectTimeout = 5000; con.readTimeout = 6000; con.instanceFollowRedirects = true
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
        return try { if (con.responseCode !in 200..299) null else con.inputStream.use { ins ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16384); var n = 0
            while (n < max) { val r = ins.read(buf); if (r < 0) break; out.write(buf, 0, r); n += r }
            out.toByteArray() } } finally { con.disconnect() }
    }
    private fun decodifica(b: ByteArray?): Bitmap? = b?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.takeIf { it.width >= 16 }

    /** Cerca il logo più grande dichiarato dal sito, poi le posizioni standard. */
    private fun scarica(host: String): Bitmap? {
        val base = "https://$host"
        val html = runCatching { leggi("$base/", 400_000)?.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
        val link = Regex("<link[^>]+>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.mapNotNull { tag ->
            val rel = Regex("rel=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
            if (!rel.contains("icon")) return@mapNotNull null
            val href = Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            if (href.endsWith(".svg", true) || href.startsWith("data:")) return@mapNotNull null
            val dim = Regex("sizes=[\"'](\\d+)", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: if (rel.contains("apple")) 180 else 32
            runCatching { URL(URL("$base/"), href).toString() }.getOrNull()?.let { it to dim }
        }.sortedByDescending { it.second }.map { it.first }.toList()
        for (u in link + listOf("$base/apple-touch-icon.png", "$base/favicon.ico"))
            runCatching { decodifica(leggi(u, 800_000)) }.getOrNull()?.let { return it }
        return null
    }

    fun svuota(c: Context) { mem.evictAll(); nessuna.clear(); cartella(c).deleteRecursively() }
}
