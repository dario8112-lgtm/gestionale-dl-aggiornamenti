package it.dlsas.cassaforte

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

const val BLU = 0xFF1E3A5F.toInt()
const val ORO = 0xFFF1C40F.toInt()

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.testo(s: String, size: Float = 16f, bold: Boolean = false, col: Int? = null) = TextView(this).apply {
    text = s; textSize = size; if (bold) setTypeface(typeface, Typeface.BOLD); col?.let { setTextColor(it) }
}
fun Context.bottone(s: String, primario: Boolean = false, onClick: () -> Unit) = Button(this).apply {
    text = s; isAllCaps = false; textSize = 16f
    if (primario) { background = GradientDrawable().apply { setColor(BLU); cornerRadius = dp(10).toFloat() }; setTextColor(Color.WHITE) }
    setOnClickListener { onClick() }
}
fun Context.colonna(pad: Int = 16) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(pad), dp(pad), dp(pad), dp(pad)) }
fun lp(h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, c: Context? = null) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h).apply { if (c != null) topMargin = c.dp(top) }
fun Context.intestazione(titolo: String) = LinearLayout(this).apply {
    setBackgroundColor(BLU); setPadding(dp(16), dp(14), dp(16), dp(14)); gravity = Gravity.CENTER_VERTICAL
    addView(testo("Cassaforte", 20f, true, ORO)); addView(testo(" $titolo", 20f, true, Color.WHITE))
}

/** Schermata di sblocco condivisa tra app e compilazione automatica. */
class Sblocco(private val a: AppCompatActivity, private val fatto: () -> Unit) {
    private val exec = ContextCompat.getMainExecutor(a)

    fun vista(): View {
        val col = a.colonna(20)
        if (!Vault.haArchivio(a)) {
            col.addView(a.testo("Nessun archivio sul telefono.\nApri l'app Cassaforte DL e premi \"Scegli archivio da Google Drive\".", 16f))
            return col
        }
        val utenti = Vault.utenti(a)
        val sp = Spinner(a).apply { adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, utenti) }
        Vault.ultimoUtente(a)?.let { u -> utenti.indexOfFirst { it.equals(u, true) }.takeIf { it >= 0 }?.let(sp::setSelection) }
        val pw = EditText(a).apply { hint = "Password principale"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val err = a.testo("", 14f, col = 0xFFC0392B.toInt())
        val prog = ProgressBar(a).apply { visibility = View.GONE }
        if (utenti.size > 1) { col.addView(a.testo("Utente", 13f)); col.addView(sp) }
        col.addView(pw, lp(top = 8, c = a))
        val ok = a.bottone("Sblocca", true) {
            err.text = ""; prog.visibility = View.VISIBLE
            val nome = sp.selectedItem as String; val m = pw.text.toString()
            Executors.newSingleThreadExecutor().execute {   // scrypt: qualche secondo, fuori dal thread grafico
                val e = runCatching { Vault.sbloccaConPassword(a, nome, m) }.exceptionOrNull()
                a.runOnUiThread { prog.visibility = View.GONE; if (e != null) err.text = e.message else { pw.setText(""); proponiImpronta() } }
            }
        }
        col.addView(ok, lp(top = 12, c = a)); col.addView(prog); col.addView(err)
        if (Vault.improntaAttiva(a)) {
            col.addView(a.bottone("☝  Sblocca con impronta") { impronta(err) }, lp(top = 8, c = a))
            col.post { impronta(err) }
        }
        return col
    }

    private fun prompt(cb: BiometricPrompt.AuthenticationCallback) = BiometricPrompt(a, exec, cb)
    private fun info(t: String) = BiometricPrompt.PromptInfo.Builder().setTitle(t)
        .setNegativeButtonText("Usa la password").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()

    private fun impronta(err: TextView) {
        val ci = Vault.cipherPerAprire(a) ?: run { err.text = "Impronta da riattivare: entra con la password"; return }
        prompt(object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                try { Vault.sbloccaConImpronta(a, r.cryptoObject!!.cipher!!); fatto() } catch (e: Exception) { err.text = e.message }
            }
        }).authenticate(info("Sblocca la Cassaforte"), BiometricPrompt.CryptoObject(ci))
    }

    private fun proponiImpronta() {
        val puo = BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!puo || (Vault.improntaAttiva(a) && Vault.improntaUtente(a) == Vault.utente)) { fatto(); return }
        android.app.AlertDialog.Builder(a).setTitle("Sblocco con impronta")
            .setMessage("Vuoi aprire la Cassaforte con l'impronta d'ora in poi?")
            .setPositiveButton("Sì") { _, _ ->
                val ci = try { Vault.cipherPerAttivare() } catch (e: Exception) { fatto(); return@setPositiveButton }
                prompt(object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        runCatching { Vault.salvaImpronta(a, r.cryptoObject!!.cipher!!) }
                        Toast.makeText(a, "Impronta attivata", Toast.LENGTH_SHORT).show(); fatto()
                    }
                    override fun onAuthenticationError(code: Int, s: CharSequence) { fatto() }
                }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Attiva impronta").setNegativeButtonText("Annulla")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(), BiometricPrompt.CryptoObject(ci))
            }
            .setNegativeButton("No") { _, _ -> fatto() }.setOnCancelListener { fatto() }.show()
    }
}

/** Elenco accessi con ricerca. */
class Elenco(private val c: Context, private val scelta: (Voce) -> Unit) {
    fun vista(sorgente: () -> List<Voce>): View {
        val col = c.colonna(12)
        val cerca = EditText(c).apply { hint = "Cerca fornitore, sito o utente…"; setSingleLine() }
        val lista = ListView(c)
        fun ricarica() {
            val q = cerca.text.toString().lowercase().trim()
            val l = sorgente().filter { q.isEmpty() || "${it.titolo} ${it.sito} ${it.utente} ${it.cartella}".lowercase().contains(q) }
            lista.adapter = object : BaseAdapter() {
                override fun getCount() = l.size; override fun getItem(p: Int) = l[p]; override fun getItemId(p: Int) = l[p].id
                override fun getView(p: Int, v: View?, g: ViewGroup?): View = LinearLayout(c).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(c.dp(8), c.dp(12), c.dp(8), c.dp(12))
                    addView(c.testo((if (l[p].preferito) "★ " else "") + l[p].nome, 17f, true))
                    addView(c.testo(l[p].utente.ifBlank { Vault.host(l[p].sito) }, 14f, col = Color.GRAY))
                }
            }
            lista.setOnItemClickListener { _, _, p, _ -> scelta(l[p]) }
        }
        cerca.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = ricarica()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        col.addView(cerca); col.addView(lista, lp(0).apply { weight = 1f }); ricarica()
        return col
    }
}
