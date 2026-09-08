package com.example.sahay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class NavigationManager(
    private val context: Context
) {

    fun checkLocationSettings(
        onLocationReady: () -> Unit,
        onLocationPromptShown: () -> Unit,
        onLocationError: () -> Unit
    ) {

        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000
            ).build()

        val settingsRequest =
            LocationSettingsRequest.Builder()
                .addLocationRequest(
                    locationRequest
                )
                .build()

        val client =
            LocationServices
                .getSettingsClient(context)

        val task =
            client.checkLocationSettings(
                settingsRequest
            )

        task.addOnSuccessListener {

            onLocationReady()
        }

        task.addOnFailureListener { exception ->

            if (
                exception is ResolvableApiException
            ) {

                try {

                    exception.startResolutionForResult(
                        context as MainActivity,
                        MainActivity.REQUEST_CHECK_SETTINGS
                    )

                    onLocationPromptShown()

                } catch (e: Exception) {

                    onLocationError()
                }

            } else {

                onLocationError()
            }
        }
    }

    fun openGoogleMaps(
        destination: String
    ) {

        val encodedDestination =
            Uri.encode(destination)

        /*
         * Google Maps navigation URI.
         *
         * mode=w means WALKING.
         *
         * Google Maps itself provides:
         *
         * - Turn left
         * - Turn right
         * - Continue straight
         * - Recalculate route
         * - Navigation voice
         */

        val googleMapsUri =
            Uri.parse(
                "google.navigation:q=" +
                        encodedDestination +
                        "&mode=w"
            )

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                googleMapsUri
            )

        intent.setPackage(
            "com.google.android.apps.maps"
        )

        try {

            context.startActivity(intent)

        } catch (e: Exception) {

            /*
             * Fallback to Google Maps web navigation.
             */

            val webUri =
                Uri.parse(
                    "https://www.google.com/maps/dir/" +
                            "?api=1" +
                            "&destination=" +
                            encodedDestination +
                            "&travelmode=walking" +
                            "&dir_action=navigate"
                )

            val webIntent =
                Intent(
                    Intent.ACTION_VIEW,
                    webUri
                )

            try {

                context.startActivity(
                    webIntent
                )

            } catch (exception: Exception) {

                Toast.makeText(
                    context,
                    "Google Maps is not available",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
