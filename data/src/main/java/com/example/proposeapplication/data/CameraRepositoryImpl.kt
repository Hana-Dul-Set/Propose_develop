package com.example.proposeapplication.data

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.lifecycle.LifecycleOwner
import com.example.proposeapplication.domain.repository.CameraRepository
import com.example.proposeapplication.utils.datasource.CameraDataSourceImpl
import com.example.proposeapplication.utils.datasource.FileHandleDataSourceImpl
import com.example.proposeapplication.utils.datasource.ImageProcessDataSourceImpl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraRepositoryImpl @Inject constructor(private val applicationContext: Context) :
    CameraRepository {

    private val cameraDataSource by lazy {
        CameraDataSourceImpl(applicationContext)
    }


    override fun initPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        ratio: AspectRatioStrategy,
        analyzer: ImageAnalysis.Analyzer,

    ) = cameraDataSource.showPreview(
        lifecycleOwner, surfaceProvider, ratio,analyzer
    )


    override suspend fun takePhoto(): Bitmap = cameraDataSource.takePhoto()


    override fun setZoomRatio(zoomLevel: Float) =
        cameraDataSource.setZoomLevel(zoomLevel)


//   suspend fun compositionData(bitmap: Bitmap): String {
//        try {
//            val URL = "https://regularly-enabled-perch.ngrok-free.app/guide/"
////             "http://172.16.101.36:8000/guide/"
//            val retrofit: Retrofit = Retrofit.Builder()
//                .baseUrl(URL).addConverterFactory(ScalarsConverterFactory.create())
//                .addConverterFactory(
//                    GsonConverterFactory.create()
//                ).build()
//            val retrofitService: RetrofitService = retrofit.create(RetrofitService::class.java)
//            val filesDir = applicationContext.filesDir
//            val file = File(filesDir, "targetImage.jpg")
//
//            file.outputStream().use { outputStream ->
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
//            }
//
//            val body = MultipartBody.Part.createFormData(
//                "image", "target.jpg",
//                RequestBody.create(MediaType.parse("multipart/form-data; image/*"), file)
//            )
//            val res = retrofitService.sendImage(body)
//            if (res.isSuccessful && res.body() != null) {
//                val data = JSONObject(res.body()!!).getString("direction")
//                Log.d("loadedData", data)
//                return data
//            } else {
//                Log.e("NetworkError", "Request was not successful or response body is null")
//            }
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Log.e("NetworkError", "Exception: ${e.message}")
//        }
//
//        return ""
//    }
}
