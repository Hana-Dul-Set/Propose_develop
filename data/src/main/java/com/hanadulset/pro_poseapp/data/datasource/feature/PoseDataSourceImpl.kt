package com.hanadulset.pro_poseapp.data.datasource.feature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.hanadulset.pro_poseapp.data.datasource.ModelRunnerImpl
import com.hanadulset.pro_poseapp.utils.R
import com.hanadulset.pro_poseapp.data.datasource.interfaces.PoseDataSource
import com.hanadulset.pro_poseapp.utils.BuildConfig
import com.hanadulset.pro_poseapp.utils.pose.PoseData
import com.hanadulset.pro_poseapp.utils.pose.YoloPredictResult
import com.opencsv.CSVReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Arrays
import java.util.stream.Collectors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

class PoseDataSourceImpl(private val context: Context, private val modelRunner: ModelRunnerImpl) :
    PoseDataSource {


    //팔레트
    private val palette by lazy {
        context.assets.open("color_palette.json").use { `is` ->
            val str = BufferedReader(InputStreamReader(`is`)).lines()
                .collect(Collectors.joining())
            str.replace(" ", "").let { input ->
                val cleanedInput = input
                    .replace("[[", "[")
                    .replace("],[", "]@[")
                    .replace("]]", "]")
                val stringArrays = cleanedInput.split("@").toTypedArray()

                val array = Array(stringArrays.size) { idx ->
                    stringArrays[idx]
                        .removeSurrounding("[", "]")
                        .split(",")
                        .map { it.toInt() }
                        .toTypedArray()
                }
                array
            }
        }
    }

    //centroid 값
    private val centroid by lazy {
        context.assets.open("centroids.csv").use { stream ->
            val resMutableList = mutableListOf<List<Double>>()
            CSVReader(InputStreamReader(stream)).forEach {
                //앞에 라벨 번호가 있는 것들만 데이터를 가져와보자
                if (!it.contains("label")) {
                    val tmpList = mutableListOf<Double>()
                    val length = it.size
                    tmpList.add(it[1].substring(1).toDouble())
                    for (i in 2 until length - 1) tmpList.add(it[i].toDouble())
                    tmpList.add(
                        it[length - 1].substring(0 until it[length - 1].length - 1).toDouble()
                    )
                    resMutableList.add(tmpList)
                }
            }
            resMutableList
        }
    }
    private val poseRanks by lazy {
        val poseDataList = mutableListOf<List<PoseData>>()
        val rankList = context.assets.open("pose_ranks.csv").use { stream ->
            val resMutableList = mutableListOf<List<Double>>()
            CSVReader(InputStreamReader(stream)).forEach { strings ->
                if (strings[1].equals("pose_ids").not()) {
                    val listStrData = strings[1].substring(1, strings[1].length - 1)
                    listStrData.split(',').map { it.toDouble() }.let {
                        resMutableList.add(it)
                    }
                }
            }
            resMutableList
        }
        val keyDrawableArray = context.resources.obtainTypedArray(R.array.keypointImage)
        rankList.forEachIndexed { index, doubleList ->
            val tmpPoseList = mutableListOf<PoseData>()
            doubleList.forEach {
                val resId = keyDrawableArray.getResourceId(it.toInt(), -1)
                tmpPoseList.add(
                    PoseData(
                        it.toInt(),
                        resId,
                        index
                    )
                )
            }
            poseDataList.add(tmpPoseList)
        }
        poseDataList.toList()
    }


    override suspend fun recommendPose(Bitmap: Bitmap): Pair<DoubleArray, List<PoseData>> =
        suspendCoroutine { cont ->
            val backgroundBitmap =
                AppCompatResources.getDrawable(context, R.drawable.sample)!!.toBitmap()
            CoroutineScope(Dispatchers.IO).launch {
                val hogResult = async { getHOG(backgroundBitmap) }.await() //HoG 결과
                val resFeature = async {
                    modelRunner.runResNet(backgroundBitmap).map { it.toDouble() }
                }.await() //ResNet 결과
//                val angle = getAngleFromHog(getHistogramMap(backgroundBitmap))

                var res = Pair(-1, java.lang.Double.POSITIVE_INFINITY)

                for (i in 0 until centroid.size - 2) {
                    val calculatedDistance = getDistance(hogResult, resFeature, i)
                    if (res.second > calculatedDistance)
                        res = res.copy(first = i, second = calculatedDistance)
                }
                val bestPoseId = res.first
//                posePosition
                cont.resume(
                    Pair(
//                        posePosition,
                        emptyArray<Double>().toDoubleArray(),
                        poseRanks[bestPoseId]
                    )
                )
            }
        }

    override fun recommendPosePosition(backgroundBitmap: Bitmap): DoubleArray {
        val (mScaleSize, outputArray) = modelRunner.runYolo(backgroundBitmap)
        val yoloResult = outputsToNMSPredictions(mScaleSize, outputArray)
        val layoutImageBitmap = makeLayoutImage(yoloResult)
//        CoroutineScope(Dispatchers.IO).launch {
//            processor.saveImageToGallery(layoutImageBitmap)
//            processor.saveImageToGallery(backgroundBitmap)
//        }

        return modelRunner.runBbPrediction(
            backgroundBitmap,
            layoutImageBitmap
        ) //centerx, centery, width, height (모두 0~1)
    }

    override fun preProcessing(targetImage: Bitmap): Mat {

        val resizedImageMat = Mat(targetImage.width, targetImage.height, CvType.CV_8UC3)
        Utils.bitmapToMat(targetImage, resizedImageMat)
        Imgproc.cvtColor(resizedImageMat, resizedImageMat, Imgproc.COLOR_RGBA2RGB) //알파값을 빼고 저장
//        Imgproc.cvtColor(resizedImageMat, resizedImageMat, HogConfig.imageConvert)
        Imgproc.resize(resizedImageMat, resizedImageMat, HogConfig.imageResize)
//        Imgproc.GaussianBlur(resizedImageMat, resizedImageMat, HogConfig.blurSize, 1.0,1.0,Core.BORDER_CONSTANT) //<= 여기서 값이 차이가 남
        return resizedImageMat
    }

    override fun getDistance(hog: List<Double>, resnet50: List<Double>, centroidIdx: Int): Double {
        val weight = 50.0
        val (centroidResNet50, centroidGHog) = Pair(
            centroid[centroidIdx].subList(0, 2048),
            centroid[centroidIdx].subList(2048, centroid[centroidIdx].size)
        )
        val distanceResNet50 = resnet50.zip(centroidResNet50).map {
            sqrt(abs(it.first.pow(2) - it.second.pow(2))) //np.linalg.norm(A - B, axis = 0)
        }.let {
            it.sum() / it.size // np.mean()
        }
        val distanceHog = distanceHog(hog, centroidGHog)
        return weight * distanceHog + distanceResNet50
    }


    override fun distanceAngle(aAngle: List<Double>, bAngle: List<Double>): Double {
        var distance = 0.0
        for (idx in aAngle.indices) {
            distance += if ((aAngle[idx] == -1.0 && bAngle[idx] != -1.0) || (aAngle[idx] != -1.0 && bAngle[idx] == -1.0)) 1.0
            else 1 - abs(cos(aAngle[idx] - bAngle[idx]))
        }
        return distance
    }

    override fun distanceHog(aHog: List<Double>, bHog: List<Double>): Double {
        val imageResizeConfig = 128.0
        val cellSizeConfig = 16.0
        val histCnt = (imageResizeConfig / cellSizeConfig).pow(2).toInt()
        val binCnt = 9
        val reshapedAHog =
            reshapeList(aHog, listOf(histCnt, binCnt)).toMutableList().apply { addZDimension(this) }
        val reshapedBHog = reshapeList(bHog, listOf(histCnt, binCnt)).toMutableList().apply {
            addZDimension(this)
        }
        return calculateDistance(reshapedAHog, reshapedBHog)
    }

    override fun addZDimension(arr: MutableList<List<Double>>) {
        val arrShape = arr.size
        val zeroArr = List(arrShape) { 0.0 }
        val normArr = MutableList(arrShape) { 0.0 }
        normArr[0] = 1.0
        normArr[1] = 1.0

        val arrTrue = arr.map { sublist ->
            sublist.all { value -> abs(value) < 1e-6 } // Adjust tolerance as needed
        }
        val arrZDim = arrTrue.mapIndexed { index, value ->
            if (value) normArr else zeroArr
        }

        val arrZDimReshaped = arrZDim.flatten()
        val arrConcat = arr.mapIndexed { index, sublist ->
            sublist + arrZDimReshaped[index]
        }
        arr.clear()
        arr.addAll(arrConcat)
    }

    override fun calculateDistance(a: List<List<Double>>, b: List<List<Double>>): Double {
        require(a.size == b.size && a.isNotEmpty()) { "Input lists must have the same non-empty size." }

        val numElements = a.size * a[0].size

        val diffSquaredSum = a.zip(b).sumOf { (aSublist, bSublist) ->
            require(aSublist.size == bSublist.size) { "Sublists must have the same size." }

            aSublist.zip(bSublist).sumOf { (aVal, bVal) ->
                val diff = aVal - bVal
                diff * diff
            }
        }

        return sqrt(diffSquaredSum) / numElements
    }

    override fun reshapeList(inputList: List<Double>, newShape: List<Int>): List<List<Double>> {
        val totalElements = inputList.size
        val newTotalElements = newShape.reduce { acc, i -> acc * i }

        require(totalElements == newTotalElements) { "Total elements in input list must match new shape, total: $totalElements / new:$newTotalElements" }

        val result = mutableListOf<List<Double>>()
        var currentIndex = 0

        for (dimension in newShape) {
            val sublist = inputList.subList(currentIndex, currentIndex + dimension)
            result.add(sublist)
            currentIndex += dimension
        }
        return result
    }

    override fun getGradient(targetImage: Mat): Pair<Mat, Mat> {
        var gradientX = Mat(targetImage.size(), targetImage.type())
        var gradientY = Mat(targetImage.size(), targetImage.type())

        Imgproc.Sobel(targetImage, gradientX, CvType.CV_64F, 1, 0, 3)
        Imgproc.Sobel(targetImage, gradientY, CvType.CV_64F, 0, 1, 3)

        //gradient_x = gradient_x / int(np.max(np.abs(gradient_x)) if np.max(np.abs(gradient_x)) != 0 else 1) * 255
        gradientX = gradientX.apply {
            val zeroMat = Mat.zeros(this.size(), this.type())
            val absMat = Mat(this.size(), this.type())
            Core.absdiff(this, zeroMat, absMat)//abs값으로 변환
            val std = Core.minMaxLoc(absMat)
            val dv = if (std.maxVal != 0.0) std.maxVal else 1.0
            Core.divide(this, Scalar(dv), this)
            Core.multiply(this, Scalar(255.0), this)
        }
//        gradient_y = gradient_y / int(np.max(np.abs(gradient_y)) if np.max(np.abs(gradient_y)) != 0 else 1) * 255
        gradientY = gradientY.apply {
            val zeroMat = Mat.zeros(this.size(), this.type())
            val absMat = Mat(this.size(), this.type())
            Core.absdiff(this, zeroMat, absMat)//abs값으로 변환
            val std = Core.minMaxLoc(absMat)
            val dv = if (std.maxVal != 0.0) std.maxVal else 1.0
            Core.divide(this, Scalar(dv), this)
            Core.multiply(this, Scalar(255.0), this)
        }


        val gradientMagnitude = Mat(gradientX.size(), gradientX.type()).apply {
            val tmpY = Mat(gradientY.size(), gradientY.type())
            Core.pow(gradientX, 2.0, this)
            Core.pow(gradientY, 2.0, tmpY)
            Core.add(this, tmpY, this)
            Core.pow(this, 0.5, this)
        }

        // Calculate gradient orientation using OpenCV
        val gradientOrientation = Mat.zeros(gradientX.size(), gradientX.type()).apply {
            Core.phase(gradientX, gradientY, this, true)
        }

        // Adjust gradient orientation to [-π, π] range -> 이부분만 살짝 수정하면 Hog값은 100퍼센트 나옴.
        for (row in 0 until gradientOrientation.rows()) {
            for (col in 0 until gradientOrientation.cols()) {
                var adjustedPhase = gradientOrientation[row, col][0]
//                if (adjustedPhase >= 180.0) {
//                    adjustedPhase -= 180.0
//                }
                adjustedPhase %= 180
                gradientOrientation.put(row, col, adjustedPhase)
            }
        }


//        for (x in 0 until gradientMagnitude.rows()) {
//            for (y in 0 until gradientMagnitude.cols()) {
//                if (gradientMagnitude.get(
//                        x, y
//                    )[0] < HogConfig.magnitudeThreshold
//                ) {
//                    gradientMagnitude.put(x, y, 0.0)
//                }
//            }
//        }
        return Pair(gradientMagnitude, gradientOrientation)
    }

    override fun getHistogram(magnitude: Mat, orientation: Mat): DoubleArray {
        val maxDegree = 180.0
        val diff = maxDegree / HogConfig.nBins

        var histogram = DoubleArray(HogConfig.nBins)

        val cellSize = magnitude.size()

        for (x in 0 until cellSize.width.toInt()) {
            for (y in 0 until cellSize.height.toInt()) {
                val magValue = magnitude[x, y][0]
                val orientationValue = orientation[x, y][0]

//                if (magValue < HogConfig.magnitudeThreshold) continue

                val index = (orientationValue / diff).toInt()
                val deg = index * diff
                histogram[index] += magValue * (1 - (orientationValue - deg) / diff)

                val nextIndex = (index + 1) % HogConfig.nBins
                histogram[nextIndex] += magValue * ((orientationValue - deg) / diff)
            }
        }

        val maxVal = histogram.max()

        histogram = histogram.map {
            if (it == maxVal && maxVal != 0.0) 1.0
            else 0.0
        }.toDoubleArray()

        return histogram
    }

    override fun getHistogramMap(backgroundBitmap: Bitmap): Mat {
        val resizedImage = preProcessing(backgroundBitmap) //이미지

        val resizedImageMats = arrayListOf(
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1),
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1),
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1)
        )
        Core.split(resizedImage, resizedImageMats)
        val resList = ArrayList<Pair<Mat, Mat>>()
        resizedImageMats.forEach {
            resList.add(getGradient(it))
        }
        val resMagnitude = Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_64FC1)
        val resOrientation = Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_64FC1)
        val cnt = Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1)


        resList.forEachIndexed { idx, it ->
            val magnitude = it.first
            val orientation = it.second
            for (row in 0 until magnitude.rows()) {
                for (col in 0 until magnitude.cols()) {
                    resMagnitude.put(row, col, resMagnitude[row, col][0] + magnitude[row, col][0])
                    if (magnitude[row, col][0] != 0.0) {
                        resOrientation.put(
                            row,
                            col,
                            resOrientation[row, col][0] + orientation[row, col][0]
                        )
                        cnt.put(
                            row,
                            col,
                            cnt[row, col][0] + 1
                        )
                    }

                }
            }
        }

        for (row in 0 until cnt.rows()) {
            for (col in 0 until cnt.cols()) {
                val currentCnt = cnt[row, col][0]
                if (currentCnt != 0.0) {
                    resMagnitude.put(
                        row, col,
                        resMagnitude[row, col][0] / currentCnt
                    )
                    resOrientation.put(
                        row, col,
                        resOrientation[row, col][0] / currentCnt
                    )
                }
            }
        }
        var sum = 0.0


        val ave =
            Core.sumElems(resMagnitude).`val`[0] / (resMagnitude.width() * resMagnitude.height())
        for (row in 0 until cnt.rows()) {
            for (col in 0 until cnt.cols()) {
                resMagnitude.put(
                    row, col,
                    if (ave * HogConfig.magnitudeThreshold / 10 > resMagnitude[row, col][0]) 0.0
                    else 1.0
                )
            }
        }
        val arrayList = ArrayList<Double>()
        for (row in 0 until cnt.rows()) {
            for (col in 0 until cnt.cols()) {
                arrayList.add(resMagnitude[row, col][0])
                continue
            }
        }



        val histogramMap = Mat.zeros(
            Size(
                resizedImage.width() / HogConfig.cellSize.width,
                resizedImage.height() / HogConfig.cellSize.height
            ), CvType.CV_64FC(HogConfig.nBins)
        )

        Mat.zeros(
            Size(
                resizedImage.width() / HogConfig.cellSize.width,
                resizedImage.height() / HogConfig.cellSize.height
            ), CvType.CV_64FC(HogConfig.nBins)
        )

        for (x in 0 until resizedImage.width() step HogConfig.cellSize.height.toInt()) {
            for (y in 0 until resizedImage.height() step HogConfig.cellSize.width.toInt()) {
                val xEnd = x + HogConfig.cellSize.width.toInt()
                val yEnd = y + HogConfig.cellSize.height.toInt()
                val cellMagnitude = resMagnitude.submat(x, xEnd, y, yEnd)
                val cellOrientation = resOrientation.submat(x, xEnd, y, yEnd)

                val histogram = getHistogram(
                    cellMagnitude, cellOrientation
                )

                histogramMap.put(
                    x / HogConfig.cellSize.width.toInt(),
                    y / HogConfig.cellSize.height.toInt(),
                    *histogram
                )

            }
        }

        return histogramMap
    }

    override fun getHOG(backgroundBitmap: Bitmap): List<Double> {
        val histogramMap = getHistogramMap(backgroundBitmap)
        val hog = mutableListOf<Double>()
        val mapSize = histogramMap.size()
        for (x in 0 until mapSize.width.toInt() - HogConfig.blockSize.width.toInt() + 1) {
            for (y in 0 until mapSize.height.toInt() - HogConfig.blockSize.height.toInt() + 1) {
                val histogramVector = mutableListOf<Double>()
                for (bx in x until x + HogConfig.blockSize.width.toInt()) {
                    for (by in y until y + HogConfig.blockSize.height.toInt()) {
                        histogramVector.addAll(histogramMap[bx, by].toList()) //값을 추가
                    }
//                    //정규화 요소 구하는 공식 -> 각 값의 제곱을 더한 후 그것에 루트를 씌움 (Norm_2)
//                    val norm = histogramVector.toDoubleArray().let { hist ->
//                        var res = 0.0
//                        hist.forEach {
//                            res += it.pow(2)
//                        }
//                        if (sqrt(res) == 0.0) 1.0
//                        else sqrt(res)
//
//                    } //정규화 요소
//                    val normVector = histogramVector.map { it / norm } //정규화된 벡터값
                    hog.addAll(histogramVector)
                }
            }
        }
        return hog
    }

    override fun getAngleFromHog(histogramMap: Mat): List<Double> {
        val angleMap = List(histogramMap.rows() * histogramMap.cols()) { -1.0 }.toMutableList()

        for (row in 0 until histogramMap.rows()) {
            for (col in 0 until histogramMap.cols()) {
                if (histogramMap[row, col].all { it == 0.0 }.not())
                    for (index in histogramMap[row, col].indices) {
                        val value = histogramMap[row, col][index]
                        if (value == 1.0) {
                            angleMap[row * histogramMap.rows() + col] = 180.0 / HogConfig.nBins
                            break
                        }
                    }
            }
        }



        return angleMap
    }

    override fun makeLayoutImage(yoloResult: ArrayList<YoloPredictResult>) = Bitmap.createBitmap(
        480,
        480,
        Bitmap.Config.ARGB_8888
    )
        .apply {
            eraseColor(
                Color.rgb(
                    palette[0][0],
                    palette[0][1],
                    palette[0][2]
                )
            )//a. 480*480 이미지를 color palette의 0번째 색으로 생성
            val initPixelData =
                MutableList<MutableList<MutableList<Int>>>(480) { MutableList(480) { mutableListOf() } }

            //Yolo 결과값을 이용하여 레이아웃 이미지를 칠한다.
            yoloResult.forEach { predictResult ->
                val rect = Rect(
                    (0.75 * predictResult.box.left).toInt(),
                    (0.75 * predictResult.box.top).toInt(),
                    (0.75 * predictResult.box.right).toInt(),
                    (0.75 * predictResult.box.bottom).toInt()
                )
                val targetColorIdx = predictResult.classIdx + 1
                for (y in rect.top until rect.bottom) {
                    for (x in rect.left until rect.right) {
                        //색 값 저장
                        initPixelData[y][x].add(palette[targetColorIdx].let { rgb ->
                            Color.rgb(rgb[0], rgb[1], rgb[2])
                        })
                        //평균 내어 값을 저장
                        val avgColor = initPixelData[y][x].sum() / initPixelData[y][x].size
                        //평균 색을 칠함
                        this.setPixel(x, y, avgColor)
                    }
                }
            }

        }

    override fun outputsToNMSPredictions(
        scaleSize: Size,
        outputs: FloatArray
    ): ArrayList<YoloPredictResult> {
        val resultDataList = ArrayList<YoloPredictResult>()
        for (i in 0 until YOLO_OUTPUT_ROW) {
            if (outputs[i * YOLO_OUTPUT_COLUMN + 4] > YOLO_THRESHOLD) {
                val (x, y) = Pair(
                    outputs[i * YOLO_OUTPUT_COLUMN],
                    outputs[i * YOLO_OUTPUT_COLUMN + 1]
                )
                val (w, h) = Pair(
                    outputs[i * YOLO_OUTPUT_COLUMN + 2],
                    outputs[i * YOLO_OUTPUT_COLUMN + 3]
                )

                val rect = Rect(
                    (scaleSize.width * (x - w / 2)).toInt(),
                    (scaleSize.height * (y - h / 2)).toInt(),
                    (scaleSize.width * (x + w / 2)).toInt(),
                    (scaleSize.height * (y + h / 2)).toInt(),
                )
                var maxValue = outputs[i * YOLO_OUTPUT_COLUMN + 5]
                var clsIdx = 0
                for (j in 0 until YOLO_OUTPUT_COLUMN - 5) {
                    if (outputs[i * YOLO_OUTPUT_COLUMN + 5 + j] > maxValue) {
                        maxValue = outputs[i * YOLO_OUTPUT_COLUMN + 5 + j]
                        clsIdx = j
                    }
                }

                resultDataList.add(
                    YoloPredictResult(
                        classIdx = clsIdx,
                        score = outputs[i * YOLO_OUTPUT_COLUMN + 4],
                        box = rect
                    )
                )
            }
        }
        return nonMaxSuppression(resultDataList)
    }

    override fun IOU(a: Rect, b: Rect): Float {
        val areaA = ((a.right - a.left) * (a.bottom - a.top)).toFloat()
        if (areaA <= 0.0) return 0.0f
        val areaB = ((b.right - b.left) * (b.bottom - b.top)).toFloat()
        if (areaB <= 0.0) return 0.0f
        val intersectionMinX = a.left.coerceAtLeast(b.left).toFloat()
        val intersectionMinY = a.top.coerceAtLeast(b.top).toFloat()
        val intersectionMaxX = a.right.coerceAtMost(b.right).toFloat()
        val intersectionMaxY = a.bottom.coerceAtMost(b.bottom).toFloat()
        val intersectionArea =
            (intersectionMaxY - intersectionMinY).coerceAtLeast(0f) * (intersectionMaxX - intersectionMinX).coerceAtLeast(
                0f
            )
        return intersectionArea / (areaA + areaB - intersectionArea)
    }

    // The two methods nonMaxSuppression and IOU below are ported
// from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift
    override fun nonMaxSuppression(boxes: ArrayList<YoloPredictResult>): ArrayList<YoloPredictResult> {

        // Do an argsort on the confidence scores, from high to low.
        boxes.sortWith { o1, o2 -> o1.score.compareTo(o2.score) }
        val selected: ArrayList<YoloPredictResult> = ArrayList()
        val active = BooleanArray(boxes.size)
        Arrays.fill(active, true)
        var numActive = active.size

        // The algorithm is simple: Start with the box that has the highest score.
        // Remove any remaining boxes that overlap it more than the given threshold
        // amount. If there are any boxes left (i.e. these did not overlap with any
        // previous boxes), then repeat this procedure, until no more boxes remain
        // or the limit has been reached.
        var done = false
        var i = 0
        while (i < boxes.size && !done) {
            if (active[i]) {
                val boxA: YoloPredictResult = boxes[i]
                selected.add(boxA)
                if (selected.size >= YOLO_NMS_LIMIT) break
                for (j in i + 1 until boxes.size) {
                    if (active[j]) {
                        val boxB: YoloPredictResult = boxes[j]
                        if (IOU(boxA.box, boxB.box) > YOLO_THRESHOLD) {
                            active[j] = false
                            numActive -= 1
                            if (numActive <= 0) {
                                done = true
                                break
                            }
                        }
                    }
                }
            }
            i++
        }
        return selected
    }


    companion object {
        private const val YOLO_OUTPUT_ROW = 25200
        private const val YOLO_OUTPUT_COLUMN = 85
        private const val YOLO_THRESHOLD = 0.3F
        private const val YOLO_NMS_LIMIT = 15

        object HogConfig {
            val imageResize: Size = Size(128.0, 128.0)
            const val imageConvert: Int =
                Imgproc.COLOR_BGR2GRAY  //cv2.COLOR_BGR2GRAY | cv2.COLOR_BGR2RGB | cv2.COLOR_BGR2HSV
            val cellSize: Size = Size(16.0, 16.0)
            val blurSize = Size(31.0, 31.0)
            val blockSize: Size = Size(1.0, 1.0)
            const val magnitudeThreshold: Int = 10
            const val nBins: Int = 9

        }
    }
}