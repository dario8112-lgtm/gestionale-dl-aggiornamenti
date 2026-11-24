package it.dlsas.cassaforte

import androidx.activity.enableEdgeToEdge
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** "Salvare in Cassaforte DL?": si apre dopo un login fatto a mano in un'app o in un sito. */
class SalvaActivity : AppCompatActivity() {
    private lateinit var radice: LinearLayout
    private val salvataggio = Salvataggio(this) { finish() }
    private var proposto = false

    override fun onCreate(s: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(s)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        radice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cSuperficie) }
        setContentView(radice); bordiSistema(radice); mostra()
    }

    private fun mostra() {
        radice.removeAllViews(); radice.addView(intestazione("Salva accesso"))
        if (!Vault.sbloccata()) { radice.addView(colonna().apply { addView(testo("Sblocca per salvare l'accesso", 16f, true)) }); radice.addView(Sblocco(this) { mostra() }.vista()); return }
        if (proposto) return; proposto = true
        val user = intent.getStringExtra("utente").orEmpty(); val pass = intent.getStringExtra("password").orEmpty()
        val dom = intent.getStringExtra("dominio"); val pac = intent.getStringExtra("pacchetto").orEmpty()
        val nomeApp = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pac, 0)).toString() }.getOrDefault(pac)
        val esiste = Vault.esistente(dom, pac, user)
        if (esiste != null && esiste.password == pass) { finish(); return }          // niente di nuovo
        val proposta = esiste?.copy(password = pass) ?: Voce(0,
            if (!dom.isNullOrBlank()) dom.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() } else nomeApp,
            if (!dom.isNullOrBlank()) "https://${dom.removePrefix("www.")}" else pac, user, pass, "", "", false)
        moduloAccesso(proposta, if (esiste != null) "Aggiornare la password di ${esiste.nome}?" else "Salvare in Cassaforte DL?",
            annullato = { finish() }) { salvataggio.salva(it) }
    }
}
