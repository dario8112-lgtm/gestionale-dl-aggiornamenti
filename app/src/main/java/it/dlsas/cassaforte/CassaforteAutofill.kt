package it.dlsas.cassaforte

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import android.service.autofill.InlinePresentation
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi

/** Campi di login trovati in una schermata. */
data class Campi(val utente: AutofillId?, val password: AutofillId?, val dominio: String?, val pacchetto: String,
                 val tutti: List<AutofillId> = emptyList(), val focus: String = "")

object Analisi {
    fun trova(s: AssistStructure): Campi {
        var user: AutofillId? = null; var pass: AutofillId? = null; var dom: String? = null
        var focusTesto: AutofillId? = null; var focusDesc = ""; val tutti = ArrayList<AutofillId>()
        val candidatiUser = ArrayList<AutofillId>()
        fun visita(n: AssistStructure.ViewNode) {
            if (dom == null && !n.webDomain.isNullOrBlank()) dom = n.webDomain
            val id = n.autofillId
            if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT && n.visibility == View.VISIBLE) {
                val hints = (n.autofillHints?.joinToString(" ") ?: "").lowercase()
                val testo = listOfNotNull(n.idEntry, n.hint, n.htmlInfo?.attributes?.joinToString(" ") { "${it.first}=${it.second}" }, n.contentDescription?.toString())
                    .joinToString(" ").lowercase()
                val cls = n.inputType and InputType.TYPE_MASK_CLASS; val varr = n.inputType and InputType.TYPE_MASK_VARIATION
                val isPw = hints.contains("password") || (cls == InputType.TYPE_CLASS_TEXT && (varr == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        varr == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD || varr == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)) ||
                        (cls == InputType.TYPE_CLASS_NUMBER && (n.inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0) ||
                        Regex("type=password|\\bpass|pwd|parola").containsMatchIn(testo)
                tutti += id
                if (n.isFocused) focusDesc = (if (isPw) "password " else "testo ") + (n.idEntry ?: n.hint ?: "")
                if (isPw) { if (pass == null) pass = id }
                else {
                    val isUser = Regex("user|login|email|e-mail|mail|utente|account|username|phone|codice").containsMatchIn("$hints $testo")
                    if (isUser && user == null && pass == null) user = id
                    if (pass == null) candidatiUser += id
                    if (n.isFocused) focusTesto = id
                }
            }
            for (i in 0 until n.childCount) visita(n.getChildAt(i))
        }
        for (i in 0 until s.windowNodeCount) visita(s.getWindowNodeAt(i).rootViewNode)
        if (focusTesto != null) user = focusTesto                              // il campo toccato è quello dell'utente
        else if (pass != null && (user == null || candidatiUser.indexOf(user) < candidatiUser.size - 2))
            user = candidatiUser.lastOrNull() ?: user                           // il campo di testo subito prima della password
        return Campi(user, pass, dom, s.activityComponent.packageName, tutti, focusDesc)
    }
}

class CassaforteAutofill : AutofillService() {
    override fun onFillRequest(req: FillRequest, cancel: CancellationSignal, cb: FillCallback) {
        try { compila(req, cb, inlineOk = true) }
        catch (e: Throwable) {
            Diagnostica.scrivi(this, "ERRORE: ${e.javaClass.simpleName}: ${e.message}")
            try { compila(req, cb, inlineOk = false) } catch (e2: Throwable) { Diagnostica.scrivi(this, "ERRORE 2: ${e2.message}"); cb.onSuccess(null) }
        }
    }
    private fun compila(req: FillRequest, cb: FillCallback, inlineOk: Boolean) {
        val s = req.fillContexts.last().structure
        val c = Analisi.trova(s)
        Diagnostica.scrivi(this, "richiesta da ${c.pacchetto} sito=${c.dominio ?: "-"} utente=${c.utente != null} password=${c.password != null} campi=${c.tutti.size} toccato=${c.focus.ifBlank { "?" }} tastiera=${req.inlineSuggestionsRequest != null}")
        if (c.pacchetto == packageName || (c.utente == null && c.password == null)) { cb.onSuccess(null); return }
        val ids = (listOfNotNull(c.utente, c.password) + c.tutti).distinct().take(6).toTypedArray()
        val specs = if (inlineOk) req.inlineSuggestionsRequest?.inlinePresentationSpecs.orEmpty() else emptyList()
        val maxInline = req.inlineSuggestionsRequest?.maxSuggestionCount ?: 0
        var n = 0
        fun spec(): InlinePresentationSpec? = if (specs.isEmpty() || n >= maxInline) null else specs[minOf(n, specs.size - 1)].also { n++ }

        // se la Cassaforte è già sbloccata e c'è un accesso adatto, si propone direttamente
        val r = FillResponse.Builder()
        if (Vault.sbloccata()) {
            Vault.adatte(this, c.dominio, c.pacchetto).take(3).forEach { v -> r.addDataset(dataset(this, c, v, spec())) }
        }
        val i = Intent(this, AuthActivity::class.java)
            .putExtra("dominio", c.dominio).putExtra("pacchetto", c.pacchetto)
            .putExtra("idUtente", c.utente).putExtra("idPassword", c.password)
        val pi = PendingIntent.getActivity(this, (System.currentTimeMillis() and 0xffff).toInt(), i,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        val sotto = if (Vault.sbloccata()) "Altro accesso" else "Sblocca e compila"
        val inl = spec()?.let { inline(this, it, "🔐 Cassaforte DL", sotto) }
        r.addDataset(Dataset.Builder(riga(this, "🔐 Cassaforte DL", sotto))
            .apply { ids.forEach { if (inl != null) setValue(it, null, riga(this@CassaforteAutofill, "🔐 Cassaforte DL", sotto), inl) else setValue(it, null) } }
            .setAuthentication(pi.intentSender).build())
        cb.onSuccess(r.build())
        Diagnostica.scrivi(this, "risposta inviata (${ids.size} campi)")
    }
    override fun onSaveRequest(req: SaveRequest, cb: SaveCallback) { cb.onSuccess() }

    companion object {
        fun riga(c: Context, titolo: String, sotto: String) = RemoteViews(c.packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, titolo); setTextViewText(android.R.id.text2, sotto)
        }
        /** Suggerimento nella barra della tastiera (SwiftKey, Gboard, tastiera Samsung). */
        fun inline(c: Context, s: InlinePresentationSpec, titolo: String, sotto: String): InlinePresentation? {
            if (!UiVersions.getVersions(s.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
            val pi = PendingIntent.getActivity(c, 0, android.content.Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val slice = InlineSuggestionUi.newContentBuilder(pi).setTitle(titolo).setSubtitle(sotto)
                .setStartIcon(android.graphics.drawable.Icon.createWithResource(c, R.mipmap.ic_launcher)).build().slice
            return InlinePresentation(slice, s, false)
        }
        fun dataset(c: Context, campi: Campi, v: Voce, s: InlinePresentationSpec? = null): Dataset {
            val rv = riga(c, v.nome, v.utente); val inl = s?.let { inline(c, it, v.nome, v.utente) }
            val b = Dataset.Builder(rv)
            fun metti(id: AutofillId, val_: String) { if (inl != null) b.setValue(id, AutofillValue.forText(val_), rv, inl) else b.setValue(id, AutofillValue.forText(val_)) }
            campi.utente?.let { metti(it, v.utente) }
            campi.password?.let { metti(it, v.password) }
            return b.build()
        }
    }
}

/** Ultimi eventi della compilazione automatica, da mostrare nell'app per capire cosa succede. */
object Diagnostica {
    fun scrivi(c: Context, riga: String) {
        val p = c.getSharedPreferences("diagnostica", Context.MODE_PRIVATE)
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY).format(java.util.Date())
        p.edit().putString("log", ("$t $riga\n" + p.getString("log", "")).take(3000)).apply()
    }
    fun leggi(c: Context) = c.getSharedPreferences("diagnostica", Context.MODE_PRIVATE).getString("log", "").orEmpty()
}
