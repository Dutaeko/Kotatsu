package org.koitharu.kotatsu.remotelist.ui

internal class RemoteListPaginationState {

	private var nextOffset = 0
	var hasNextPage = false
		private set

	fun getOffset(append: Boolean): Int {
		return if (append) nextOffset else 0
	}

	fun onPageLoaded(offset: Int, size: Int) {
		nextOffset = if (offset == 0) {
			size
		} else {
			offset + size
		}
		hasNextPage = size > 0
	}
}
