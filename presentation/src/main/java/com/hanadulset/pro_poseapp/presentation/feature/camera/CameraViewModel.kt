package com.hanadulset.pro_poseapp.presentation.feature.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.AspectRatio
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanadulset.pro_poseapp.domain.usecase.GetPoseFromImageUseCase
import com.hanadulset.pro_poseapp.domain.usecase.PreLoadModelUseCase
import com.hanadulset.pro_poseapp.domain.usecase.ai.CheckForDownloadModelUseCase
import com.hanadulset.pro_poseapp.domain.usecase.ai.DownloadModelUseCase
import com.hanadulset.pro_poseapp.domain.usecase.ai.RecommendCompInfoUseCase
import com.hanadulset.pro_poseapp.domain.usecase.ai.RecommendPoseUseCase
import com.hanadulset.pro_poseapp.domain.usecase.camera.CaptureImageUseCase
import com.hanadulset.pro_poseapp.domain.usecase.camera.GetLatestImageUseCase
import com.hanadulset.pro_poseapp.domain.usecase.camera.SetZoomLevelUseCase
import com.hanadulset.pro_poseapp.domain.usecase.camera.ShowFixedScreenUseCase
import com.hanadulset.pro_poseapp.domain.usecase.camera.ShowPreviewUseCase
import com.hanadulset.pro_poseapp.utils.DownloadInfo
import com.hanadulset.pro_poseapp.utils.camera.CameraState
import com.hanadulset.pro_poseapp.utils.pose.PoseData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ExperimentalGetImage
@HiltViewModel
class CameraViewModel @Inject constructor(
    //UseCases
    private val checkForDownloadModelUseCase: CheckForDownloadModelUseCase,
    private val downloadModelUseCase: DownloadModelUseCase,
    private val showPreviewUseCase: ShowPreviewUseCase,
    private val captureImageUseCase: CaptureImageUseCase,
    private val showFixedScreenUseCase: ShowFixedScreenUseCase,
    private val setZoomLevelUseCase: SetZoomLevelUseCase,
    private val recommendCompInfoUseCase: RecommendCompInfoUseCase,
    private val recommendPoseUseCase: RecommendPoseUseCase,
    private val getLatestImageUseCase: GetLatestImageUseCase,
    private val preLoadModelUseCase: PreLoadModelUseCase,
    private val getPoseFromImageUseCase: GetPoseFromImageUseCase

) : ViewModel() {


    private val reqPoseState = MutableStateFlow(false)// 포즈 추천 요청 on/off
    private val reqCompState = MutableStateFlow(false) // 구도 추천 요청 on/off

    private val viewRateList = listOf(
        Pair(AspectRatio.RATIO_4_3, 3 / 4F),
        Pair(AspectRatio.RATIO_16_9, 9 / 16F)
    )

    private val _previewState = MutableStateFlow(CameraState(CameraState.CAMERA_INIT_NOTHING))

    private val _viewRateIdxState = MutableStateFlow(0)
    private val _viewRateState = MutableStateFlow(viewRateList[0].first)
    private val _aspectRatioState = MutableStateFlow(viewRateList[0].second)


    private val _capturedBitmapState = MutableStateFlow<Uri?>( //캡쳐된 이미지 상태
//        Bitmap.createBitmap(
//            100, 100, Bitmap.Config.ARGB_8888
//        )
        null
    )
    private val _poseResultState = MutableStateFlow<Pair<DoubleArray?, List<PoseData>?>?>(null)
    private val _compResultState = MutableStateFlow("")
    private val _downloadInfoState = MutableStateFlow(DownloadInfo())
    private val _fixedScreenState = MutableStateFlow<Bitmap?>(null)


    //State Getter

    val capturedBitmapState = _capturedBitmapState.asStateFlow()
    val poseResultState = _poseResultState.asStateFlow()
    val compResultState = _compResultState.asStateFlow()
    val downloadInfoState = _downloadInfoState.asStateFlow()
    val viewRateIdxState = _viewRateIdxState.asStateFlow()
    val aspectRatioState = _aspectRatioState.asStateFlow()
    val viewRateState = _viewRateState.asStateFlow()
    val fixedScreenState = _fixedScreenState.asStateFlow()
    val previewState = _previewState.asStateFlow()

    val testOBject = MutableStateFlow("")

    //매 프레임의 image를 수신함.
    private val imageAnalyzer = ImageAnalysis.Analyzer {
        it.use { image ->
            //포즈 선정 로직
            if (reqPoseState.value) {
                reqPoseState.value = false
                _poseResultState.value = null
                viewModelScope.launch {
                    _poseResultState.value = recommendPoseUseCase(
                        image = image.image!!,
                        rotation = image.imageInfo.rotationDegrees
                    )
                }

            }
            //구도 추천 로직
            if (reqCompState.value) {
                reqCompState.value = false
                viewModelScope.launch {
                    _compResultState.value = ""
                    _compResultState.value = recommendCompInfoUseCase(
                        image = image.image!!,
                        rotation = image.imageInfo.rotationDegrees
                    )
                }
            }
        }
    }


    private fun getImageFromAnalyzer() {

    }


    fun showPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int
    ) {
        _previewState.value =
            _previewState.value.copy(cameraStateId = CameraState.CAMERA_INIT_ON_PROCESS) // OnProgress
        viewModelScope.launch {
            _previewState.value = showPreviewUseCase(
                lifecycleOwner, surfaceProvider,
                aspectRatio = aspectRatio,
                analyzer = imageAnalyzer,
                previewRotation = previewRotation
            )
        }
    }

    fun getPhoto(
    ) {
        viewModelScope.launch {
            _capturedBitmapState.value = captureImageUseCase()
        }
    }

    fun reqPoseRecommend() {
        if (reqPoseState.value.not()) reqPoseState.value = true
    }


    fun reqCompRecommend() {
        if (reqCompState.value.not()) reqCompState.value = true
    }

    fun setZoomLevel(zoomLevel: Float) = setZoomLevelUseCase(zoomLevel)

    fun changeViewRate(idx: Int) {
        _viewRateIdxState.value = idx
        _viewRateState.value = viewRateList[idx].first
        _aspectRatioState.value = viewRateList[idx].second
    }

    fun testS3() {
        testOBject.value = ""
        viewModelScope.launch {
            if (preLoadModelUseCase()) testOBject.value =
                checkForDownloadModelUseCase(_downloadInfoState)
        }
    }

    fun checkForDownloadModel() {
        viewModelScope.launch {
            _downloadInfoState.value = DownloadInfo(state = DownloadInfo.ON_REQUEST)
            checkForDownloadModelUseCase(_downloadInfoState)
        }
    }

    fun reqDownloadModel() {
        viewModelScope.launch {
            _downloadInfoState.value = DownloadInfo(state = DownloadInfo.ON_REQUEST)
            downloadModelUseCase(_downloadInfoState)
        }
    }

    fun controlFixedScreen(isRequest: Boolean) {
        if (isRequest) {
            _fixedScreenState.value = null
            viewModelScope.launch {
                _fixedScreenState.value = showFixedScreenUseCase()
            }
        } else _fixedScreenState.value = null
    }

    fun getPoseFromImage(uri: Uri?) {
        _fixedScreenState.value = null
        val res = getPoseFromImageUseCase(uri)
        _fixedScreenState.value = res
    }


    //최근 이미지
    fun lastImage() = getLatestImageUseCase()

}