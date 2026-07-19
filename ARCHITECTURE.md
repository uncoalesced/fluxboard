# Sticky Keys Architecture

This document outlines the core architectural conventions used across the Sticky Keys repository. Adhering to these patterns ensures a consistent, predictable, and maintainable codebase.

## 1. MVVM & Unidirectional Data Flow (UDF)

The application follows the **Model-View-ViewModel (MVVM)** pattern combined with **Unidirectional Data Flow (UDF)**. 

### The `UiState` Convention
- **One State Object per Screen**: Every UI screen corresponds to exactly one `ViewModel`.
- **Sealed Interfaces**: The state exposed by the `ViewModel` is typically a `sealed interface` (e.g., `StickersUiState`) to explicitly represent mutually exclusive states (like `Loading`, `Error`, and `Success`).
- **State flows down, events flow up**: 
  - The UI (Compose) strictly *observes* the state flowing down from the `ViewModel` via `StateFlow`.
  - The UI communicates user actions to the `ViewModel` via explicit event callbacks or an event sealed class (e.g., `StickersUiEvent`).

### The `ViewModel` Rules
- **No Android Framework References**: ViewModels must never hold references to Android framework classes (no `Context`, no `View`, no `Activity`). This keeps them purely logical and testable.
- **Dependency Injection**: Use `@HiltViewModel` to inject dependencies (like Repositories or UseCases) into the constructor.

```kotlin
// Example Convention
sealed interface ExampleUiState {
    data object Loading : ExampleUiState
    data class Error(val message: String) : ExampleUiState
    data class Success(val data: List<String>) : ExampleUiState
}

@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val repository: ExampleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExampleUiState>(ExampleUiState.Loading)
    val uiState: StateFlow<ExampleUiState> = _uiState.asStateFlow()

    fun onEvent(event: ExampleUiEvent) {
        // Handle UI events and mutate _uiState
    }
}
```

## 2. Navigation
Routing is handled exclusively via **Navigation Compose** (`androidx.navigation:navigation-compose`). A centralized `AppNavGraph` orchestrates top-level navigation, and individual nested graphs handle complex sub-flows. Do not hand-roll custom routers.

## 3. Dependency Injection
**Hilt** is the standard DI framework. 
- Use `@AndroidEntryPoint` on the `MainActivity`.
- Place Hilt modules inside the relevant subsystem module (e.g., `sticker-core`, `keyboard-core`).
