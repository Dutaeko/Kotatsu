package org.koitharu.kotatsu.core.parser

import androidx.annotation.StringRes
import org.koitharu.kotatsu.R

enum class ParserProvider(
	@StringRes val titleResId: Int,
	val isActive: Boolean,
) {
	KOTATSU_REDO(R.string.parser_provider_kotatsu_redo, false),
	KOTOTORO(R.string.parser_provider_kototoro, false),
	YAKATEAM(R.string.parser_provider_yakateam, true),
}
