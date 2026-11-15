package com.wmods.wppenhacer.activities

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.databinding.ActivityAboutBinding

class AboutActivity : BaseActivity() {
    private val contributors = arrayOf(
        arrayOf("asHDEV", "https://t.me/ashrizki")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTelegram.setOnClickListener { openUrl("https://t.me/WaTrobosIndonesia") }
        binding.btnGithub.setOnClickListener { openUrl("https://github.com/asharizki/WaTrobos") }

        val contributorTopMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
        contributors.forEachIndexed { index, contributor ->
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = contributorTopMargin
            }
            val button =
                MaterialButton(ContextThemeWrapper(this, R.style.ModernButton_Outlined)).apply {
                    text = contributor[0]
                    setIconResource(R.drawable.ic_telegram)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = resources.getDimensionPixelSize(R.dimen.spacing_small)
                    layoutParams = params
                    if (contributor[1].isNotEmpty()) {
                        setOnClickListener { openUrl(contributor[1]) }
                    } else {
                        isClickable = false
                    }
                }
            binding.contributorsContainer.addView(button)
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
