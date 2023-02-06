package com.plcoding.backgroundlocationtracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class DefaultLocationClient(
    private val context: Context,
    private val client: FusedLocationProviderClient
): LocationClient {

    @SuppressLint("MissingPermission")
    override fun getLocationUpdates(interval: Long): Flow<Location> {
        // callbackFlow is used if you have a callback that you want to use as a Flow
        // so you have a callback that gets fired over again and again
        // like this location callback (that gets triggered when we fetch a new location)
        // and we want is as a Flow

        // Callback is also very useful If we want to model some callback that has a lifecycle
        // in this case for the location updates
        // Because we need to tell the client to Start fetching the location
        // and to STOP fetching the location
        // With callbackFlow we can easily model this behaviour using coroutines
        // to easily collect this Flow.

        // As soon as we stop collecting this Flow (when the scope is cancelled, user navigates away
        // and the viewModels scope is cancelled)
        // Then we can automatically call this function to Stop getting Location updates.
        return callbackFlow {
            // check for locations permissions
            if(!context.hasLocationPermission()) {
                // permissions arent granted
                throw LocationClient.LocationException("Missing location permission")
            }

            // check for location hardware accessibility
            // that means GPS or NetworkProvider enabled
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if(!isGpsEnabled && !isNetworkEnabled) {
                // no location hardware is enabled
                throw LocationClient.LocationException("GPS is disabled")
            }

            // Location service
            // define how often we want a location
            // and what accuracy we want
            val request = LocationRequest.create()
                .setInterval(interval)
                .setFastestInterval(interval)

            // specifying a location callback
            val locationCallback = object : LocationCallback() {
                // this function is called when location client fetches a new location
                override fun onLocationResult(result: LocationResult) {
                    super.onLocationResult(result)
                    // result contains location list and the last item is the new location
                    result.locations.lastOrNull()?.let { location ->
                        // if it exists we want to notify the Flow
                        // and send the location in that Flow
                        launch { send(location) }
                    }
                }
            }

            // start requesting location updates
            // doesnt recognize permission check use annotation
            client.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )

            // when we stop collecting location updates
            // awaitClose will block the Flow until its closed (until the coroutine is canceled)
            awaitClose {
                client.removeLocationUpdates(locationCallback)
            }
        }
    }
}