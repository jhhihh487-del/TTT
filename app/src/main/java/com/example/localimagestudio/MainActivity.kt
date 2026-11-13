package com.example.localimagestudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("studio_prefs", MODE_PRIVATE) }
    private var modelUri: Uri? = null
    private lateinit var promptInput: EditText
    private lateinit var negativeInput: EditText
    private lateinit var promptCount: TextView
    private lateinit var modelName: TextView
    private lateinit var status: TextView
    private lateinit var checkButton: Button
    private lateinit var stepsValue: TextView
    private lateinit var cfgValue: TextView
    private lateinit var stepsBar: SeekBar
    private lateinit var cfgBar: SeekBar
    private var steps = 24
    private var cfg = 7.0f

    private val bg = Color.rgb(10, 16, 29)
    private val card = Color.rgb(19, 29, 47)
    private val cardAlt = Color.rgb(24, 37, 58)
    private val accent = Color.rgb(65, 220, 195)
    private val primaryText = Color.rgb(239, 245, 255)
    private val secondaryText = Color.rgb(159, 176, 199)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelUri = savedInstanceState?.getString(KEY_MODEL_URI)?.let(Uri::parse)
            ?: prefs.getString(KEY_MODEL_URI, null)?.let(Uri::parse)
        buildScreen()
        restoreDraft()
        refreshModelLabel()
    }

    private fun buildScreen() {
        val pad = dp(18)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(18))
            addView(root)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(22), pad, dp(18))
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = text("LOCAL IMAGE STUDIO", 13f, accent, true)
        brand.letterSpacing = 0.12f
        brandRow.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val offline = text("●  OFFLINE", 11f, accent, true).apply {
            background = rounded(Color.rgb(20, 62, 60), 30f)
            setPadding(dp(11), dp(7), dp(11), dp(7))
        }
        brandRow.addView(offline)
        header.addView(brandRow)
        header.addView(text("حوّل أفكارك إلى صور", 27f, primaryText, true).apply {
            setPadding(0, dp(17), 0, dp(4))
        })
        header.addView(text("استوديو محلي بواجهة بسيطة وإعدادات واضحة", 14f, secondaryText))
        root.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }
        root.addView(content)

        // Prompt card
        val promptCard = makeCard()
        promptCard.addView(sectionTitle("وصف الصورة", "01  /  PROMPT"))
        promptInput = editField("صف الصورة التي تتخيلها بالتفصيل...", 4).apply {
            setText(prefs.getString(KEY_PROMPT, "") ?: "")
            setSelection(text.length)
        }
        promptCard.addView(promptInput, matchWrap(dp(12)))
        promptCount = text("0 / 1200 حرف", 11f, secondaryText)
        promptCard.addView(promptCount)
        promptInput.addTextChangedListener(SimpleTextWatcher { value ->
            promptCount.text = "${value.length} / 1200 حرف"
            if (value.length > 1200) promptInput.error = "اختصر الوصف إلى 1200 حرف"
        })
        promptCard.addView(label("أفكار سريعة", dpTop = 14))
        val presets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(
            "طبيعة" to "منظر طبيعي هادئ، جبال بعيدة، بحيرة صافية، إضاءة ذهبية ناعمة، تفاصيل واقعية",
            "مدينة" to "مدينة مستقبلية أنيقة وقت الغروب، أضواء نيون هادئة، انعكاسات جميلة، cinematic composition",
            "منتج" to "تصوير منتج احترافي على خلفية بسيطة، إضاءة استوديو ناعمة، تفاصيل دقيقة، high quality"
        ).forEach { (title, value) ->
            val b = smallAction(title)
            b.setOnClickListener { promptInput.setText(value); promptInput.setSelection(promptInput.text.length) }
            presets.addView(b, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) })
        }
        promptCard.addView(presets)
        val draftRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val restore = textButton("استعادة آخر وصف") { restoreDraft() }
        val clear = textButton("مسح") { promptInput.setText("") }
        draftRow.addView(restore, LinearLayout.LayoutParams(0, dp(42), 1f))
        draftRow.addView(clear, LinearLayout.LayoutParams(0, dp(42), 1f))
        promptCard.addView(draftRow)
        content.addView(promptCard, matchWrap(dp(14)))

        // Negative prompt card
        val negativeCard = makeCard()
        negativeCard.addView(sectionTitle("الوصف السلبي", "02  /  NEGATIVE PROMPT"))
        negativeInput = editField("عناصر لا تريد ظهورها (اختياري)...", 2).apply {
            setText(prefs.getString(KEY_NEGATIVE, "") ?: "")
        }
        negativeCard.addView(negativeInput, matchWrap())
        content.addView(negativeCard, matchWrap(dp(14)))

        // Model card
        val modelCard = makeCard()
        modelCard.addView(sectionTitle("ملف النموذج", "03  /  MODEL"))
        modelCard.addView(text("اختر ملف نموذج محفوظاً على جهازك. لا يتم رفعه إلى أي خادم.", 12f, secondaryText))
        val modelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(cardAlt, 16f)
        }
        val modelIcon = text("▣", 24f, accent, true)
        modelRow.addView(modelIcon)
        val modelTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(8), 0) }
        modelName = text("لم يتم اختيار نموذج", 13f, primaryText, true)
        modelTexts.addView(modelName)
        modelTexts.addView(text("الاختيار من ذاكرة الهاتف", 11f, secondaryText))
        modelRow.addView(modelTexts, LinearLayout.LayoutParams(0, -2, 1f))
        val choose = smallAction("اختيار")
        choose.setOnClickListener { chooseModel() }
        modelRow.addView(choose)
        modelCard.addView(modelRow, matchWrap(dp(8)))
        content.addView(modelCard, matchWrap(dp(14)))

        // Settings card
        val settingsCard = makeCard()
        settingsCard.addView(sectionTitle("إعدادات الصورة", "04  /  SETTINGS"))
        settingsCard.addView(text("الوضع الحالي: تحويل النص إلى صورة", 13f, accent, true).apply {
            background = rounded(Color.rgb(20, 62, 60), 12f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }, matchWrap(dp(12)))
        settingsCard.addView(text("أوضاع الصورة إلى صورة والتلوين الداخلي ستضاف بعد دمج المحرك.", 11f, secondaryText))

        val stepsHeader = valueHeader("عدد الخطوات", "STEPS")
        stepsValue = stepsHeader.second
        settingsCard.addView(stepsHeader.first, matchWrap())
        stepsBar = SeekBar(this).apply {
            max = 45
            progress = steps - 5
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(seekListener { steps = it + 5; stepsValue.text = steps.toString() })
        }
        stepsValue.text = steps.toString()
        settingsCard.addView(stepsBar, matchWrap(dp(10)))

        val cfgHeader = valueHeader("قوة الالتزام بالوصف", "CFG SCALE")
        cfgValue = cfgHeader.second
        settingsCard.addView(cfgHeader.first, matchWrap())
        cfgBar = SeekBar(this).apply {
            max = 26
            progress = ((cfg - 2f) * 2f).roundToInt()
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(seekListener { cfg = (2f + it * 0.5f); cfgValue.text = String.format(java.util.Locale.US, "%.1f", cfg) })
        }
        cfgValue.text = String.format(java.util.Locale.US, "%.1f", cfg)
        settingsCard.addView(cfgBar, matchWrap(dp(10)))

        settingsCard.addView(label("أبعاد الصورة", dpTop = 8))
        val sizeSpinner = spinner(listOf("512 × 512  ·  مربع", "512 × 768  ·  عمودي", "768 × 512  ·  أفقي"))
        settingsCard.addView(sizeSpinner, matchWrap(dp(10)))
        settingsCard.addView(label("Sampler", dpTop = 4))
        val samplerSpinner = spinner(listOf("Euler a", "Euler", "DPM++ 2M"))
        settingsCard.addView(samplerSpinner, matchWrap(dp(10)))
        settingsCard.addView(label("Seed  ·  اختياري", dpTop = 4))
        val seedInput = editField("اتركه فارغاً لاختيار قيمة تلقائية", 1).apply { inputType = 2 }
        settingsCard.addView(seedInput)
        content.addView(settingsCard, matchWrap(dp(14)))

        // Generate / status card
        val actionCard = makeCard().apply { background = rounded(Color.rgb(17, 42, 55), 20f) }
        actionCard.addView(text("جاهز لفحص الإعدادات؟", 18f, primaryText, true))
        actionCard.addView(text("سيتم فحص المدخلات فقط. محرك توليد الصور لم يُدمج بعد في هذه النسخة.", 12f, secondaryText).apply {
            setPadding(0, dp(6), 0, dp(12))
        })
        checkButton = Button(this).apply {
            text = "فحص الإعدادات"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.rgb(7, 24, 29))
            background = rounded(accent, 14f)
            minHeight = dp(52)
            setOnClickListener { validateAndRun(sizeSpinner.selectedItem.toString(), samplerSpinner.selectedItem.toString(), seedInput.text.toString()) }
        }
        actionCard.addView(checkButton, matchWrap(dp(10)))
        status = text("المحرك: غير مدمج حالياً", 12f, secondaryText)
        actionCard.addView(status)
        content.addView(actionCard, matchWrap(dp(12)))

        val footer = text("LOCAL FIRST  •  لا توجد صلاحية إنترنت في هذا المشروع", 10f, secondaryText).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(footer)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        setContentView(scroll)
        promptCount.text = "${promptInput.text.length} / 1200 حرف"
    }

    private fun validateAndRun(size: String, sampler: String, seed: String) {
        val prompt = promptInput.text.toString().trim()
        val uri = modelUri
        val error = when {
            prompt.isBlank() -> "اكتب وصف الصورة أولاً."
            prompt.length > 1200 -> "الوصف طويل جداً؛ الحد 1200 حرف."
            uri == null -> "اختر ملف نموذج من الجهاز أولاً."
            seed.isNotBlank() && seed.toLongOrNull() == null -> "قيمة Seed يجب أن تكون رقماً صحيحاً."
            else -> null
        }
        if (error != null) { status.text = error; return }
        prefs.edit()
            .putString(KEY_PROMPT, prompt)
            .putString(KEY_NEGATIVE, negativeInput.text.toString())
            .putInt(KEY_STEPS, steps)
            .putFloat(KEY_CFG, cfg)
            .apply()
        checkButton.isEnabled = false
        status.text = "جارٍ فحص الإعدادات…"
        worker.execute {
            val report = "تم فحص المدخلات بنجاح.\nالأبعاد: $size\nSampler: $sampler\nSteps: $steps  ·  CFG: ${String.format(java.util.Locale.US, "%.1f", cfg)}\n\nلم يتم إنشاء صورة: محرك Stable Diffusion غير مدمج بعد."
            runOnUiThread {
                status.text = report
                checkButton.isEnabled = true
            }
        }
    }

    private fun chooseModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_MODEL)
    }

    private fun refreshModelLabel() {
        val uri = modelUri
        modelName.text = if (uri == null) "لم يتم اختيار نموذج" else queryDisplayName(uri)
    }

    private fun queryDisplayName(uri: Uri): String {
        var name: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        } catch (_: Exception) { }
        return name ?: (uri.lastPathSegment ?: "ملف نموذج محدد")
    }

    private fun restoreDraft() {
        if (!::promptInput.isInitialized) return
        promptInput.setText(prefs.getString(KEY_PROMPT, "") ?: "")
        negativeInput.setText(prefs.getString(KEY_NEGATIVE, "") ?: "")
        steps = prefs.getInt(KEY_STEPS, 24).coerceIn(5, 50)
        cfg = prefs.getFloat(KEY_CFG, 7f).coerceIn(2f, 15f)
        if (::stepsValue.isInitialized) stepsValue.text = steps.toString()
        if (::cfgValue.isInitialized) cfgValue.text = String.format(java.util.Locale.US, "%.1f", cfg)
        if (::stepsBar.isInitialized) stepsBar.progress = steps - 5
        if (::cfgBar.isInitialized) cfgBar.progress = ((cfg - 2f) * 2f).roundToInt().coerceIn(0, 26)
    }

    @Deprecated("Deprecated callback retained to avoid AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MODEL && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            modelUri = uri
            prefs.edit().putString(KEY_MODEL_URI, uri.toString()).apply()
            refreshModelLabel()
            status.text = "تم اختيار الملف. لم يتم تحميله أو التحقق من توافقه مع المحرك بعد."
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        modelUri?.let { outState.putString(KEY_MODEL_URI, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun makeCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(card, 20f)
    }

    private fun sectionTitle(title: String, meta: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(title, 17f, primaryText, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(meta, 9f, accent, true))
        return row
    }

    private fun valueHeader(title: String, tag: String): Pair<View, TextView> {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text("$title  ·  $tag", 13f, primaryText, true), LinearLayout.LayoutParams(0, -2, 1f))
        val value = text("", 13f, accent, true)
        row.addView(value)
        return row to value
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun label(value: String, dpTop: Int = 8) = text(value, 12f, secondaryText, true).apply {
        setPadding(0, dp(dpTop), 0, dp(7))
    }

    private fun editField(hintText: String, lines: Int) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.rgb(113, 132, 157))
        setTextColor(primaryText)
        textSize = 14f
        minLines = lines
        maxLines = if (lines == 1) 1 else lines + 2
        if (lines > 1) gravity = Gravity.TOP
        setPadding(dp(13), dp(12), dp(13), dp(12))
        background = rounded(Color.rgb(12, 21, 36), 13f, Color.rgb(43, 60, 82))
    }

    private fun smallAction(title: String) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 11f
        setTextColor(accent)
        minHeight = dp(38)
        minimumHeight = dp(38)
        background = rounded(Color.rgb(26, 51, 65), 12f)
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun textButton(title: String, action: () -> Unit) = TextView(this).apply {
        text = title
        textSize = 12f
        setTextColor(accent)
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        background = rounded(Color.rgb(12, 21, 36), 12f, Color.rgb(43, 60, 82))
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun matchWrap(bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = bottom }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChange(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    companion object {
        private const val REQUEST_MODEL = 101
        private const val KEY_MODEL_URI = "selected_model_uri"
        private const val KEY_PROMPT = "last_prompt"
        private const val KEY_NEGATIVE = "last_negative"
        private const val KEY_STEPS = "last_steps"
        private const val KEY_CFG = "last_cfg"
    }
}
