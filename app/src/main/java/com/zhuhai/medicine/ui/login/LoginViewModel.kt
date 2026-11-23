package com.zhuhai.medicine.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuhai.medicine.data.remote.ApiResult
import com.zhuhai.medicine.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** 一次性信号：导航完成后由 UI 调 consumeLoginSuccess 复位，防止重复跳转 */
    val loginSucceeded: Boolean = false
)

class LoginViewModel @JvmOverloads constructor(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun login() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val username = current.username.trim()
        if (username.isEmpty() || current.password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入用户名和密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            when (val result = repository.login(username, current.password)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isSubmitting = false, loginSucceeded = true)
                }

                is ApiResult.BusinessError -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }

                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }

                ApiResult.Unauthorized -> _uiState.update {
                    it.copy(isSubmitting = false, errorMessage = "用户名或密码错误")
                }
            }
        }
    }

    fun consumeLoginSuccess() {
        _uiState.update { it.copy(loginSucceeded = false) }
    }
}
