package it.dlsas.cassaforte

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bouncycastle.crypto.generators.SCrypt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Un accesso della Cassaforte (stessi campi del Gestionale DL). */
data class Voce(
    val id: Long, val titolo: String, val sito: String, val utente: String,
    val password: String, val note: String, val cartella: String, val preferito: Boolean
) {
    val nome get() = titolo.ifBlank { Vault.host(sito) }
}

/**
 * Archivio esportato dal server (cassaforte-dl.cdlm su Google Drive):
 * { formato:"cassaforte-dl-mobile", utenti:[{nome,salt,chk}], voci:[{id,utente,dati}] }
 * Ogni voce è cifrata come nel Gestionale: AES-256-GCM con chiave scrypt(N=2^17,r=8,p=1) della password principale.
 */
object Vault {
    private const val FILE = "cassaforte.cdlm"
    private const val PREF = "cassaforte"
    private const val ALIAS = "cdl_impronta"
    private const val DURATA = 5 * 60 * 1000L

    // sessione sbloccata (solo in memoria)
    private var chiave: ByteArray? = null
    var utente: String? = null; private set
    var voci: List<Voce> = emptyList(); private set
    private var scade = 0L

    fun sbloccata(): Boolean {
        if (chiave != null && System.currentTimeMillis() > scade) blocca()
        if (chiave != null) scade = System.currentTimeMillis() + DURATA
        return chiave != null
    }
    fun blocca() { chiave?.fill(0); chiave = null; voci = emptyList(); utente = null }

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun file(c: Context) = File(c.filesDir, FILE)
    fun haArchivio(c: Context) = file(c).exists()
    fun uriDrive(c: Context): Uri? = prefs(c).getString("uri", null)?.let(Uri::parse)
    fun aggiornatoIl(c: Context): String = prefs(c).getString("quando", "") ?: ""

    private fun archivio(c: Context) = JSONObject(file(c).readText())
    fun utenti(c: Context): List<String> = if (!haArchivio(c)) emptyList() else
        archivio(c).getJSONArray("utenti").let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("nome") } }

    /** Copia l'archivio scelto da Google Drive dentro il telefono (resta cifrato). */
    fun importa(c: Context, uri: Uri) {
        val testo = c.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val j = JSONObject(testo)
        require(j.optString("formato") == "cassaforte-dl-mobile") { "Il file scelto non è l'archivio della Cassaforte DL" }
        file(c).writeText(testo)
        prefs(c).edit().putString("uri", uri.toString())
            .putString("quando", j.optString("creato", "")).apply()
        if (chiave != null && utente != null) voci = leggiVoci(c, utente!!, chiave!!)
    }

    // ---------- cifratura compatibile con vault-core.js ----------
    private fun b64(s: String) = Base64.decode(s, Base64.DEFAULT)
    private fun apriGcm(key: ByteArray, b: String): ByteArray {
        val buf = b64(b)
        val iv = buf.copyOfRange(0, 12); val tag = buf.copyOfRange(12, 28); val dati = buf.copyOfRange(28, buf.size)
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return ci.doFinal(dati + tag)
    }
    private fun derive(master: String, salt: ByteArray): ByteArray =
        SCrypt.generate(Normalizer.normalize(master, Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8), salt, 1 shl 17, 8, 1, 32)

    private fun utenteJson(c: Context, nome: String): JSONObject {
        val a = archivio(c).getJSONArray("utenti")
        for (i in 0 until a.length()) a.getJSONObject(i).let { if (it.getString("nome").equals(nome, true)) return it }
        throw IllegalArgumentException("Utente non trovato")
    }
    private fun verifica(c: Context, nome: String, key: ByteArray) {
        try { apriGcm(key, utenteJson(c, nome).getString("chk")) } catch (e: Exception) { throw IllegalArgumentException("Password principale errata") }
    }
    private fun leggiVoci(c: Context, nome: String, key: ByteArray): List<Voce> {
        val a = archivio(c).getJSONArray("voci"); val out = ArrayList<Voce>()
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i); if (!r.getString("utente").equals(nome, true)) continue
            val v = try { JSONObject(String(apriGcm(key, r.getString("dati")), Charsets.UTF_8)) } catch (e: Exception) { continue }
            out += Voce(r.getLong("id"), v.optString("titolo"), v.optString("sito"), v.optString("utente"),
                v.optString("password"), v.optString("note"), v.optString("cartella"), v.optBoolean("preferito"))
        }
        return out.sortedWith(compareByDescending<Voce> { it.preferito }.thenBy { it.nome.lowercase() })
    }
    private fun apri(c: Context, nome: String, key: ByteArray) {
        verifica(c, nome, key)
        blocca(); chiave = key; utente = nome; voci = leggiVoci(c, nome, key)
        scade = System.currentTimeMillis() + DURATA
        prefs(c).edit().putString("ultimoUtente", nome).apply()
    }
    fun ultimoUtente(c: Context) = prefs(c).getString("ultimoUtente", null)

    /** Sblocco con password principale (lento di proposito: qualche secondo). */
    fun sbloccaConPassword(c: Context, nome: String, master: String) {
        val u = utenteJson(c, nome)
        apri(c, u.getString("nome"), derive(master, b64(u.getString("salt"))))
    }

    // ---------- impronta: la chiave è conservata cifrata con una chiave del Keystore che richiede l'impronta ----------
    fun improntaAttiva(c: Context) = prefs(c).getString("imp_dati", null) != null
    fun improntaUtente(c: Context) = prefs(c).getString("imp_utente", null)
    private fun chiaveKeystore(crea: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!crea) return null
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true).build())
        return g.generateKey()
    }
    /** Cipher da far sbloccare con l'impronta per ATTIVARE (cifrare la chiave della Cassaforte). */
    fun cipherPerAttivare(): Cipher {
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) } catch (_: Exception) {}
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, chiaveKeystore(true)) }
    }
    fun salvaImpronta(c: Context, ci: Cipher) {
        val k = chiave ?: throw IllegalStateException("Cassaforte bloccata")
        val dati = ci.doFinal(k)
        prefs(c).edit().putString("imp_dati", Base64.encodeToString(dati, Base64.NO_WRAP))
            .putString("imp_iv", Base64.encodeToString(ci.iv, Base64.NO_WRAP)).putString("imp_utente", utente).apply()
    }
    /** Cipher da far sbloccare con l'impronta per APRIRE. Null se l'impronta va riattivata. */
    fun cipherPerAprire(c: Context): Cipher? = try {
        val iv = b64(prefs(c).getString("imp_iv", null)!!)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, chiaveKeystore(false), GCMParameterSpec(128, iv)) }
    } catch (e: Exception) { togliImpronta(c); null }
    fun sbloccaConImpronta(c: Context, ci: Cipher) {
        val key = ci.doFinal(b64(prefs(c).getString("imp_dati", null)!!))
        try { apri(c, improntaUtente(c)!!, key) }
        catch (e: IllegalArgumentException) { togliImpronta(c); throw IllegalArgumentException("La password principale è cambiata: entra con la password e riattiva l'impronta") }
    }
    fun togliImpronta(c: Context) { prefs(c).edit().remove("imp_dati").remove("imp_iv").remove("imp_utente").apply() }

    // ---------- abbinamento app/sito -> accesso ----------
    fun host(u: String): String = try {
        Uri.parse(if (u.contains("://")) u else "https://$u").host.orEmpty().removePrefix("www.").lowercase()
    } catch (e: Exception) { "" }
    private fun radice(h: String) = h.split('.').let { if (it.size >= 2) it[it.size - 2] else h }

    fun ricordato(c: Context, chiaveApp: String): Long = prefs(c).getLong("abb:$chiaveApp", -1)
    fun ricorda(c: Context, chiaveApp: String, id: Long) { prefs(c).edit().putLong("abb:$chiaveApp", id).apply() }

    /** Voci adatte a un sito (dominio web) o a un'app (nome pacchetto). */
    fun adatte(c: Context, dominio: String?, pacchetto: String?): List<Voce> {
        val chiaveApp = dominio?.takeIf { it.isNotBlank() } ?: pacchetto ?: ""
        val rid = ricordato(c, chiaveApp)
        val d = dominio?.removePrefix("www.")?.lowercase()
        return voci.filter { v ->
            val h = host(v.sito); if (v.id == rid) return@filter true
            if (h.isBlank()) return@filter false
            if (!d.isNullOrBlank()) d == h || d.endsWith(".$h") || h.endsWith(".$d")
            else pacchetto != null && radice(h).length >= 3 && pacchetto.lowercase().contains(radice(h).replace("-", ""))
        }.sortedByDescending { it.id == rid }
    }
}
