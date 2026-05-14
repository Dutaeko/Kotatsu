package org.koitharu.kotatsu.tracker.domain

import coil3.request.CachePolicy
import org.koitharu.kotatsu.core.model.getPreferredBranch
import org.koitharu.kotatsu.core.model.isLocal
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.util.MultiMutex
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.toInstantOrNull
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.local.data.LocalMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.util.findById
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.tracker.domain.model.MangaTracking
import org.koitharu.kotatsu.tracker.domain.model.MangaUpdates
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckNewChaptersUseCase @Inject constructor(
	private val repository: TrackingRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val localMangaRepository: LocalMangaRepository,
) {

	private val mutex = MultiMutex<Long>()

	suspend operator fun invoke(manga: Manga): MangaUpdates = mutex.withLock(manga.id) {
		repository.updateTracks()
		val tracking = repository.getTrackOrNull(manga) ?: return@withLock MangaUpdates.Failure(
			manga = manga,
			error = null,
		)
		invokeImpl(tracking)
	}

	suspend operator fun invoke(track: MangaTracking): MangaUpdates = mutex.withLock(track.manga.id) {
		invokeImpl(track)
	}

	suspend operator fun invoke(manga: Manga, currentChapterId: Long) = mutex.withLock(manga.id) {
		runCatchingCancellable {
			repository.updateTracks()
			val details = getFullManga(manga)
			val track = repository.getTrackOrNull(manga) ?: return@withLock
			val branch = checkNotNull(details.chapters?.findById(currentChapterId)).branch
			val chapters = details.getChapters(branch)
			val chapterIndex = chapters.indexOfFirst { x -> x.id == currentChapterId }
			val lastNewChapterIndex = chapters.size - track.newChapters
			val lastChapter = chapters.lastOrNull()
			val tracking = MangaTracking(
				manga = details,
				lastChapterId = lastChapter?.id ?: 0L,
				lastCheck = Instant.now(),
				lastChapterDate = lastChapter?.uploadDate?.toInstantOrNull() ?: track.lastChapterDate,
				newChapters = when {
					track.newChapters == 0 -> 0
					chapterIndex < 0 -> track.newChapters
					chapterIndex >= lastNewChapterIndex -> chapters.lastIndex - chapterIndex
					else -> track.newChapters
				},
			)
			repository.mergeWith(tracking)
		}.onFailure { e ->
			e.printStackTraceDebug()
		}.isSuccess
	}

	private suspend fun invokeImpl(track: MangaTracking): MangaUpdates = runCatchingCancellable {
		val details = getFullManga(track.manga)
		compare(track, details, getBranch(details, track.lastChapterId))
	}.getOrElse { error ->
		MangaUpdates.Failure(
			manga = track.manga,
			error = error,
		)
	}.also { updates ->
		repository.saveUpdates(updates)
	}

	private suspend fun getBranch(manga: Manga, trackChapterId: Long): String? {
		historyRepository.getOne(manga)?.let {
			manga.chapters?.findById(it.chapterId)
		}?.let {
			return it.branch
		}
		manga.chapters?.findById(trackChapterId)?.let {
			return it.branch
		}
		return manga.getPreferredBranch(null)
	}

	private suspend fun getFullManga(manga: Manga): Manga = if (manga.isLocal) {
		fetchDetails(
			requireNotNull(localMangaRepository.getRemoteManga(manga)) {
				"Local manga is not supported"
			},
		)
	} else {
		fetchDetails(manga)
	}

	private suspend fun fetchDetails(manga: Manga): Manga {
		val repo = mangaRepositoryFactory.create(manga.source)
		return if (repo is CachingMangaRepository) {
			repo.getDetails(manga, CachePolicy.WRITE_ONLY)
		} else {
			repo.getDetails(manga)
		}
	}

	private fun compare(track: MangaTracking, manga: Manga, branch: String?): MangaUpdates.Success {
		val chapters = requireNotNull(manga.getChapters(branch))
		if (track.isEmpty()) {
			return MangaUpdates.Success(manga, branch, emptyList(), isValid = false)
		}
		val newChapters = chapters.takeLastWhile { x -> x.id != track.lastChapterId }
		return when {
			newChapters.isEmpty() -> {
				MangaUpdates.Success(
					manga = manga,
					branch = branch,
					newChapters = emptyList(),
					isValid = chapters.lastOrNull()?.id == track.lastChapterId,
				)
			}

			newChapters.size == chapters.size -> compareByChapterDate(track, manga, branch, chapters)

			else -> MangaUpdates.Success(manga, branch, newChapters, isValid = true)
		}
	}

	private fun compareByChapterDate(
		track: MangaTracking,
		manga: Manga,
		branch: String?,
		chapters: List<MangaChapter>,
	): MangaUpdates.Success {
		val lastChapterDate = track.lastChapterDate?.toEpochMilli() ?: 0L
		if (lastChapterDate <= 0L) {
			return MangaUpdates.Success(manga, branch, emptyList(), isValid = false)
		}
		val newChapters = chapters.takeLastWhile { x -> x.uploadDate > lastChapterDate }
		return if (newChapters.isEmpty()) {
			MangaUpdates.Success(manga, branch, emptyList(), isValid = true)
		} else {
			MangaUpdates.Success(manga, branch, newChapters, isValid = true)
		}
	}
}
