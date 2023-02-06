package com.plcoding.backgroundlocationtracking

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationClient {

    // interval - how often we want an update
    // Flow will emit everytime there is a new location that we fetch
    fun getLocationUpdates(interval: Long): Flow<Location>

    // if something goes wrong
    // user doesnt how a LocationPermission
    // or GPS is disabled
    class LocationException(message: String): Exception()
}