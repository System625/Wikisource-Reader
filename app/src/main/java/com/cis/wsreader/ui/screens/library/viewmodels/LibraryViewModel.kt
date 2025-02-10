/**
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cis.wsreader.ui.screens.library.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cis.wsreader.data.model.Book
import com.cis.wsreader.database.library.LibraryDao
import com.cis.wsreader.database.library.LibraryItem
import com.cis.wsreader.epub.EpubParser
import com.cis.wsreader.helpers.PreferenceUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.cis.wsreader.utils.EventChannel
import com.cis.wsreader.reader.OpeningError
import com.cis.wsreader.reader.ReaderActivityContract


@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val libraryDao: LibraryDao,
    private val epubParser: EpubParser,
    private val preferenceUtil: PreferenceUtil
) : AndroidViewModel(application) {

    private val app get() = getApplication<com.cis.wsreader.MyneApp>()
    val channel = EventChannel(Channel<Event>(Channel.BUFFERED), viewModelScope)

    val allItems: LiveData<List<Book>> = app.bookRepository.books()

    private val _showOnboardingTapTargets: MutableState<Boolean> = mutableStateOf(
        value = preferenceUtil.getBoolean(PreferenceUtil.LIBRARY_ONBOARDING_BOOL, true)
    )
    val showOnboardingTapTargets: State<Boolean> = _showOnboardingTapTargets

    fun deletePublication(book: Book) =
        viewModelScope.launch {
            app.bookshelf.deleteBook(book)
        }

    fun openPublication(
        bookId: Long,
    ) {
        viewModelScope.launch {
            app.readerRepository
                .open(bookId)
                .onFailure {
                    channel.send(Event.OpenPublicationError(it))
                }
                .onSuccess {
                    val arguments = ReaderActivityContract.Arguments(bookId)
                    channel.send(Event.LaunchReader(arguments))
                }
        }
    }

    /*
    Should enable in the future after fixes
    fun shouldShowLibraryTooltip(): Boolean {
        return preferenceUtil.getBoolean(PreferenceUtil.LIBRARY_SWIPE_TOOLTIP_BOOL, true)
                && allItems.value?.isNotEmpty() == true
                && allItems.value?.any { !it.isExternalBook } == true
    }

    fun libraryTooltipDismissed() = preferenceUtil.putBoolean(
        PreferenceUtil.LIBRARY_SWIPE_TOOLTIP_BOOL, false
    )*/

    fun onboardingComplete() {
        preferenceUtil.putBoolean(PreferenceUtil.LIBRARY_ONBOARDING_BOOL, false)
        _showOnboardingTapTargets.value = false
    }

    fun importBooks(
        context: Context,
        fileUris: List<Uri>,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                fileUris.forEach { uri ->
                    app.bookshelf.importPublicationFromStorage(uri)
                }

                // Add delay here so user can see the import progress bar even if
                // the import is very fast instead of just a flicker, improving UX
                delay(800)
            }

            withContext(Dispatchers.Main) {
                result.onSuccess {
                    onComplete()
                }.onFailure { exception ->
                    Log.e("LibraryViewModel", "Error importing book", exception)
                    onError(exception)
                }
            }
        }
    }

    sealed class Event {

        class OpenPublicationError(
            val error: OpeningError,
        ) : Event()

        class LaunchReader(
            val arguments: ReaderActivityContract.Arguments,
        ) : Event()
    }
}