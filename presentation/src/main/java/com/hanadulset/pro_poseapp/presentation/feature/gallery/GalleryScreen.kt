package com.hanadulset.pro_poseapp.presentation.feature.gallery

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hanadulset.pro_poseapp.presentation.R
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraScreenButtons
import com.hanadulset.pro_poseapp.utils.camera.ImageResult

object GalleryScreen {
    //최근 찍힌 이미지들을 목록으로 보여준다.
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun GalleryScreen(
        imageList: List<ImageResult>,
        onLoadImages: () -> Unit,
        onDeleteImage: (Int, () -> Unit) -> Unit,
        onBackPressed: () -> Unit
    ) {
        //변수 목록
        val showMenuBarState =
            remember { mutableStateOf(true) } //삭제 및 공유 버튼 , 갤러리 메뉴 버튼을 볼 수 있도록 하는 뷰보이기
        val updatedImageList by rememberUpdatedState(newValue = imageList) //새로이 가져온 이미지 데이터
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val galleryOpenLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = {

            }) //갤러리를 열 때 사용하는 런처

        val horizontalPagerState = rememberPagerState(
            initialPage = 0,
            initialPageOffsetFraction = 0F,
            pageCount = { updatedImageList.size }) //페이저의 상태를 가지고 있는 변수
        val localDensity = LocalDensity.current
        val fontSize = 20.sp


        //갤러리 화면 틀 구성
        Box( //Parent
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {


            //Child 2
            //사진이 보이는 화면
            HorizontalPager(
                state = horizontalPagerState, modifier = Modifier
                    .fillMaxSize(), pageSpacing = 0.dp
            ) {

                ImageContent(
                    modifier = Modifier
                        .fillMaxSize(),
                    imgUri = if (imageList.isNotEmpty()) imageList[it].dataUri!! else null,
                    imgSize = screenWidth,
                    onClickEvent = {
                        showMenuBarState.value = showMenuBarState.value.not()
                    }
                )


            }
            //Child 1
            //만약 메뉴화면이 존재해야 하는 경우
            AnimatedVisibility(
                visible = showMenuBarState.value,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                //UpperBar -> 상단바 (뒤로가기, 현재 사진 수 ,갤러리 화면)
                Box(modifier = Modifier.fillMaxSize()) {

                    Row(
                        modifier = Modifier
                            .background(Color.Black)
                            .fillMaxWidth()
                            .padding(top = 40.dp, start = 20.dp, end = 20.dp)
                            .align(Alignment.TopCenter)
                            .height(screenHeight / 8),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                onBackPressed()
                            },
                            modifier = Modifier
                                .padding(end = 30.dp)
                                .size(with(localDensity) { fontSize.toDp() })

                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.left_arrow),
                                contentDescription = "뒤로가기",
                                tint = Color.White
                            )
                        }
                        Text(
                            modifier = Modifier
                                .wrapContentSize(align = Alignment.Center),
                            text = if (updatedImageList.isEmpty()) "이미지 없음" else "${horizontalPagerState.currentPage + 1}/${updatedImageList.size}",
                            fontSize = fontSize,
                            fontFamily = CameraScreenButtons.pretendardFamily,
                            fontWeight = FontWeight.Light,
                            color = Color.White
                        )
                        Text(
                            modifier = Modifier
                                .wrapContentSize()
                                .clickable {
                                    openGallery(galleryOpenLauncher)
                                },
                            text = "갤러리",
                            fontSize = fontSize,
                            fontFamily = CameraScreenButtons.pretendardFamily,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                    }



                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(screenHeight / 5)
                            .navigationBarsPadding()
                            .background(Color.Black),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (imageList.isNotEmpty()) {
                                    //삭제하시겠습니까? 다이얼로그 보여주기
                                    onDeleteImage(horizontalPagerState.currentPage) {
                                        onLoadImages()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp),
                        ) {
                            Icon(
                                tint = Color.White,
                                painter = painterResource(id = R.drawable.icon_delete),
                                contentDescription = "지우기"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openGallery(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {

        val intent = Intent()
        intent.action = Intent.ACTION_VIEW

        intent.setDataAndType(
            Uri.Builder()
                .path(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.path + "/Pictures/Pro_Pose/")
                .build(),
            "image/*",

            )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        launcher.launch(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            intent
        )
    }

    @Composable
    private fun ImageContent(
        modifier: Modifier,
        imgUri: Uri?,
        imgSize: Dp,
        onClickEvent: () -> Unit,
    ) {
        val imagePainter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imgUri.run { this ?: R.drawable.impossible_icon })
                .size(with(LocalDensity.current) { imgSize.toPx().toInt() }) //뷰 사이즈의 크기 만큼 이미지 리사이징
                .build()
        )
        Image(
            modifier = modifier.clickable(
                indication = null,
                interactionSource = MutableInteractionSource()
            ) {
                onClickEvent()

            },
            painter = imagePainter,
            contentScale = ContentScale.Fit,
            contentDescription = "저장된 이미지",
            alignment = Alignment.Center,
        )
    }


}


@Preview
@Composable
fun Test() {
    GalleryScreen.GalleryScreen(
        imageList = listOf(ImageResult(), ImageResult(), ImageResult(), ImageResult()),
        onLoadImages = { },
        onDeleteImage = { index, func ->

        },
        onBackPressed = {}
    )
}