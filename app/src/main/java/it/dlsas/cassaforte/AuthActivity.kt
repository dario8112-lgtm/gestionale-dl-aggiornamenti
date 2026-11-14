package it.dlsas.cassaforte

import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** Si apre quando tocchi "Cassaforte DL" sopra la tastiera: sblocca, scegli l'accesso, compila. */
class AuthActivity : AppCompatActivity() {
    private lateinit var radice: LinearLayout
    private lateinit var campi: Campi

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        @Suppress("DEPRECATION")
        campi = Campi(intent.getParcelableExtra("idUtente") as AutofillId?, intent.getParcelableExtra("idPassword") as AutofillId?,
            intent.getStringExtra("dominio"), intent.getStringExtra("pacchetto") ?: "")
        radice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(radice)
        mostra()
    }

    private fun mostra() {
        radice.removeAllViews(); radice.addView(intestazione("DL"))
        if (!Vault.sbloccata()) { radice.addView(Sblocco(this) { mostra() }.vista()); return }
        val adatte = Vault.adatte(this, campi.dominio, campi.pacchetto)
        val dove = campi.dominio ?: campi.pacchetto
        radice.addView(testo("  Accesso per: $dove", 13f, col = android.graphics.Color.GRAY))
        var tutte = adatte.isEmpty()
        lateinit var aggiorna: () -> Unit
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        aggiorna = {
            box.removeAllViews()
            if (!tutte) box.addView(bottone("Mostra tutti gli accessi") { tutte = true; aggiorna() })
            box.addView(Elenco(this) { scegli(it) }.vista { if (tutte) Vault.voci else adatte }, lp(0).apply { weight = 1f })
        }
        aggiorna(); radice.addView(box, lp(0).apply { weight = 1f })
    }

    private fun scegli(v: Voce) {
        Vault.ricorda(this, campi.dominio?.takeIf { it.isNotBlank() } ?: campi.pacchetto, v.id)   // la prossima volta lo propone subito
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, CassaforteAutofill.dataset(this, campi, v)))
        finish()
    }
}
