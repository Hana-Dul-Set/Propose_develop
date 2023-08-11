package com.example.proposeapplication.domain.usecase.camera

import android.graphics.Bitmap
import com.example.proposeapplication.domain.repository.CameraRepository
import javax.inject.Inject

class ShowFixedScreenUseCase@Inject constructor(private val repository: CameraRepository) {
    suspend operator fun invoke(rawBitmap: Bitmap) =
        repository.getFixedScreen(rawBitmap)
}