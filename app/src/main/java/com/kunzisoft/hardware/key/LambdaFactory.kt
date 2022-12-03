package com.kunzisoft.hardware.key

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class LambdaFactory<T : ViewModel>(
    private val create: () -> T
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return create.invoke() as T
    }
}
