package com.example.jobservicepractice1

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.jobservicepractice1.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val JobId = 1001
    private val STORAGE_AVAILABLE_SIZE = (30 * 1024 * 1024).toLong()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            trigger.setOnClickListener {
                // Define network constraint for the job.
                val networkRequestBuilder = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    .build()

// Define the job and its constraints.
                val jobInfo = JobInfo.Builder(JobId, ComponentName(this@MainActivity, DummyDownloadJobService::class.java))
                    .setUserInitiated(true)
                    .setRequiredNetwork(networkRequestBuilder)
                    .setEstimatedNetworkBytes(STORAGE_AVAILABLE_SIZE, STORAGE_AVAILABLE_SIZE)
                    .build()

// Schedule the job.
                val jobScheduler = this@MainActivity.getSystemService(JobScheduler::class.java)
                jobScheduler.schedule(jobInfo)
            }
            stop.setOnClickListener {
                val jobScheduler = this@MainActivity.getSystemService(JobScheduler::class.java)
                jobScheduler.cancel(JobId)
            }
        }

        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}