package it.dlsas.cassaforte

import androidx.activity.enableEdgeToEdge
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var radice: android.widget.FrameLayout
    private val salvataggio = Salvataggio(this) { mostra() }

    private val scegli = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        aggiorna(uri)
    }

    override fun onCreate(s: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(s)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        radice = android.widget.FrameLayout(this).apply { setBackgroundColor(cSuperficie) }
        setContentView(radice); bordiSistema(radice)
    }
    override fun onResume() { super.onResume(); mostra(); Vault.uriDrive(this)?.let { aggiorna(it, silenzioso = true) } }

    private fun aggiorna(uri: Uri, silenzioso: Boolean = false) = Executors.newSingleThreadExecutor().execute {
        val e = runCatching { Vault.importa(this, uri) }.exceptionOrNull()
        runOnUiThread {
            if (e == null) { if (!silenzioso) Toast.makeText(this, "Archivio aggiornato", Toast.LENGTH_SHORT).show(); mostra() }
            else if (!silenzioso) MaterialAlertDialogBuilder(this).setTitle("Non riesco a leggere il file").setMessage(e.message).setPositiveButton("OK", null).show()
        }
    }

    private fun quando(): String = try {
        val t = java.time.OffsetDateTime.parse(Vault.aggiornatoIl(this)).atZoneSameInstant(java.time.ZoneId.systemDefault())
        "aggiornato " + t.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    } catch (e: Exception) { "" }

    private fun mostra() {
        radice.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        radice.addView(col)
        if (!Vault.haArchivio(this)) {
            col.addView(intestazione("Benvenuto"))
            val c = colonna(24)
            c.addView(android.widget.ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; topMargin = dp(24) })
            c.addView(testo("Collega l'archivio", 22f, true).apply { gravity = android.view.Gravity.CENTER }, lp(top = 20, c = this))
            c.addView(testo("Scegli il file della Cassaforte su Google Drive:\nCassaforte DL › cassaforte-dl.cdlm", 15f, col = cTenue).apply { gravity = android.view.Gravity.CENTER }, lp(top = 8, c = this))
            c.addView(bottone("Scegli da Google Drive", true, R.drawable.ic_open) { scegli.launch(arrayOf("*/*")) }, lp(top = 28, c = this))
            col.addView(c); radice.requestApplyInsets(); return
        }
        if (!Vault.sbloccata()) {
            col.addView(intestazione("Bloccata"))
            col.addView(Sblocco(this) { mostra() }.vista(), lp(0).apply { weight = 1f }); radice.requestApplyInsets(); return
        }
        val bianco = android.graphics.Color.WHITE
        col.addView(intestazione(Vault.utente ?: "", listOf(
            iconaBottone(R.drawable.ic_lock, "Blocca", bianco) { Vault.blocca(); mostra() },
            iconaBottone(R.drawable.ic_more, "Altro", bianco) { menu(it) })))
        val att = Vault.inAttesa(this)
        if (att > 0) col.addView(LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL; background = arrotondato(cContenitore, 12); setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(android.widget.ImageView(context).apply { setImageResource(R.drawable.ic_schedule); imageTintList = android.content.res.ColorStateList.valueOf(cSuContenitore) }, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(testo(if (att == 1) "  1 accesso in arrivo al Gestionale" else "  $att accessi in arrivo al Gestionale", 13f, col = cSuContenitore))
        }, lp(top = 12, c = this).apply { leftMargin = dp(16); rightMargin = dp(16) })
        col.addView(Elenco(this, quando()) { dettaglio(it) }.vista { Vault.voci }, lp(0).apply { weight = 1f })
        val fab = com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton(this).apply {
            text = "Nuovo"; setIconResource(R.drawable.ic_add); isAllCaps = false
            setOnClickListener { moduloAccesso(null, "Nuovo accesso") { salvataggio.salva(it) } }
        }
        radice.addView(fab, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM or android.view.Gravity.END).apply { setMargins(dp(16), dp(16), dp(16), dp(20)) })
        radice.requestApplyInsets()
        if (!getSystemService(AutofillManager::class.java).hasEnabledAutofillServices()) attivaAutofill(chiedi = true)
    }

    private fun menu(ancora: View) {
        val m = androidx.appcompat.widget.PopupMenu(this, ancora)
        m.menu.add(0, 1, 0, "Aggiorna da Google Drive"); m.menu.add(0, 2, 1, "Compilazione automatica"); m.menu.add(0, 3, 2, "Diagnostica"); m.menu.add(0, 4, 3, "Ricarica i loghi dei siti")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> Vault.uriDrive(this)?.let { u -> aggiorna(u) } ?: scegli.launch(arrayOf("*/*"))
                2 -> attivaAutofill()
                4 -> { Icone.svuota(this); mostra() }
                3 -> MaterialAlertDialogBuilder(this).setTitle("Diagnostica compilazione")
                    .setMessage(Diagnostica.leggi(this).ifBlank { "Nessuna richiesta ricevuta da Android finora." }).setPositiveButton("OK", null).show()
            }; true
        }
        m.show()
    }

    private fun attivaAutofill(chiedi: Boolean = false) {
        val am = getSystemService(AutofillManager::class.java)
        if (chiedi && getSharedPreferences("cassaforte", MODE_PRIVATE).getBoolean("noAutofill", false)) return
        if (am.hasEnabledAutofillServices()) { Toast.makeText(this, "Compilazione automatica già attiva", Toast.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(this).setTitle("Compilazione automatica")
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
        val d = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val c = colonna(20)
        val testa = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        val logo = android.widget.FrameLayout(this); val img = android.widget.ImageView(this); val lett = avatar(v.nome, 52)
        logo.addView(lett, android.widget.FrameLayout.LayoutParams(dp(52), dp(52))); logo.addView(img, android.widget.FrameLayout.LayoutParams(dp(52), dp(52)))
        Icone.carica(this, v.sito, img, { img.visibility = View.INVISIBLE; lett.visibility = View.VISIBLE }, { img.visibility = View.VISIBLE; lett.visibility = View.INVISIBLE })
        testa.addView(logo)
        testa.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0)
            addView(testo(v.nome, 20f, true)); if (v.sito.isNotBlank()) addView(testo(Vault.host(v.sito).ifBlank { v.sito }, 14f, col = cTenue))
        })
        c.addView(testa)
        fun riga(etichetta: String, valore: String, segreto: Boolean) {
            val r = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; background = arrotondato(cScheda, 14); setPadding(dp(16), dp(10), dp(6), dp(10)) }
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(testo(etichetta, 12f, col = cTenue))
            val val_ = testo(if (segreto) "••••••••••" else valore.ifBlank { "—" }, 17f).apply { if (!segreto) setTextIsSelectable(true) }
            t.addView(val_); r.addView(t, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (segreto) r.addView(iconaBottone(R.drawable.ic_eye, "Mostra") {
                val_.text = if (val_.text.startsWith("•")) valore else "••••••••••"
                val_.typeface = if (val_.text.startsWith("•")) android.graphics.Typeface.DEFAULT else android.graphics.Typeface.MONOSPACE
            })
            r.addView(iconaBottone(R.drawable.ic_copy, "Copia $etichetta", cPrimario) { copia(etichetta, valore, segreto) })
            c.addView(r, lp(top = 12, c = this))
        }
        riga("Utente", v.utente, false); riga("Password", v.password, true)
        if (v.note.isNotBlank()) c.addView(testo(v.note, 14f, col = cTenue).apply { background = arrotondato(cScheda, 14); setPadding(dp(16), dp(12), dp(16), dp(12)) }, lp(top = 12, c = this))
        if (v.inAttesa) c.addView(testo("In arrivo al Gestionale", 13f, col = cSuContenitore).apply {
            background = arrotondato(cContenitore, 12); setPadding(dp(12), dp(8), dp(12), dp(8))
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_schedule, 0, 0, 0); compoundDrawablePadding = dp(8)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(cSuContenitore)
        }, LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        val b = LinearLayout(this)
        if (v.sito.isNotBlank()) b.addView(bottone("Apri sito", true, R.drawable.ic_open) {
            d.dismiss(); runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (v.sito.contains("://")) v.sito else "https://${v.sito}"))) }
        }, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
        b.addView(bottone("Modifica", false, R.drawable.ic_edit) { d.dismiss(); moduloAccesso(v, "Modifica accesso") { salvataggio.salva(it) } },
            LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(b, lp(top = 20, c = this))
        d.setContentView(c); d.show()
    }

    override fun onStop() { super.onStop(); if (isFinishing) Vault.blocca() }
}
