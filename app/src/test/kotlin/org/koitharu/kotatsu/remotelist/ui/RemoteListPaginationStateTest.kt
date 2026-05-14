package org.koitharu.kotatsu.remotelist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteListPaginationStateTest {

	@Test
	fun firstPageStartsAtZero() {
		val state = RemoteListPaginationState()

		assertEquals(0, state.getOffset(false))
	}

	@Test
	fun nextOffsetUsesLoadedItemCount() {
		val state = RemoteListPaginationState()

		state.onPageLoaded(offset = 0, size = 24)

		assertEquals(24, state.getOffset(true))
		assertTrue(state.hasNextPage)
	}

	@Test
	fun pagedSourceCanContinueAfterSecondPage() {
		val state = RemoteListPaginationState()

		state.onPageLoaded(offset = 0, size = 24)
		state.onPageLoaded(offset = state.getOffset(true), size = 24)

		assertEquals(48, state.getOffset(true))
		assertTrue(state.hasNextPage)
	}

	@Test
	fun singlePageSourceStopsOnEmptyAppend() {
		val state = RemoteListPaginationState()

		state.onPageLoaded(offset = 0, size = 24)
		state.onPageLoaded(offset = state.getOffset(true), size = 0)

		assertEquals(24, state.getOffset(true))
		assertFalse(state.hasNextPage)
	}
}
