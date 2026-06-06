package com.example.updatedchessmint.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.updatedchessmint.data.DataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun uiState_initiallyLoading() = runTest {
    val owner = MainScreenViewModelOwner(NeverEmittingRepository())
    try {
      assertEquals(MainScreenUiState.Loading, owner.viewModel.uiState.first())
    } finally {
      owner.clear()
    }
  }

  @Test
  fun uiState_dataLoaded_isDisplayed() = runTest {
    val owner = MainScreenViewModelOwner(FakeMyModelRepository())
    try {
      assertEquals(
        MainScreenUiState.Success(listOf("Sample")),
        owner.viewModel.uiState
          .dropWhile { it == MainScreenUiState.Loading }
          .first(),
      )
    } finally {
      owner.clear()
    }
  }
}

private class FakeMyModelRepository : DataRepository {
  override val data: Flow<List<String>> = flow { emit(listOf("Sample")) }
}

private class NeverEmittingRepository : DataRepository {
  override val data: Flow<List<String>> = emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
  private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
  override fun starting(description: Description) {
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}

private class MainScreenViewModelOwner(repository: DataRepository) {
  private val store = ViewModelStore()

  val viewModel: MainScreenViewModel =
    ViewModelProvider(
      store,
      MainScreenViewModelFactory(repository),
    )[MainScreenViewModel::class.java]

  fun clear() {
    store.clear()
  }
}

private class MainScreenViewModelFactory(
  private val repository: DataRepository,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T = createViewModel(modelClass)

  override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
    createViewModel(modelClass)

  @Suppress("UNCHECKED_CAST")
  private fun <T : ViewModel> createViewModel(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(MainScreenViewModel::class.java)) {
      return MainScreenViewModel(repository) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
