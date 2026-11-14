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

/** Campi di login trovati in una schermata. */
data class Campi(val utente: AutofillId?, val password: AutofillId?, val dominio: String?, val pacchetto: String)

object Analisi {
    fun trova(s: AssistStructure): Campi {
        var user: AutofillId? = null; var pass: AutofillId? = null; var dom: String? = null
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
                if (isPw) { if (pass == null) pass = id }
                else {
                    val isUser = Regex("user|login|email|e-mail|mail|utente|account|username|phone|codice").containsMatchIn("$hints $testo")
                    if (isUser && user == null && pass == null) user = id
                    if (pass == null) candidatiUser += id
                }
            }
            for (i in 0 until n.childCount) visita(n.getChildAt(i))
        }
        for (i in 0 until s.windowNodeCount) visita(s.getWindowNodeAt(i).rootViewNode)
        if (user == null && pass != null) user = candidatiUser.lastOrNull()   // il campo di testo subito prima della password
        return Campi(user, pass, dom, s.activityComponent.packageName)
    }
}

class CassaforteAutofill : AutofillService() {
    override fun onFillRequest(req: FillRequest, cancel: CancellationSignal, cb: FillCallback) {
        val s = req.fillContexts.last().structure
        val c = Analisi.trova(s)
        if (c.pacchetto == packageName || (c.utente == null && c.password == null)) { cb.onSuccess(null); return }
        val ids = listOfNotNull(c.utente, c.password).toTypedArray()

        // se la Cassaforte è già sbloccata e c'è un accesso adatto, si propone direttamente
        val r = FillResponse.Builder()
        if (Vault.sbloccata()) {
            Vault.adatte(this, c.dominio, c.pacchetto).take(4).forEach { v -> r.addDataset(dataset(this, c, v)) }
        }
        val i = Intent(this, AuthActivity::class.java)
            .putExtra("dominio", c.dominio).putExtra("pacchetto", c.pacchetto)
            .putExtra("idUtente", c.utente).putExtra("idPassword", c.password)
        val pi = PendingIntent.getActivity(this, (System.currentTimeMillis() and 0xffff).toInt(), i,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        r.addDataset(Dataset.Builder(riga(this, "🔐 Cassaforte DL", if (Vault.sbloccata()) "Scegli un altro accesso" else "Sblocca e compila"))
            .apply { ids.forEach { setValue(it, null) } }.setAuthentication(pi.intentSender).build())
        cb.onSuccess(r.build())
    }
    override fun onSaveRequest(req: SaveRequest, cb: SaveCallback) { cb.onSuccess() }

    companion object {
        fun riga(c: Context, titolo: String, sotto: String) = RemoteViews(c.packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, titolo); setTextViewText(android.R.id.text2, sotto)
        }
        fun dataset(c: Context, campi: Campi, v: Voce): Dataset {
            val b = Dataset.Builder(riga(c, v.nome, v.utente))
            campi.utente?.let { b.setValue(it, AutofillValue.forText(v.utente)) }
            campi.password?.let { b.setValue(it, AutofillValue.forText(v.password)) }
            return b.build()
        }
    }
}
