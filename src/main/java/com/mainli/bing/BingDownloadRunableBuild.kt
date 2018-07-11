package com.mainli.bing

import com.google.gson.Gson
import okio.Okio
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.*
import javax.net.ssl.HttpsURLConnection

class BingDownloadRunableBuild {

    /**
     *  我们可以通过访问：http://cn.bing.com/HPImageArchive.aspx?format=xml&idx=0&n=1获得一个XML文件，里面包含了图片的地址。
     *  上面访问参数的含义分别是：
     *  1、format，非必要。返回结果的格式，不存在或者等于xml时，输出为xml格式，等于js时，输出json格式。
     *  2、idx，非必要。不存在或者等于0时，输出当天的图片，-1为已经预备用于明天显示的信息，1则为昨天的图片，idx最多获取到前16天的图片信息。
     *  3、n，必要参数。这是输出信息的数量。比如n=1，即为1条，以此类推，至多输出8条。
     *  在返回的XML文件中我们通过访问images->image->url获得图片地址，然后通过http://s.cn.bing.net/获得的图片地址进行访问。
     */
    private fun obtainURL(idx: Int, count: Int): URL {
        return URL("http://cn.bing.com/HPImageArchive.aspx?format=js&idx=$idx&n=$count");
    }

    private fun downloadJSON(url: URL): String {
        val openConnection = url.openConnection() as HttpURLConnection
        openConnection.requestMethod = "GET"
        openConnection.setDoInput(true);
        openConnection.connect()
        val buffer = Okio.buffer(Okio.source(openConnection.inputStream))
        return buffer.readUtf8()
    }

    val BING_URL = "https://cn.bing.com"
    private fun analyzeJsonObtainImageBingRunable(json: String, savePath: String): List<ImageDownloadRunable> {
        val bingWrapper = Gson().fromJson(json, BingWrapper::class.java)
        val file = File(savePath)
        if (!file.exists()) {
            file.mkdirs()
        }
        println("存储路径:$file")
        val toList = bingWrapper.images.asSequence().map {
            ImageDownloadRunable(
                    BingImage("$BING_URL${it.url}",
                            "${it.copyright.replace(Regex("[ \\{\\}\\*\\/©]"), "").trim()}.jpg", it.enddate), savePath)
        }.filter(ImageDownloadRunable::isNeedDownload).toList()
        return addCountDownLatch(toList)
    }

    private fun addCountDownLatch(toList: List<ImageDownloadRunable>): List<ImageDownloadRunable> {
        if (toList.size > 1) {
            mCurrentCountDownLatch = CountDownLatch(toList.size)
        } else if (mCurrentCountDownLatch != null) {
            mCurrentCountDownLatch = null;
        }
        toList.forEach {
            it.countDownLatch = mCurrentCountDownLatch
        }
        return toList
    }


    var mCurrentCountDownLatch: CountDownLatch? = null

    fun awaitFinish() {
        mCurrentCountDownLatch?.await()
    }

    var mDownloadImageJson: String? = null
    fun loadDownloadImageJson(idx: Int, count: Int): BingDownloadRunableBuild {
        mDownloadImageJson = downloadJSON(obtainURL(idx, count))
        return this
    }

    fun buildRunableList(savePath: String): List<ImageDownloadRunable> {
        if (mDownloadImageJson == null) {
            throw RuntimeException("未获取到必应服务器返回信息")
        }
        return analyzeJsonObtainImageBingRunable(mDownloadImageJson!!, savePath)
    }


}

class ImageDownloadRunable : Runnable {
    val image: BingImage
    val file: File
    var countDownLatch: CountDownLatch? = null

    constructor(image: BingImage, savePath: String) {
        this.image = image
        this.file = File(savePath, "${image.enddate}-${image.copyright}")
    }

    override fun run() {
        val openConnection = URL(image.url).openConnection() as HttpsURLConnection
        openConnection.requestMethod = "GET"
        openConnection.setDoInput(true);
        openConnection.connect()
        println("[开始]下载(${file.name})...")
//        Okio.buffer(Okio.sink(file)).writeAll(Okio.source(openConnection.inputStream))
        Okio.buffer(Okio.source(openConnection.inputStream)).readAll(Okio.sink(file))
        println("下载完成($file)")
        //减少
        countDownLatch?.countDown()
    }

    fun isNeedDownload(): Boolean {
        if (file.exists()) {
            println("${file.name}已存在,跳过下载")
            return false
        }
        return true
    }
}

var index = 0
var count = 5
const val PARAME_START_TIME = "-startTime"
const val PARAME_COUNT = "-count"

fun fixIndex(index: Int): Int {
    if (index < 0) {
        return -1;
    } else if (index > 0) {
        return 1;
    } else {
        return 0;
    }
}

const val VERSION_NAME = "1.0.1"
const val VERSION_CODE = 2

fun main(args: Array<String>) {
    println("版本:$VERSION_NAME-$VERSION_CODE")
    try {
        if (args.isNotEmpty()) {
            if (PARAME_START_TIME.equals(args[0], true)) {
                index = fixIndex(args[1].toInt())
            } else if (PARAME_COUNT.equals(args[0], true)) {
                count = args[1].toInt()
            }
            if (PARAME_START_TIME.equals(args[2], true)) {
                index = fixIndex(args[3].toInt())
            } else if (PARAME_COUNT.equals(args[2], true)) {
                count = args[3].toInt()
            }
        }
    } finally {
        println("参数:${Arrays.toString(args)}")
    }
    val bingDownloadRunableBuild = BingDownloadRunableBuild().loadDownloadImageJson(index, count)
    var newCachedThreadPool: ExecutorService? = null
    try {
        val runablesList = bingDownloadRunableBuild.buildRunableList(File("bing背景").absolutePath)
        if (runablesList.size > 1) {
            newCachedThreadPool = Executors.newCachedThreadPool()
            runablesList.forEach {
                newCachedThreadPool.execute(it)
            }
        } else if (runablesList.isNotEmpty()) {
            runablesList[0].run()
        }
//    val arrayListOf = Array<Future<*>>(runables.size) { index ->
//        newCachedThreadPool.submit(runables[index])
//    }
//    for (future in arrayListOf) {
//        future.get()
//    }
    } catch (e: RuntimeException) {
    } finally {
        bingDownloadRunableBuild.awaitFinish()
        newCachedThreadPool?.shutdownNow()
        println("结束:作者Mainli")
    }
}


data class BingImage(val url: String, val copyright: String, val enddate: String)
data class BingWrapper(val images: Array<BingImage>)