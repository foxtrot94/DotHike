package me.dotteam.dotprod.loc;

import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Location Requests to GoogleApiClient and provides an easy way for any object to obtain Location Updates using one common entity.
 *
 * Created by EricTremblay on 15-11-05.
 */
public class HikeLocationEntity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    /**
     * Class TAG
     */
    private final String TAG = "HikeLocationEntity";

    /**
     * Default Values for Location Updates
     */
    private final int DEFAULT_INTERVAL = 5000;
    private final int DEFAULT_FASTEST_INTERVAL = 1000;

    /**
     * Location Request Priority. Default is High Accuracy
     */
    private int REQUEST_PRIORITY = LocationRequest.PRIORITY_HIGH_ACCURACY;

    /**
     * Alpha parameter for lowpass filter on altitude
     */
    private static final float FILTER_ALPHA = 0.25f;

    /**
     * Minimum location accuracy
     */
    private int MIN_LOCATION_ACCURACY = 40;

    /**
     * List of Listeners
     */
    private List<HikeLocationListener> mListeners;

    /**
     * Location Request Object
     */
    private LocationRequest mLocationRequest;

    /**
     * Actual Values for Location Updates
     */
    private int mInterval;
    private int mFastestInterval;
    private int mPriority;

    /**
     * HikeLocationEntity Singleton Reference
     */
    private static HikeLocationEntity mInstance;

    /**
     * Activity Context Object
     */
    private Context mContext;

    /**
     * Reference to Google API Client Object
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * Boolean to save the GoogleApiClient connection state
     */
    private boolean mGoogleApiClientConnected = false;

    /**
     * Boolean to save whether updates are on or off
     */
    private boolean mRequestingLocationUpdates = false;

    /**
     * Last Known Location
     */
    private Location mLastKnownLocation;

    /**
     * Singleton method to obtain or generate current instance
     * @param context Context from which the instance is being requested
     * @return Singleton Object instance of HikeLocationEntity
     */
    public static HikeLocationEntity getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new HikeLocationEntity(context);
        }
        return mInstance;
    }

    /**
     * Private Constructor for HikeLocationEntity
     * @param context Context from which the object creation is being requested
     */
    private HikeLocationEntity(Context context) {
        // Save Context
        mContext = context;

        // Build GoogleApiClient
        buildGoogleApiClient();

        // Create list of listeners
        mListeners = new ArrayList<>();

        // Create Location Request and set variables to default values
        mLocationRequest = new LocationRequest();
        mInterval = DEFAULT_INTERVAL;
        mFastestInterval = DEFAULT_FASTEST_INTERVAL;
        mPriority = REQUEST_PRIORITY;
        mLocationRequest.setInterval(mInterval);
        mLocationRequest.setFastestInterval(mFastestInterval);
        mLocationRequest.setPriority(mPriority);
    }

    private void updateFromPreferences(){
        SharedPreferences prefMan = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (prefMan.contains("location_permission")){
            REQUEST_PRIORITY = Integer.parseInt(
                    prefMan.getString("location_permission",String.valueOf(LocationRequest.PRIORITY_HIGH_ACCURACY)));
        }
        if(prefMan.contains("location_filter_accuracy")){
            MIN_LOCATION_ACCURACY = prefMan.getInt("location_filter_accuracy", MIN_LOCATION_ACCURACY);
        }
    }

    /**
     * Starts location updates for all registered listeners
     */
    public void startLocationUpdates(Context context) {
        Log.d(TAG, "Starting location updates");

        // Update Context
        mContext = context;

        mRequestingLocationUpdates = true;

        //Read the preferences
        updateFromPreferences();

        if (mGoogleApiClientConnected) {

            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                    .addLocationRequest(mLocationRequest);

            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

            result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                @Override
                public void onResult(LocationSettingsResult result) {
                    final Status status = result.getStatus();
                    final LocationSettingsStates states = result.getLocationSettingsStates();
                    switch (status.getStatusCode()) {
                        case LocationSettingsStatusCodes.SUCCESS:
                            // All location settings are satisfied. The client can initialize location
                            // requests here.

                            break;
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the user
                            // a dialog.

                            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);
                            dialogBuilder.setTitle("Turn on Location?")
                                    .setMessage("In order for an optimal .Hike experience, please turn on your cellphone's location services. Do you wish to turn on location services?")
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            mContext.startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                        }
                                    })
                                    .setNegativeButton("Not Now", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            stopLocationUpdates();
                                        }
                                    });
                            dialogBuilder.create().show();

                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            Log.i(TAG, "Location services error - Settings Chance Unavailable");
                            Toast.makeText(mContext, "An error occurred! Location services will not be available.", Toast.LENGTH_LONG).show();
                            break;
                    }
                }
            });

            // Add location listener
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

        } else {
            Log.i(TAG, "GoogleApiClient is not connected. Unable to start location updates");
        }
    }

    /**
     * Stops location updates for all registered listeners
     */
    public void stopLocationUpdates() {
        Log.d(TAG, "Stopping location updates");
        mRequestingLocationUpdates = false;
        mLastKnownLocation =null;
        if (mGoogleApiClientConnected) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        } else {
            Log.i(TAG, "GoogleApiClient is not connected. Unable to stop location updates");
        }
    }

    /**
     * Applies changes to location entity by calling {@link #stopLocationUpdates()}
     * and {@link #startLocationUpdates(Context)}
     */
    public void resetLocationUpdates() {
        if (mGoogleApiClientConnected) {
            stopLocationUpdates();
            startLocationUpdates(mContext);
        } else {
            Log.i(TAG, "GoogleApiClient is not connected. Unable to reset location updates");
        }
    }

    /**
     * Register LocationListener to HikeLocationEntity.
     * Start location updates for the LocationListener if the GoogleApiClient is connected and the location updates have been started
     * @param listener LocationListener to be registered
     */
    public void addListener(HikeLocationListener listener) {
        Log.d(TAG, "Adding listener " + listener.toString());
        mListeners.add(listener);
    }

    /**
     * Unregister LocationListener from HikeLocationEntity.
     * This LocationListener will cease receiving any location updates
     * @param listener LocationListener to unregister
     */
    public void removeListener(HikeLocationListener listener) {
        Log.d(TAG, "Removing listener " + listener.toString());
        mListeners.remove(listener);
    }

    /**
     * Method to build GoogleApiClient object from within HikeLocationEntity object
     */
    private synchronized void buildGoogleApiClient() {
        Log.d(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    /**
     * Method to set the preferred rate at which the application receives location updates
     * @param mInterval New interval between location updates in milliseconds
     */
    public void setInterval(int mInterval) {
        this.mInterval = mInterval;
        mLocationRequest.setInterval(mInterval);
    }

    /**
     * Method to set the fastest rate at which the application can handle location updates
     * @param mFastestInterval New fastest interval between location updates in milliseconds
     */
    public void setFastestInterval(int mFastestInterval) {
        this.mFastestInterval = mFastestInterval;
        mLocationRequest.setFastestInterval(mFastestInterval);
    }

    /**
     * Method to set the priority of the location request. This will allow the Google Play services to determine which location sources to use.
     * Possible values for priority: PRIORITY_BALANCED_POWER_ACCURACY,
     * PRIORITY_HIGH_ACCURACY, PRIORITY_LOW_POWER, and PRIORITY_NO_POWER.
     * @param mPriority New priority for location request
     */
    public void setPriority(int mPriority) {
        this.mPriority = mPriority;
        mLocationRequest.setPriority(mPriority);
    }

    /**
     * Method to obtain the current preferred interval between location updates.
     * @return Current preferred interval value
     */
    public int getInterval() {
        return mInterval;
    }

    /**
     * Method to obtain the current fastest interval between location updates.
     * @return Current fastest interval value
     */
    public int getFastestInterval() {
        return mFastestInterval;
    }

    /**
     * Method to obtain the current priority of the location request.
     * @return Current priority value
     */
    public int getPriority() {
        return mPriority;
    }

    /**
     * Method to obtain the current state of the GoogleApiClient Connection
     * @return True indicates the GoogleApiClient is connected, false indicates it is disconnected
     */
    public boolean isGoogleApiClientConnected() {
        return mGoogleApiClientConnected;
    }

    /**
     * Method to obtain the current state of the location updates.
     * @return True indicates the location updates are on, false indicates that they are off.
     */
    public boolean isRequestingLocationUpdates() {
        return mRequestingLocationUpdates;
    }

    private double[] lowpassFilter(double[] input) {
        double[] output = new double[input.length];

        output[0] = input[0];

        for (int i = 1; i < input.length; i++) {
            output[i] = output[i - 1] + FILTER_ALPHA * (input[i] - output[i - 1]);
        }

        return output;
    }

    @Override
    public void onLocationChanged(Location location) {
        // Log values
        Log.i(TAG, "Location Changed!"
                + "\nLatitude: " + location.getLatitude()
                + "\nLongitude: " + location.getLongitude()
                + "\nAltitude: " + location.getAltitude()
                + "\nBearing: " + location.getBearing()
                + "\nAccuracy :" + location.getAccuracy());

        // Check if the accuracy is good enough
        if (location.getAccuracy() <= MIN_LOCATION_ACCURACY) {
            // Check if its the first point
            if (mLastKnownLocation == null) {
                mLastKnownLocation = new Location(location);

                // Notify listeners
                for (int i = 0; i < mListeners.size(); i++) {
                    mListeners.get(i).onLocationChanged(location, 0);
                }
            } else {
                // Get previous location
                double prevLatitude = mLastKnownLocation.getLatitude();
                double prevLongitude = mLastKnownLocation.getLongitude();

                // Array to store distance result
                float results[] = new float[3];

                // Get distance current location and last known location
                Location.distanceBetween(prevLatitude, prevLongitude, location.getLatitude(), location.getLongitude(), results);

                // Log distance result
                Log.d(TAG, "Distance: " + String.valueOf(results[0]));

                // Check if distance is greater than accuracy
                if (results[0] > location.getAccuracy()) {

                    // Put current and previous altitudes in an array of doubles
                    double[] altitude = new double[2];
                    altitude[0] = mLastKnownLocation.getAltitude();
                    altitude[1] = location.getAltitude();

                    // Filter altitude
                    double[] new_altitude = lowpassFilter(altitude);

                    // Update location object with new altitude
                    location.setAltitude(new_altitude[1]);

                    // Find change in altitude
                    double delta_altitude = location.getAltitude() - mLastKnownLocation.getAltitude();

                    // Adjust distance (d' = sqrt(d^2 + a^2))
                    double adjusted_distance = Math.sqrt(Math.pow(results[0], 2) + Math.pow(delta_altitude, 2));

                    Log.d(TAG, "Distance with altitude: " + String.valueOf(adjusted_distance));

                    // update last known location
                    mLastKnownLocation = location;
                    broadcastUpdate(location, results[0]);
                }
            }
        }
    }

    private void broadcastUpdate(Location location, float distance){
        // Notify listeners
        for (int i = 0; i < mListeners.size(); i++) {
            mListeners.get(i).onLocationChanged(location, distance);
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");
        mGoogleApiClientConnected = true;
        if (mRequestingLocationUpdates) {
            startLocationUpdates(mContext);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        String cause = "CAUSE_UNKNOWN";
        switch (i) {
            case CAUSE_SERVICE_DISCONNECTED: {
                cause = "CAUSE_SERVICE_DISCONNECTED";
                break;
            }
            case CAUSE_NETWORK_LOST: {
                cause = "CAUSE_NETWORK_LOST";
                break;
            }
        }

        Log.i(TAG, "GoogleApiClient Connection Suspended. Cause: " + cause);
        mGoogleApiClientConnected = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "GoogleApiClient connection failed" + connectionResult);
        mGoogleApiClientConnected = false;

    }
}
