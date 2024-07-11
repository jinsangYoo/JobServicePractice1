package com.example.jobservicepractice1

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
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
import kotlinx.coroutines.launch
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * User-initiated data transfer job service for (dummy) downloading files.
 */
class DummyDownloadJobService : JobService() {
    private val TAG = javaClass.simpleName
    private val max = 30000

    private var mDownList = LinkedHashMap<Int, Int>()
    private var mDownloadRunnable: DownloadRunnable? = null
    private var params: JobParameters? = null
    private var waitingNotify = false

    private fun makeDummyDownList() {
        for (i in 0 until max) {
            mDownList[i] = i
        }
        Log.d(TAG, "mDownList.size: ${mDownList.size}")
    }

    private fun postDelayedNotification(params: JobParameters?, progress: Double = 0.0, delayMillis: Long = 500L) {
        CoroutineScope(Dispatchers.Main).launch {

//        Log.d(TAG, "in postDelayedNotification::index: ${++index}, progress: ${progress}")
//        Log.d(TAG, "postDelayedNotification, waitingNotify: ${waitingNotify}")
        if (!waitingNotify) {
            Log.d(TAG, "handler.postDelayed!!")
            waitingNotify = true
//            Log.d(TAG, "postDelayedNotification, waitingNotify: ${waitingNotify}")


            Timer().schedule(delayMillis) {

                Log.d(TAG, "in postDelayed::${index}: ${progress}")
                postNotification(params, progress)
//                Handler(Looper.getMainLooper()).postDelayed({
//                    Log.d(TAG, "in getMainLooper.post::${index}: ${contentText}")
//                    postNotification3(params, "${index}: ${contentText}")
//                                                            }, delayMillis)

//                    val _contentText = contentText
//                    val _index = index
//                    Log.d(TAG, "in handler.post::_index: ${_index}, _contentText: ${_contentText}")
//                    postNotification2(params, _contentText, _index)


//                postNotification3(params, "${index}: ${contentText}")


            }
        }
//        else {
//            Log.d(TAG, "handler pass")
//        }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Log.d(TAG, "in onStartJob")
            // Create notification channel.
            val name = "Download Data"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            // Post initial notification.
            postNotification(params, 0.0)

            // Create a new thread for the actual work.
            // In this example, we only count from 0 to 10 while updating the notification text.
//            CoroutineScope(Dispatchers.IO).launch {
//                for (i in 0..max) {
//                    // Update progress to notification.
////                    Log.d(TAG, "in loop i: ${i}")
////                    postNotification(params, i / 10.0)
//
//                    postDelayedNotification(
//                        params = params,
//                        i / 10.0
//                    )
//                    Thread.sleep(300L)
//                }
//                postNotification(params, 1.0)
//                jobFinished(params, false)
//            }




//            val handlerThread = HandlerThread("MyDownloadThread").apply {
//                start()
//            }
//            val handler = Handler(handlerThread.looper)
//            handler.post {
//                for (i in 0..max) {
//                    // Update progress to notification.
////                    Log.d(TAG, "in loop i: ${i}")
//
////                    postNotification(params, i / 10.0)
//
//                    postDelayedNotification(
//                        params = params,
//                        i / 10.0
//                    )
//                }
//                Log.d(TAG, "last progress: ${max * 1.0}")
//                // Update completion to notification.
//                postNotification(params, max * 1.0)
//
//                // Finish the job.
//                jobFinished(params, false)
//            }

            this.params = params
            mDownloadRunnable?.start(params)

            // Returns true so that the service is keep running.
            return true
        } else {
            // Returns false so that the service is stopped.
            return false
        }
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

    @Synchronized
    private fun download(params: JobParameters?) {
        Log.d(TAG, "sync test 00001")
        var nowTrackID = -1
        val keys = mDownList.keys
        val it = keys.iterator()
        if (it.hasNext()) {
            nowTrackID = it.next()
        }
        Log.d(TAG, "sync test 00002")

        postDelayedNotification(
            params = params,
            nowTrackID * 1.0
        )

        Log.d(TAG, "sync test 00003")

        Log.d(TAG, "sync test 00005")
        Log.d(TAG, "sync test 00008")
        if (mDownList.size > 0)
            mDownList.remove(nowTrackID)
//
        Log.d(TAG, "sync test 00009")
        if (mDownList.size == 0) {
            stopAction()
        }

        Log.d(TAG, "sync test 00010")
    }

    private fun stopAction() {
        mDownloadRunnable?.stop()
        mDownList.clear()
        params?.let {
            jobFinished(it, false)
        }
    }

    private inner class DownloadRunnable : Runnable {

        private var mThread: Thread? = null
        private var mIsLive = false
        private var _params: JobParameters? = null

        fun start(params: JobParameters?) {
            _params = params
            if (!mIsLive) {
                mIsLive = true
                mThread = Thread(this, "download thread name")
                mThread?.start()
            }
        }

        override fun run() {
            Log.d(TAG, "in run mIsLive: ${mIsLive}")
//            mDownList.ot
            while (mIsLive) {
                Log.d(TAG, "run download")
                try {
                    download(_params)
                } catch (e: InterruptedIOException) {
                    e.printStackTrace()
                }
            }
        }

        fun stop() {
            mIsLive = false
            try {
                Log.d(TAG, "stop download")
                mThread?.interrupt()
            } catch (e: Exception) {
//                e.printStackTrace()
            }
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // Returns true so that the job can be retried rescheduled based on the retry criteria.
        return false
    }

    override fun onCreate() {
        super.onCreate()
        makeDummyDownList()
        mDownloadRunnable = DownloadRunnable()
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