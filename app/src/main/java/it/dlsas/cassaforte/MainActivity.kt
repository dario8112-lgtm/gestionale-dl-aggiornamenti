package it.dlsas.cassaforte

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var radice: LinearLayout

    private val scegli = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        aggiorna(uri)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        radice = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(radice)
    }
    override fun onResume() { super.onResume(); mostra(); Vault.uriDrive(this)?.let { aggiorna(it, silenzioso = true) } }

    private fun aggiorna(uri: Uri, silenzioso: Boolean = false) = Executors.newSingleThreadExecutor().execute {
        val e = runCatching { Vault.importa(this, uri) }.exceptionOrNull()
        runOnUiThread {
            if (e == null) { if (!silenzioso) Toast.makeText(this, "Archivio aggiornato", Toast.LENGTH_SHORT).show(); mostra() }
            else if (!silenzioso) AlertDialog.Builder(this).setMessage("Non riesco a leggere il file: ${e.message}").setPositiveButton("OK", null).show()
        }
    }

    private fun mostra() {
        radice.removeAllViews(); radice.addView(intestazione("DL"))
        if (!Vault.haArchivio(this)) {
            val c = colonna(20)
            c.addView(testo("Benvenuto! Per iniziare scegli il file della Cassaforte su Google Drive:\nGoogle Drive › Cassaforte DL › cassaforte-dl.cdlm", 16f))
            c.addView(bottone("Scegli archivio da Google Drive", true) { scegli.launch(arrayOf("*/*")) }, lp(top = 16, c = this))
            radice.addView(c); return
        }
        if (!Vault.sbloccata()) { radice.addView(Sblocco(this) { mostra() }.vista()); return }
        val barra = LinearLayout(this).apply { setPadding(dp(12), dp(6), dp(12), 0) }
        barra.addView(bottone("🔄 Aggiorna") { Vault.uriDrive(this)?.let { aggiorna(it) } ?: scegli.launch(arrayOf("*/*")) })
        barra.addView(bottone("⚙ Compilazione") { attivaAutofill() })
        barra.addView(bottone("🔒 Blocca") { Vault.blocca(); mostra() })
        barra.addView(bottone("🩺") {
            AlertDialog.Builder(this).setTitle("Diagnostica compilazione")
                .setMessage(Diagnostica.leggi(this).ifBlank { "Nessuna richiesta ricevuta da Android finora." })
                .setPositiveButton("OK", null).show()
        })
        radice.addView(barra)
        radice.addView(testo("  Archivio del ${Vault.aggiornatoIl(this).take(16).replace('T', ' ')}", 12f, col = android.graphics.Color.GRAY))
        radice.addView(Elenco(this) { dettaglio(it) }.vista { Vault.voci }, lp(0).apply { weight = 1f })
        if (!getSystemService(AutofillManager::class.java).hasEnabledAutofillServices()) attivaAutofill(chiedi = true)
    }

    private fun attivaAutofill(chiedi: Boolean = false) {
        val am = getSystemService(AutofillManager::class.java)
        if (chiedi && getSharedPreferences("cassaforte", MODE_PRIVATE).getBoolean("noAutofill", false)) return
        if (am.hasEnabledAutofillServices()) { Toast.makeText(this, "Compilazione automatica già attiva", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Compilazione automatica")
            .setMessage("Per compilare utente e password nelle app e nei siti, scegli \"Cassaforte DL\" nella schermata che si apre.\n\nSu Samsung: Servizio preferito → Cassaforte DL.")
            .setPositiveButton("Apri impostazioni") { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Non ora") { _, _ -> getSharedPreferences("cassaforte", MODE_PRIVATE).edit().putBoolean("noAutofill", true).apply() }
            .show()
    }

    private fun copia(etichetta: String, valore: String, segreto: Boolean) {
        val cm = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(etichetta, valore)
        if (segreto) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        Toast.makeText(this, "$etichetta copiato", Toast.LENGTH_SHORT).show()
        if (segreto) radice.postDelayed({ runCatching { cm.clearPrimaryClip() } }, 45_000)
    }

    private fun dettaglio(v: Voce) {
        val c = colonna(8)
        c.addView(testo(v.sito, 14f, col = android.graphics.Color.GRAY))
        c.addView(testo("Utente: ${v.utente}", 16f).apply { setTextIsSelectable(true) }, lp(top = 10, c = this))
        val pw = testo("Password: ••••••••", 16f)
        c.addView(pw, lp(top = 6, c = this))
        val r1 = LinearLayout(this)
        r1.addView(bottone("Copia utente") { copia("Utente", v.utente, false) })
        r1.addView(bottone("Copia password") { copia("Password", v.password, true) })
        r1.addView(bottone("👁") { pw.text = if (pw.text.contains("•")) "Password: ${v.password}" else "Password: ••••••••" })
        c.addView(r1, lp(top = 10, c = this))
        if (v.note.isNotBlank()) c.addView(testo(v.note, 14f), lp(top = 8, c = this))
        val d = AlertDialog.Builder(this).setTitle(v.nome).setView(c).setNegativeButton("Chiudi", null)
        if (v.sito.isNotBlank()) d.setPositiveButton("Apri sito") { _, _ ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (v.sito.contains("://")) v.sito else "https://${v.sito}"))) }
        }
        d.show()
    }

    override fun onStop() { super.onStop(); if (isFinishing) Vault.blocca() }
}
