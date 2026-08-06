// Engineered by uncoalesced
package com.uncoalesced.stickykeys.debugharness

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Every field type the keyboard classifies, in one screen. Debug builds only.
 *
 * **Why this exists.** `fieldKindFor` is the password gate, and it had gone two releases
 * without ever running on a phone, because verifying it needs a field the keyboard will see as
 * a secret and the only ones on this device are the real lock credential and a Wi-Fi password
 * dialog. Neither is something to type test values into. This is the sanctioned alternative:
 * a throwaway harness with no storage, no network and no form to submit.
 *
 * **Why `src/debug` and not a flag.** The same reason `UsageRecorder` is split by source set
 * rather than gated on `BuildConfig`: a runtime boolean cannot make a class stop existing, and
 * R8 keeps anything still referenced. A test screen that ships to users in any form is worse
 * than no test screen. Living here, it is not compiled into the release variant at all.
 *
 * **Why `inputType` is set numerically rather than through Compose's `KeyboardType`.** The
 * thing under test reads `EditorInfo.inputType`, so the harness sets exactly that, with the
 * platform constants. Going through a `KeyboardType` mapping would put the framework's own
 * translation between the test and the code being tested, which is the layer most likely to
 * differ by version and the last place a surprise should be hiding.
 *
 * Launch: `adb shell am start -n com.uncoalesced.stickykeys/.debugharness.FieldTypeHarnessActivity`
 */
class FieldTypeHarnessActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PAD, PAD, PAD, PAD)
                setBackgroundColor(Color.BLACK)
            }

        FIELDS.forEach { field ->
            column.addView(label(field.label))
            column.addView(
                EditText(this).apply {
                    inputType = field.inputType
                    imeOptions = field.imeOptions
                    // Named so uiautomator can find the right field without depending on
                    // screen position, which shifts the moment the keyboard resizes.
                    contentDescription = field.id
                    hint = field.id
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.GRAY)
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                },
            )
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(column)
            },
        )
    }

    private fun label(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#7CAE7A"))
            gravity = Gravity.START
            setPadding(0, PAD, 0, 0)
        }

    private data class Field(
        val id: String,
        val label: String,
        val inputType: Int,
        val imeOptions: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    )

    private companion object {
        const val PAD = 24

        /**
         * The negative cases are the point as much as the positive ones. A gate that catches
         * every password and also catches an amount field silently kills suggestions on
         * ordinary typing, and nothing about that looks broken enough for anyone to report.
         */
        val FIELDS =
            listOf(
                Field(
                    "f1_text",
                    "1. plain text -- expect NORMAL, suggestions ON",
                    InputType.TYPE_CLASS_TEXT,
                ),
                Field(
                    "f2_password",
                    "2. TEXT_VARIATION_PASSWORD -- expect PASSWORD",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                ),
                Field(
                    "f3_visible_password",
                    "3. VISIBLE_PASSWORD -- expect PASSWORD",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                ),
                Field(
                    "f4_web_password",
                    "4. WEB_PASSWORD -- expect PASSWORD",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                ),
                Field(
                    "f5_pin",
                    "5. NUMBER_VARIATION_PASSWORD -- expect PIN, digit grid",
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                ),
                Field(
                    "f6_number",
                    "6. plain NUMBER -- expect NORMAL, NOT a PIN grid",
                    InputType.TYPE_CLASS_NUMBER,
                ),
                Field(
                    "f7_phone",
                    "7. PHONE -- expect NORMAL",
                    InputType.TYPE_CLASS_PHONE,
                ),
                Field(
                    "f8_email",
                    "8. EMAIL -- expect NORMAL",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                ),
                Field(
                    "f9_no_learning",
                    "9. plain text + NO_PERSONALIZED_LEARNING -- expect incognito lock",
                    InputType.TYPE_CLASS_TEXT,
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
                ),
                Field(
                    "f10_multiline",
                    "10. multi-line text -- Enter must insert a newline",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                ),
            )
    }
}
