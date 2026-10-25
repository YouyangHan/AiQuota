package com.hanyo.apiquota.widget

import androidx.annotation.DrawableRes
import com.hanyo.apiquota.R

/** 平台 -> 品牌图标。 */
object WidgetIcons {
    @DrawableRes
    fun drawableFor(providerId: String): Int? = when (providerId) {
        "deepseek" -> R.drawable.ic_brand_deepseek
        "kimi" -> R.drawable.ic_brand_kimi
        "kimicode" -> R.drawable.ic_brand_kimicode
        "glm" -> R.drawable.ic_brand_glm
        else -> null
    }
}
