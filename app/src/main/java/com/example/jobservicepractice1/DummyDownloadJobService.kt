package com.example.jobservicepractice1

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * User-initiated data transfer job service for (dummy) downloading files.
 */
class DummyDownloadJobService : JobService() {
    private val TAG = javaClass.simpleName
//    private val max = 30000
    private val max = 10

    private val BUFFER_SIZE = 16 * 1024
    private var mIsStop = false

    private var mDownList = LinkedHashMap<Int, Int>()
    private var params: JobParameters? = null
    private var waitingNotify = false
    private lateinit var coroutuneJob: Job

    private var mDrmConn: HttpURLConnection? = null
    private var mDrmInputStream: InputStream? = null
    private var mDrmOutputStream: OutputStream? = null
    /**
     * 외부에서 강제적으로 종료 시킨경우, 오류 코드 삽입.
     */
    var errorCode: Int = 0

    private fun makeDummyDownList() {
        for (i in 0 until max) {
            mDownList[i] = i + 1
        }
        Log.d(TAG, "mDownList.size: ${mDownList.size}")
    }

    private fun postDelayedNotification(params: JobParameters?, progress: Double = 0.0, delayMillis: Long = 300L) {
        CoroutineScope(Dispatchers.Main).launch {
            if (!waitingNotify) {
                Log.d(TAG, "handler.postDelayed!!")
                waitingNotify = true

                Timer().schedule(delayMillis) {
                    Log.d(TAG, "in postDelayed::${index}: ${progress}")
                    postNotification(params, progress)
                }
            }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        Log.d(TAG, "in onStartJob")
        // Create notification channel.
        this.params = params

        val name = "Download Data"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        // Post initial notification.
        postNotification(params, 0.0)

        // Create a new thread for the actual work.
        // In this example, we only count from 0 to 10 while updating the notification text.
        coroutuneJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                download(params)
//                Thread.sleep(300L)
            }
        }

            // Returns true so that the service is keep running.
            return true
    }

    private fun postNotification(params: JobParameters?, progress: Double) {
        waitingNotify = false
        val contentText = if (progress >= max) {
            "Completed!"
        } else {
            "Progress: ${progress}%"
        }
        Log.d(TAG, "progress: ${progress}, contentText: ${contentText}")

        val notification = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Downloading your file")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentText(contentText)
            .build()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this)
                .notify(
                    NOTIFICATION_ID,
                    notification,
                )
        }
        else {
            Log.d(TAG, "in postNotification, Manifest.permission.POST_NOTIFICATIONS 권한 없음")
        }

        params?.let {
            // Set notification to job.
            setNotification(
                it,
                NOTIFICATION_ID,
                notification,
                JOB_END_NOTIFICATION_POLICY_REMOVE,
            )
        }

        Log.d(TAG, "in postNotification mDownList.size: ${mDownList.size}")
        if (mDownList.size == 0) {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
    }

    private fun download(params: JobParameters?) {
        Log.d(TAG, "sync test 00001")
        var nowTrackID = -1
        val keys = mDownList.keys
        val it = keys.iterator()
        if (it.hasNext()) {
            nowTrackID = it.next()
        }

        Log.d(TAG, "sync test 00002, nowTrackID: ${nowTrackID}")
        postDelayedNotification(
            params = params,
            nowTrackID * 1.0
        )

        val _file = File(getDrmDirectory(this), "/$nowTrackID.pdf")
        downloadDRM("http://192.168.220.14:52274/file/$nowTrackID.pdf", _file)

        if (mDownList.size > 0)
            mDownList.remove(nowTrackID)

        if (mDownList.size == 0) {
            stopAction()
        }
        Log.d(TAG, "sync test 00003")
    }

    private fun downloadDRM(strUrl: String, downFile: File): Boolean {
        mIsStop = false

        val dir = downFile.parentFile
        if (!dir.exists()) {
            makeDirs(dir)
        }

        if (downFile.exists())
            downFile.delete()

        var result = false
        try {
            val url = URL(strUrl)
            HttpURLConnection.setFollowRedirects(false)
            mDrmConn = url.openConnection() as HttpURLConnection

            mDrmConn?.let {
                it.instanceFollowRedirects = false
                it.requestMethod = "GET"
//                it.setRequestProperty("user-agent", getUserAgent(mContext))
                it.connectTimeout = 30 * 1000
                it.readTimeout = 30 * 1000

                val map = it.headerFields
                for ((key, value) in map) {
                    Log.d(TAG, "Key : $key, Value : $value")
                }

                errorCode = it.responseCode

                if (errorCode == HttpURLConnection.HTTP_OK) {
                    var contentLength: Long = 0
                    var current: Long = 0
                    var read = 0
                    val b = ByteArray(BUFFER_SIZE)

                    try {
                        mDrmInputStream = it.inputStream.apply {
                            contentLength = it.contentLength.toLong()

                            // "받다가 중간에 끊어지면 이미 받아놓은 cache파일이 못쓰게 되므로 tmp파일에 작성하고 바꿔치기 한다."
                            val tmpFile = File(downFile.absolutePath + "tmp")
//                            mDrmOutputStream = OutputStream(this, tmpFile.absolutePath)
                            mDrmOutputStream = FileOutputStream(downFile)

                            read = read(b)
                            while (read != -1) {
                                if (mIsStop) { // "받고 있는 중에 중단 한다."
                                    close()
                                    mDrmInputStream = null
                                    mDrmOutputStream!!.close()
                                    mDrmOutputStream = null
                                    tmpFile.delete()
                                    if (mDrmConn != null) {
                                        it.disconnect()
                                        mDrmConn = null
                                    }
                                    return false
                                }

                                mDrmOutputStream!!.write(b, 0, read)

                                current += read.toLong()
//                                if (mListener != null) {
//                                    mListener!!.onProgress(current, contentLength)
//                                }
                                read = read(b)
                            }

                            mDrmOutputStream!!.flush()
                            mDrmOutputStream!!.close()

                            if (downFile.exists())
                                downFile.delete()

                            if (contentLength == current) {
                                tmpFile.renameTo(downFile)
                                result = true
                                Log.e(TAG, "ret rename $result")
                            } else {
                                Log.e(TAG, contentLength.toString() + "/" + current)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not save file from $strUrl", e)
                    } finally {
                        mDrmInputStream?.close()
                        mDrmOutputStream?.close()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not load file from $strUrl", e)
        } finally {
            try {
                if (mDrmConn != null) {
                    mDrmConn!!.disconnect()
                }
            } catch (ignore: Exception) {
                Log.e(TAG, "error close . drm ", ignore)
            }

        }

        return result
    }

    fun getDrmDirectory(context: Context): File {
        return File(context.getExternalFilesDir(null), "/drm")
    }

    fun makeDirs(dir: File): Boolean {
        return if (dir.exists() == false) {
            dir.mkdirs()
        } else true
    }

    private fun stopAction() {
        mIsStop = true
        coroutuneJob.cancel()
        mDownList.clear()
        params?.let {
            jobFinished(it, false)
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // Returns true so that the job can be retried rescheduled based on the retry criteria.
        stopAction()
        return false
    }

    override fun onCreate() {
        super.onCreate()
        makeDummyDownList()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        // Notification channel ID for downloading data.
        const val NOTIFICATION_CHANNEL_ID = "download-data-channel"

        // Notification ID for downloading data.
        const val NOTIFICATION_ID = 123

        var index = 0
    }
}