package net.techcndev.upoblationdioramaapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.nordan.dialog.Animation;
import com.nordan.dialog.DialogType;
import com.nordan.dialog.NordanLoadingDialog;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GlobalObject {

    public static final String LOG_TAG = GlobalObject.class.getSimpleName();

    private static Context mContext;

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;
    SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef;
    DatabaseReference rootRef;
    DatabaseReference registeredUserRef;
    DatabaseReference leftLaserModeRef;
    DatabaseReference rightLaserModeRef;
    DatabaseReference lightpostStateRef;
    DatabaseReference leftLedPumpModeRef;
    DatabaseReference rightLedPumpModeRef;
    DatabaseReference musicNameRef;
    DatabaseReference spotlightStateRef;
    DatabaseReference batteryPercentRef;
    DatabaseReference deviceModeRef;
    DatabaseReference powerSourceRef;
    DatabaseReference soundVolumeRef;
    DatabaseReference waterLevelRef;

    public GlobalObject(Context context) {
        GlobalObject.mContext = context.getApplicationContext();

        sharedPreferences = context.getSharedPreferences("PREFS_DATA", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        // Initialize the listener
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.d(LOG_TAG, "GlobalObject SharedPreference changed: " + key);
                if (key.equals("user_device")) {
                    init_database_refs();
                }
            }
        };

        // Register the listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        rootRef = database.getReference();

        init_database_refs();
    }

    private void init_database_refs() {
        String userDevice = sharedPreferences.getString("user_device","");
        myRef = rootRef.child(userDevice);
        registeredUserRef = myRef.child("registeredUser");

        leftLaserModeRef = myRef.child("custom_controls").child("leftLaserMode");
        leftLedPumpModeRef = myRef.child("custom_controls").child("leftLedPumpMode");
        lightpostStateRef = myRef.child("custom_controls").child("lightpostState");
        musicNameRef = myRef.child("custom_controls").child("musicName");
        spotlightStateRef = myRef.child("custom_controls").child("spotlightState");
        rightLaserModeRef = myRef.child("custom_controls").child("rightLaserMode");
        rightLedPumpModeRef = myRef.child("custom_controls").child("rightLedPumpMode");

        batteryPercentRef = myRef.child("utilities").child("batteryPercent");
        deviceModeRef = myRef.child("utilities").child("deviceMode");
        powerSourceRef = myRef.child("utilities").child("powerSource");
        soundVolumeRef = myRef.child("utilities").child("soundVolume");
        waterLevelRef = myRef.child("utilities").child("waterLevel");
    }

    public boolean isReliableInternetAvailable() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return checkInternetConnection();
            }
        });

        try {
            // Wait for the task to complete and get the result (with a timeout)
            return future.get(3, TimeUnit.SECONDS); // Wait for up to 3 seconds for the result
        } catch (TimeoutException e) {
            Log.d(LOG_TAG, "Timeout waiting for internet connection");
            return false; // In case of a timeout, return false
        } catch (Exception e) {
            Log.d(LOG_TAG, "Error checking internet connection: " + e.getMessage());;
            return false; // Handle other exceptions
        } finally {
            executorService.shutdown(); // Shutdown the executor properly
        }
    }

    private boolean checkInternetConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            Log.d(LOG_TAG, String.valueOf("isNetworkAvailable: " + activeNetwork != null && activeNetwork.isConnected()));

            if (activeNetwork != null && activeNetwork.isConnected()) {
                try {
                    HttpURLConnection urlConnection = (HttpURLConnection)
                            (new URL("https://www.google.com").openConnection());
                    urlConnection.setRequestProperty("User-Agent", "Test");
                    urlConnection.setRequestProperty("Connection", "close");
                    urlConnection.setConnectTimeout(1000); // 1 second timeout
                    urlConnection.connect();

                    Log.d(LOG_TAG, "checkInternetConnection: " + urlConnection.getResponseCode());
                    int responseCode = urlConnection.getResponseCode();
                    return responseCode >= 200 && responseCode < 300;

                } catch (IOException e) {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    // Make sure to clean up the listener when it's no longer needed
    public void unregisterListener() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }
}
