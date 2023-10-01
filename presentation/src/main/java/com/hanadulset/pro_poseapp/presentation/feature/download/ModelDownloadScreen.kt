package com.hanadulset.pro_poseapp.presentation.feature.download

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraViewModel
import com.hanadulset.pro_poseapp.presentation.R
import com.hanadulset.pro_poseapp.presentation.core.CustomDialog
import com.hanadulset.pro_poseapp.presentation.feature.splash.PrepareServiceViewModel
import com.hanadulset.pro_poseapp.utils.DownloadInfo
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

object ModelDownloadScreen {

    /**
     *  _화면 흐름 방식_
     *
     * 1. _[ModelDownloadProgressScreen]_ : 다운로드 할 모델이 있는 경우, 모델을 다운로드 해야한다고 알려줌.
     *
     *      -> **이 때 용량이 어느 정도 인지 사용자에게 알려줘야 할 거 같음.**
     * 2. _[DownloadStateModal]_ : 사용자가 모델을 다운로드 하겠다는 경우, 다운로드를 진행하는 모달창을 만들고,
     *
     *      사용자가 다운로드를 취소한다면, 앱을 종료한다.
     *
     * */

    val pretendardFamily = FontFamily(
        Font(R.font.pretendard_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.pretendard_light, FontWeight.Light, FontStyle.Normal),
    )
    private val mainStyle = TextStyle(
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        fontFamily = pretendardFamily
    )
    private val subStyle = TextStyle(
        lineHeight = 10.sp,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        fontFamily = pretendardFamily
    )

    //모델 다운로드가 진행될 화면
    @Composable
    fun ModelDownloadRequestScreen(
        prepareServiceViewModel: PrepareServiceViewModel,
        moveToPerm: () -> Unit,
        moveToDownloadProgress: () -> Unit
    ) {
        val downloadInfoState by prepareServiceViewModel.downloadState.collectAsState()
        val context = LocalContext.current





        // 받을 리소스가  있는 경우, 다운로드 창을 사용자에게 보여줌.
        if (downloadInfoState.state in listOf(DownloadInfo.ON_UPDATE, DownloadInfo.ON_DOWNLOAD)) {
            prepareServiceViewModel.requestToDownload()
            val isDownload = (downloadInfoState.state == DownloadInfo.ON_DOWNLOAD)
            CustomDialog.DownloadAlertDialog(
                totalSize = downloadInfoState.byteTotal,
                onDismissRequest = {
                    if (isDownload) (context as Activity).finish()
                    else moveToPerm()
                },
                onConfirmRequest = {
                    moveToDownloadProgress()
                }
            )
        }
    }

    //상태에 따라 화면을 변경하도록 하자.

    @Composable
    fun ModelDownloadProgressScreen(
        downloadProcess: DownloadInfo,
        onDismissEvent: () -> Unit
    ) {


        Surface(
            modifier = Modifier
                .width(270.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "리소스 다운로드", style = mainStyle
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "모델 다운로드 중... ( ${downloadProcess.nowIndex + 1} / ${downloadProcess.totalLength} )",
                    style = subStyle
                )
                Spacer(modifier = Modifier.height(20.dp))
                CustomLinearProgressBar(
                    modifier = Modifier
                        .width(245.dp)
                        .height(15.dp)
                        .align(Alignment.CenterHorizontally),
                    progress = downloadProcess.let {
                        ((it.byteCurrent / 1e+6) / (it.byteTotal / 1e+6)).toFloat()
                    },

                    )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    text =
                    downloadProcess.let {
                        val byteC = (it.byteCurrent / 1e+6).roundToLong()
                        val byteT = (it.byteTotal / 1e+6).roundToLong()
                        "$byteC MB / $byteT MB"
                    }, style = subStyle
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFFFFAFAF),
                            shape = RoundedCornerShape(size = 12.dp)
                        )
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable(
                            indication = null, //Ripple 효과 제거
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ) {
                            onDismissEvent()
                        }

                ) {
                    Text(
                        text = "앱 종료",
                        modifier = Modifier.align(Alignment.Center), style = TextStyle(
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = pretendardFamily
                        )
                    )
                }

            }
        }
    }


    @Composable
    fun DownloadStateModal() {

    }

    @Composable
    private fun CustomLinearProgressBar(
        modifier: Modifier = Modifier,
        progress: Float,
        backgroundColor: Color = Color(0xFFF0F0F0),
        color: Color = Color(0xFF95FFA7),
    ) {
        Canvas(
            modifier
                .progressSemantics(progress)
        ) {
            val strokeWidth = size.height
            drawLinearIndicatorBackground(backgroundColor, strokeWidth)
            drawLinearIndicator(0f, progress, color, strokeWidth)
        }


    }

    private fun DrawScope.drawLinearIndicatorBackground(
        color: Color,
        strokeWidth: Float
    ) = drawLinearIndicator(0f, 1f, color, strokeWidth)

    private fun DrawScope.drawLinearIndicator(
        startFraction: Float,
        endFraction: Float,
        color: Color,
        strokeWidth: Float
    ) {
        val width = size.width
        val height = size.height
        // Start drawing from the vertical center of the stroke
        val yOffset = height / 2

        val isLtr = layoutDirection == LayoutDirection.Ltr
        val barStart = (if (isLtr) startFraction else 1f - endFraction) * width
        val barEnd = (if (isLtr) endFraction else 1f - startFraction) * width

        // Progress line
        drawLine(
            color, Offset(barStart, yOffset),
            Offset(barEnd, yOffset), strokeWidth,
            cap = StrokeCap.Round

        )
    }


}

@Preview(widthDp = 300, heightDp = 300, showSystemUi = true)
@Composable
private fun TestRequestModal() {
    val state = remember {
        mutableStateOf(
            DownloadInfo(
                state = DownloadInfo.ON_DOWNLOAD, 0, 1, 0,
                10000000
            )
        )
    }
    LaunchedEffect(key1 = Unit) {
        for (k in 0 until 3) {
            state.value = state.value.copy(nowIndex = k, totalLength = 3)
            for (i in 0..10000000 step (100000)) {
                delay(100)
                state.value = state.value.copy(byteCurrent = i.toLong())
            }
        }
        state.value = state.value.copy(state = DownloadInfo.ON_COMPLETE)

    }

    ModelDownloadScreen.ModelDownloadProgressScreen(
        state.value
    ) {}


}