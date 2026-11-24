package it.dlsas.cassaforte

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors

const val BLU = 0xFF1E3A5F.toInt()
const val BLU_SCURO = 0xFF14263F.toInt()
const val ORO = 0xFFF1C40F.toInt()

// ---------- piccoli aiuti grafici ----------
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.colore(attr: Int) = MaterialColors.getColor(this, attr, Color.GRAY)
val Context.cSuperficie get() = colore(com.google.android.material.R.attr.colorSurface)
val Context.cTesto get() = colore(com.google.android.material.R.attr.colorOnSurface)
val Context.cTenue get() = colore(com.google.android.material.R.attr.colorOnSurfaceVariant)
val Context.cPrimario get() = colore(androidx.appcompat.R.attr.colorPrimary)
val Context.cScheda get() = colore(com.google.android.material.R.attr.colorSurfaceContainerLow)
val Context.cContenitore get() = colore(com.google.android.material.R.attr.colorSecondaryContainer)
val Context.cSuContenitore get() = colore(com.google.android.material.R.attr.colorOnSecondaryContainer)

fun Context.testo(s: String, size: Float = 16f, bold: Boolean = false, col: Int? = null) = TextView(this).apply {
    text = s; textSize = size; setTextColor(col ?: cTesto)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}
fun Context.bottone(s: String, primario: Boolean = false, icona: Int? = null, onClick: () -> Unit): MaterialButton =
    MaterialButton(this, null, if (primario) com.google.android.material.R.attr.materialButtonStyle
        else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = s; isAllCaps = false; textSize = 15f; minHeight = dp(52); cornerRadius = dp(14)
        icona?.let { setIconResource(it); iconPadding = dp(8) }
        setOnClickListener { onClick() }
    }
fun Context.iconaBottone(icona: Int, descr: String, tinta: Int = cTesto, onClick: (View) -> Unit) = ImageButton(this).apply {
    setImageResource(icona); imageTintList = ColorStateList.valueOf(tinta); contentDescription = descr
    val tv = TypedValue(); context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
    setBackgroundResource(tv.resourceId); setPadding(dp(10), dp(10), dp(10), dp(10))
    setOnClickListener { onClick(it) }
}
fun Context.colonna(pad: Int = 16) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(pad), dp(pad), dp(pad), dp(pad)) }
fun lp(h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, c: Context? = null) =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h).apply { if (c != null) topMargin = c.dp(top) }
fun Context.arrotondato(colore: Int, raggio: Int) = GradientDrawable().apply { setColor(colore); cornerRadius = dp(raggio).toFloat() }

/** Colore fisso per ogni nome (per i cerchietti con l'iniziale). */
private val TAVOLOZZA = intArrayOf(0xFF1E3A5F.toInt(), 0xFF2E7D32.toInt(), 0xFFB8860B.toInt(), 0xFF6A1B9A.toInt(),
    0xFFC62828.toInt(), 0xFF00838F.toInt(), 0xFFAD1457.toInt(), 0xFF4E342E.toInt(), 0xFF283593.toInt(), 0xFFEF6C00.toInt())
fun Context.avatar(nome: String, size: Int = 44) = TextView(this).apply {
    text = nome.trim().firstOrNull()?.uppercase() ?: "?"; gravity = Gravity.CENTER; textSize = size / 2.6f
    setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(TAVOLOZZA[Math.floorMod(nome.lowercase().hashCode(), TAVOLOZZA.size)]) }
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
}

/** Barra blu in alto: logo, titolo, sottotitolo e pulsanti a destra. */
fun Context.intestazione(sotto: String = "", azioni: List<View> = emptyList()) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(BLU, BLU_SCURO))
    setPadding(dp(16), dp(14), dp(6), dp(14)); tag = "intestazione"
    addView(ImageView(context).apply {
        setImageResource(R.mipmap.ic_launcher); layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(12) }
    })
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(context).apply {
            addView(testo("Cassaforte", 20f, true, ORO)); addView(testo(" DL", 20f, true, Color.WHITE))
        })
        if (sotto.isNotBlank()) addView(testo(sotto, 13f, col = 0xCCFFFFFF.toInt()))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    azioni.forEach { addView(it) }
}

/** Margini di sistema (barra di stato e navigazione): la barra blu scende sotto la barra di stato. */
fun AppCompatActivity.bordiSistema(radice: View) {
    androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(radice) { v, ins ->
        val b = ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
        v.findViewWithTag<View>("intestazione")?.let { it.setPadding(it.paddingLeft, dp(14) + b.top, it.paddingRight, it.paddingBottom) }
        v.setPadding(b.left, 0, b.right, b.bottom); ins
    }
}

/** Schermata di sblocco condivisa tra app, compilazione automatica e salvataggio. */
class Sblocco(private val a: AppCompatActivity, private val fatto: () -> Unit) {
    private val exec = ContextCompat.getMainExecutor(a)

    fun vista(): View {
        val sv = ScrollView(a).apply { isFillViewport = true }
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(a.dp(24), a.dp(28), a.dp(24), a.dp(24)) }
        sv.addView(col)
        col.addView(ImageView(a).apply { setImageResource(R.mipmap.ic_launcher) }, LinearLayout.LayoutParams(a.dp(84), a.dp(84)))
        col.addView(a.testo("Cassaforte bloccata", 22f, true).apply { gravity = Gravity.CENTER }, lp(top = 16, c = a))
        if (!Vault.haArchivio(a)) {
            col.addView(a.testo("Nessun archivio sul telefono.\nApri l'app Cassaforte DL e scegli il file da Google Drive.", 15f, col = a.cTenue).apply { gravity = Gravity.CENTER }, lp(top = 8, c = a))
            return sv
        }
        col.addView(a.testo("Le tue password restano cifrate sul telefono", 14f, col = a.cTenue).apply { gravity = Gravity.CENTER }, lp(top = 4, c = a))

        val carta = MaterialCardView(a).apply { radius = a.dp(20).toFloat(); cardElevation = 0f; setCardBackgroundColor(a.cScheda); strokeWidth = 0 }
        val dentro = a.colonna(18); carta.addView(dentro)
        col.addView(carta, lp(top = 24, c = a))

        val utenti = Vault.utenti(a)
        val sceltaUtente = MaterialAutoCompleteTextView(a).apply {
            setSimpleItems(utenti.toTypedArray()); inputType = InputType.TYPE_NULL
            setText(Vault.ultimoUtente(a)?.let { u -> utenti.firstOrNull { it.equals(u, true) } } ?: utenti.firstOrNull() ?: "", false)
        }
        if (utenti.size > 1) {
            val tilU = TextInputLayout(a, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
                hint = "Utente"; setBoxCornerRadii(a.dp(12).toFloat(), a.dp(12).toFloat(), a.dp(12).toFloat(), a.dp(12).toFloat()); addView(sceltaUtente)
            }
            dentro.addView(tilU, lp(top = 0, c = a))
        }
        val tilP = TextInputLayout(a, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Password principale"; endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            setBoxCornerRadii(a.dp(12).toFloat(), a.dp(12).toFloat(), a.dp(12).toFloat(), a.dp(12).toFloat())
        }
        val pw = TextInputEditText(tilP.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        tilP.addView(pw); dentro.addView(tilP, lp(top = if (utenti.size > 1) 12 else 0, c = a))
        val prog = LinearProgressIndicator(a).apply { isIndeterminate = true; visibility = View.GONE }
        val ok = a.bottone("Sblocca", true, R.drawable.ic_lock) {}
        ok.setOnClickListener {
            tilP.error = null; prog.visibility = View.VISIBLE; ok.isEnabled = false; ok.text = "Apertura in corso…"
            val nome = sceltaUtente.text.toString().ifBlank { utenti.first() }; val m = pw.text.toString()
            Executors.newSingleThreadExecutor().execute {   // scrypt: qualche secondo, fuori dal thread grafico
                val e = runCatching { Vault.sbloccaConPassword(a, nome, m) }.exceptionOrNull()
                a.runOnUiThread {
                    prog.visibility = View.GONE; ok.isEnabled = true; ok.text = "Sblocca"
                    if (e != null) tilP.error = e.message else { pw.setText(""); proponiImpronta() }
                }
            }
        }
        pw.setOnEditorActionListener { _, _, _ -> ok.performClick(); true }
        dentro.addView(ok, lp(top = 16, c = a)); dentro.addView(prog, lp(top = 8, c = a))
        if (Vault.improntaAttiva(a)) {
            col.addView(a.bottone("Sblocca con impronta", false, R.drawable.ic_fingerprint) { impronta(tilP) }, lp(top = 16, c = a))
            col.post { impronta(tilP) }
        }
        return sv
    }

    private fun prompt(cb: BiometricPrompt.AuthenticationCallback) = BiometricPrompt(a, exec, cb)
    private fun info(t: String) = BiometricPrompt.PromptInfo.Builder().setTitle(t).setSubtitle("Cassaforte DL")
        .setNegativeButtonText("Usa la password").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build()

    private fun impronta(err: TextInputLayout) {
        val ci = Vault.cipherPerAprire(a) ?: run { err.error = "Impronta da riattivare: entra con la password"; return }
        prompt(object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                try { Vault.sbloccaConImpronta(a, r.cryptoObject!!.cipher!!); fatto() } catch (e: Exception) { err.error = e.message }
            }
        }).authenticate(info("Sblocca la Cassaforte"), BiometricPrompt.CryptoObject(ci))
    }

    private fun proponiImpronta() {
        val puo = BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!puo || (Vault.improntaAttiva(a) && Vault.improntaUtente(a) == Vault.utente)) { fatto(); return }
        MaterialAlertDialogBuilder(a).setIcon(R.drawable.ic_fingerprint).setTitle("Sblocco con impronta")
            .setMessage("Vuoi aprire la Cassaforte con l'impronta d'ora in poi?")
            .setPositiveButton("Attiva") { _, _ ->
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
            .setNegativeButton("Non ora") { _, _ -> fatto() }.setOnCancelListener { fatto() }.show()
    }
}

/** Elenco accessi con ricerca. */
class Elenco(private val c: Context, private val info: String = "", private val scelta: (Voce) -> Unit) {
    fun vista(sorgente: () -> List<Voce>): View {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(c.dp(16), c.dp(14), c.dp(16), 0) }
        val tilC = TextInputLayout(c, null, com.google.android.material.R.attr.textInputFilledStyle).apply {
            hint = "Cerca fornitore, sito o utente"; setStartIconDrawable(R.drawable.ic_search)
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT; boxStrokeWidth = 0; boxStrokeWidthFocused = 0
            val r = c.dp(28).toFloat(); setBoxCornerRadii(r, r, r, r)
        }
        val cerca = TextInputEditText(tilC.context).apply { setSingleLine(); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO }
        tilC.addView(cerca)
        val conta = c.testo("", 13f, col = c.cTenue)
        val lista = GridView(c).apply {
            numColumns = GridView.AUTO_FIT; columnWidth = c.dp(150); stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = c.dp(10); verticalSpacing = c.dp(10); clipToPadding = false; setPadding(0, c.dp(6), 0, c.dp(96))
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        }
        val vuoto = c.testo("Nessun accesso trovato", 15f, col = c.cTenue).apply { gravity = Gravity.CENTER; setPadding(0, c.dp(40), 0, 0); visibility = View.GONE }
        fun ricarica() {
            val q = cerca.text.toString().lowercase().trim()
            val l = sorgente().filter { q.isEmpty() || "${it.titolo} ${it.sito} ${it.utente} ${it.cartella}".lowercase().contains(q) }
            conta.text = (if (q.isEmpty()) "${l.size} accessi" else "${l.size} trovati") + (if (info.isNotBlank()) "  ·  $info" else "")
            vuoto.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
            lista.adapter = object : BaseAdapter() {
                override fun getCount() = l.size; override fun getItem(p: Int) = l[p]; override fun getItemId(p: Int) = l[p].id
                override fun getView(p: Int, v: View?, g: ViewGroup?): View = riga(l[p])
            }
            lista.setOnItemClickListener { _, _, p, _ -> scelta(l[p]) }
        }
        cerca.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = ricarica()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
        })
        col.addView(tilC); col.addView(conta, lp(top = 10, c = c).apply { leftMargin = c.dp(6) })
        col.addView(vuoto); col.addView(lista, lp(0).apply { weight = 1f; topMargin = c.dp(4) }); ricarica()
        return col
    }

    /** Casella grande con il logo del sito, come in RoboForm. */
    private fun riga(v: Voce): View {
        val card = MaterialCardView(c).apply {
            radius = c.dp(18).toFloat(); cardElevation = c.dp(1).toFloat(); setCardBackgroundColor(c.cScheda)
            strokeWidth = c.dp(1); strokeColor = (c.cTenue and 0x00FFFFFF) or 0x22000000
            isClickable = false; isFocusable = false
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(132))
        }
        val dentro = FrameLayout(c)
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(c.dp(10), c.dp(14), c.dp(10), c.dp(12)) }
        val logo = FrameLayout(c)
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.INVISIBLE }
        val lettera = c.avatar(v.nome, 52)
        logo.addView(lettera, FrameLayout.LayoutParams(c.dp(52), c.dp(52), Gravity.CENTER))
        logo.addView(img, FrameLayout.LayoutParams(c.dp(56), c.dp(56), Gravity.CENTER))
        col.addView(logo, LinearLayout.LayoutParams(c.dp(60), c.dp(60)))
        col.addView(c.testo(v.nome, 14f, true).apply {
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, lp(top = 10, c = c))
        dentro.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // segnalini negli angoli: preferito in alto a destra, "in arrivo" in basso a destra
        if (v.preferito) dentro.addView(ImageView(c).apply { setImageResource(R.drawable.ic_star); imageTintList = ColorStateList.valueOf(0xFFE0A800.toInt()) },
            FrameLayout.LayoutParams(c.dp(18), c.dp(18), Gravity.TOP or Gravity.END).apply { setMargins(0, c.dp(8), c.dp(8), 0) })
        if (v.inAttesa) dentro.addView(ImageView(c).apply { setImageResource(R.drawable.ic_schedule); imageTintList = ColorStateList.valueOf(c.cTenue); contentDescription = "In arrivo al Gestionale" },
            FrameLayout.LayoutParams(c.dp(16), c.dp(16), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, c.dp(8), c.dp(8)) })
        card.addView(dentro)
        Icone.carica(c, v.sito, img, manca = { img.visibility = View.INVISIBLE; lettera.visibility = View.VISIBLE },
            trovata = { img.visibility = View.VISIBLE; lettera.visibility = View.INVISIBLE })
        return card
    }
}

/** Modulo (foglio dal basso) per un accesso nuovo o da modificare. */
fun AppCompatActivity.moduloAccesso(v: Voce?, titolo: String, annullato: () -> Unit = {}, salvato: (Voce) -> Unit) {
    val d = BottomSheetDialog(this)
    val c = colonna(20)
    c.addView(testo(titolo, 20f, true))
    fun campo(etichetta: String, valore: String, pw: Boolean = false, multi: Boolean = false): TextInputEditText {
        val til = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = etichetta; val r = dp(12).toFloat(); setBoxCornerRadii(r, r, r, r)
        }
        val e = TextInputEditText(til.context).apply {
            setText(valore); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            if (multi) { minLines = 2; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE } else setSingleLine()
            if (pw) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        til.addView(e)
        if (pw) {
            til.endIconMode = TextInputLayout.END_ICON_CUSTOM; til.setEndIconDrawable(R.drawable.ic_dice); til.endIconContentDescription = "Genera password"
            til.setEndIconOnClickListener {
                val cs = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#%&*?-_"; val r = java.security.SecureRandom()
                e.setText((1..16).map { cs[r.nextInt(cs.length)] }.joinToString("")); til.helperText = "Password generata: ricordati di salvarla"
            }
        }
        c.addView(til, lp(top = 12, c = this)); return e
    }
    val nome = campo("Nome (es. Foneday)", v?.titolo ?: ""); val sito = campo("Sito o app", v?.sito ?: "")
    val ut = campo("Utente o email", v?.utente ?: ""); val pw = campo("Password", v?.password ?: "", pw = true)
    val note = campo("Note", v?.note ?: "", multi = true)
    var fatto = false
    val riga = LinearLayout(this).apply { gravity = Gravity.END }
    riga.addView(bottone("Annulla") { d.dismiss() })
    riga.addView(bottone("Salva", true) {
        if (nome.text.isNullOrBlank() && sito.text.isNullOrBlank()) { nome.error = "Inserisci almeno il nome o il sito"; return@bottone }
        fatto = true; d.dismiss()
        salvato(Voce(v?.id ?: 0, nome.text.toString().trim(), sito.text.toString().trim(), ut.text.toString().trim(),
            pw.text.toString(), note.text.toString(), v?.cartella ?: "", v?.preferito ?: false, v?.uid))
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
    c.addView(riga, lp(top = 20, c = this))
    d.setContentView(ScrollView(this).apply { addView(c) })
    d.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED; d.behavior.skipCollapsed = true
    d.setOnDismissListener { if (!fatto) annullato() }
    d.show()
}

/** Salva e, la prima volta, chiede dove mettere il file degli arrivi su Google Drive. */
class Salvataggio(private val a: AppCompatActivity, private val fine: (Boolean) -> Unit = {}) {
    private val crea = a.registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        if (u == null) { Toast.makeText(a, "Salvato solo sul telefono: scegli la cartella di Drive per mandarlo al Gestionale", Toast.LENGTH_LONG).show(); fine(true); return@registerForActivityResult }
        runCatching { a.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val e = runCatching { Vault.impostaUriArrivi(a, u) }.exceptionOrNull()
        Toast.makeText(a, if (e == null) "Salvato: arriva nel Gestionale entro 15 minuti" else "Non riesco a scrivere su Drive: ${e.message}", Toast.LENGTH_LONG).show()
        fine(true)
    }
    fun salva(v: Voce) {
        val ok = try { Vault.salva(a, v) } catch (e: Exception) {
            Toast.makeText(a, "Salvato sul telefono, ma Drive non risponde: riprovo al prossimo salvataggio", Toast.LENGTH_LONG).show(); fine(true); return }
        if (ok) { Toast.makeText(a, "Salvato: arriva nel Gestionale entro 15 minuti", Toast.LENGTH_LONG).show(); fine(true); return }
        MaterialAlertDialogBuilder(a).setTitle("Dove salvo su Google Drive?")
            .setMessage("Solo la prima volta: nella schermata che si apre scegli Google Drive › cartella \"Cassaforte DL\" e premi Salva.")
            .setPositiveButton("Scegli") { _, _ -> crea.launch("arrivi-telefono.cdla") }
            .setNegativeButton("Più tardi") { _, _ -> fine(true) }.setCancelable(false).show()
    }
}
