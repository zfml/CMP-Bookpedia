@file:OptIn(FlowPreview::class)

package com.plcoding.bookpedia.book.presentation.book_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plcoding.bookpedia.book.domain.Book
import com.plcoding.bookpedia.book.domain.BookRepository
import com.plcoding.bookpedia.core.domain.Result
import com.plcoding.bookpedia.core.domain.onError
import com.plcoding.bookpedia.core.domain.onSuccess
import com.plcoding.bookpedia.core.presentation.UiText
import com.plcoding.bookpedia.core.presentation.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class BookListViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("Kotlin")
    private val _selectedTabIndex = MutableStateFlow(0)

    private val favoriteBooks = bookRepository.getFavoriteBooks()

    private var cachedBooks: List<Book> = emptyList()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults = _searchQuery
        .debounce(400L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            when {
                query.isBlank() -> flow{ emit(Result.Success(cachedBooks))}
                query.length < 2 -> flow { emit(Result.Success(cachedBooks)) }
                else -> flow {
                    bookRepository.searchBooks(query)
                        .onSuccess { searchResults ->
                            cachedBooks = searchResults
                            emit(Result.Success(searchResults))
                        }
                        .onError { error ->
                            emit(Result.Error(error))
                        }

                }
            }
        }

    val state = combine(
        _searchQuery,
        _selectedTabIndex,
        favoriteBooks,
        searchResults
    ) { query, tab, favorites, searchResult ->
        var errorMessage: UiText? = null
        var searchList: List<Book> = emptyList()
        var isLoading = false

        when(searchResult){
            is Result.Error -> {
                errorMessage = searchResult.error.toUiText()
            }
            is Result.Success -> {
                searchList = searchResult.data
            }
        }

        BookListState(
            searchQuery = query,
            selectedTabIndex = tab,
            favoriteBooks = favorites,
            searchResults = searchList,
            errorMessage = errorMessage,
            isLoading = isLoading
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        BookListState()
    )

    fun onAction(action: BookListAction) {
        when (action) {
            is BookListAction.OnSearchQueryChange -> _searchQuery.value = action.query
            is BookListAction.OnTabSelected -> _selectedTabIndex.value = action.index
            is BookListAction.OnBookClick -> { }
        }
    }
}
