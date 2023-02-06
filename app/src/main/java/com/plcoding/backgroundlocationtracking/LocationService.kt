package com.plcoding.backgroundlocationtracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// SERVICE NEEDS TO BE REGISTERED IN MANIFEST
// LIKE THIS android:foregroundServiceType="location"
// Otherwise Android wont allow you to track users location.

// Service keeps running when minimize the application
// A foreground service comes with notifications
// so the user knows that the service is running.
// Then the app is considered in the foreground.

// We do not have restrictions like for a BACKGROUND service.
// In that case we need a manifest permission for background service
// and also request this permission, because the user wont see it running also restricted in Play Store.

// set up Service to fetch location in the background

class LocationService: Service() {

    // serviceScope bound to the lifetime of this service
    // IO - this is an IO operation
    // SupervisorJob - if one job in this scope fails others will keep running
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // our abstraction
    private lateinit var locationClient: LocationClient

    override fun onBind(p0: Intent?): IBinder? {
        // return null because we do not bind our service to anything
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // when this service is created
        // initialize location client USE DI
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
    }

    // This function can check what the action/command is.
    // It will be called every time we send an INTENT in this service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // if the intent has action and then check which action it is
        when(intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Since we want to use a foreground service we need to show Notifications
    private fun start() {
        // create notification
        val notification = NotificationCompat.Builder(this, "location")
            .setContentTitle("Tracking location...")
            // default notification info
            .setContentText("Location: null")
            .setSmallIcon(R.drawable.ic_launcher_background)
            // cant swipe it away
            .setOngoing(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // We want to launch our callback Flow together with the start function function with
        locationClient
                // get location update every 10 seconds
            .getLocationUpdates(10000L)
                // catch exceptions and print them
            .catch { e -> e.printStackTrace() }
                // on each location update our Notification
            .onEach { location ->
                val lat = location.latitude.toString().takeLast(3)
                val long = location.longitude.toString().takeLast(3)

                // construct updated location
                val updatedNotification = notification.setContentText(
                    // only need to change the text
                    "Location: ($lat, $long)"
                )
                // using NotificationManager we can update our existing notification
                notificationManager.notify(1, updatedNotification.build())
            }
            // launchIn(serviceScope) will bind this callback Flow (which getLocationUpdates() returns
            // to the lifetime of our service (which scope will be destroyed)
            .launchIn(serviceScope)

        // to make this a foreground service
        // doesnt start the location tracking
        startForeground(1, notification.build())
    }

    private fun stop() {
        // remove the notification when service stops
        stopForeground(true)
        // to stop this service
        stopSelf()
    }

    // When the Service is destroyed we cancel coroutine scope
    override fun onDestroy() {
        super.onDestroy()
        // Since we use a callback Flow in DefaultLocationClient
        // and we will launch that Flow in our serviceScope.
        // So as we cancel this scope we automatically stop fetching the location. (awaitClose{})
        serviceScope.cancel()
    }

    // If we want to communicate with this service (in Activity we click START_BTN or STOP_BTN)
    // We need to send this information to our service to update our to UPDATE our notification
    // or to START the tracking
    companion object {
        // START tracking
        const val ACTION_START = "ACTION_START"
        // STOP tracking
        const val ACTION_STOP = "ACTION_STOP"
    }
}