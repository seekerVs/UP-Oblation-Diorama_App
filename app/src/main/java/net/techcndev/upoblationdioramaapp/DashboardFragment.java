package net.techcndev.upoblationdioramaapp;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.nordan.dialog.Animation;
import com.nordan.dialog.DialogType;
import com.nordan.dialog.NordanAlertDialog;
import com.nordan.dialog.NordanAlertDialogListener;
import com.nordan.dialog.NordanLoadingDialog;

import net.techcndev.upoblationdioramaapp.databinding.FragmentDashboardBinding;

import org.apache.commons.text.WordUtils;

import java.util.Objects;


public class DashboardFragment extends Fragment {

    public static final String LOG_TAG = DashboardFragment.class.getSimpleName();

    FragmentDashboardBinding binding;
    GlobalObject globalObject;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    private FirebaseAuth mAuth;
    String userDevice;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(LOG_TAG, LOG_TAG + " onViewCreated");
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        globalObject.batteryPercentRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    binding.batteryText.setText(String.valueOf(snapshot.getValue())+"%");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Snackbar.make(binding.mainLayout, "Error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
            }
        });

        globalObject.deviceModeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    binding.modeText.setText(snapshot.getValue(String.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Snackbar.make(binding.mainLayout, "Error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
            }
        });

        globalObject.powerSourceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Log.d(LOG_TAG, "Power Source: " + snapshot.getValue(String.class));
                    binding.powerText.setText(snapshot.getValue(String.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Snackbar.make(binding.mainLayout, "Error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
            }
        });

        globalObject.waterLevelRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    binding.waterText.setText(snapshot.getValue(String.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Snackbar.make(binding.getRoot(), "Error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.controlsBtn.setOnClickListener(v -> {
            Log.d(LOG_TAG, "!!!!!!!!!userDevice: " + userDevice);
            Dialog loading = NordanLoadingDialog.createLoadingDialog(getActivity(), "Loading…");
            loading.show();
            if (globalObject.isReliableInternetAvailable()) {
                if (!userDevice.isBlank()) {
                    new Handler().postDelayed(loading::hide, 1000);
                    Navigation.findNavController(v).navigate(R.id.action_dashboardFragment_to_controlsFragment3);
                } else {
                    new Handler().postDelayed(loading::hide, 1000);
                    Snackbar.make(binding.getRoot(), "No Linked Device!", Snackbar.LENGTH_SHORT).show();
                }
            } else {
                new Handler().postDelayed(loading::hide, 1000);
                Snackbar.make(binding.getRoot(), "No Internet Connection", Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.settingsBtn.setOnClickListener(
                v -> Navigation.findNavController(v).navigate(R.id.action_dashboardFragment_to_settingsFragment2));

        checkAuthenticatedUser();
        startBackgroundService();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Log.d(LOG_TAG, LOG_TAG + " onAttach");
        sharedPreferences = context.getSharedPreferences("PREFS_DATA", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        globalObject = new GlobalObject(context.getApplicationContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, LOG_TAG + " onResume");
        if (!sharedPreferences.getBoolean("isWelcomed", false)) {
            editor.putBoolean("isWelcomed", true);
            editor.commit();
            showDialog(DialogType.SUCCESS, "Sign in Success", "Welcome to UP Oblation Diorama App!", Animation.POP, true, "NICE");
        }
    }

    private void startBackgroundService() {
        try {
            Log.d(LOG_TAG, "startBackgroundService");
            userDevice = sharedPreferences.getString("user_device", "");
            if (!userDevice.isBlank()) {
                boolean isNotifEnabled = sharedPreferences.getBoolean("is_notif_enabled", false);
                Log.d(LOG_TAG, "InventoryActivity isNotifEnabled: " + isNotifEnabled + ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

                if (isNotifEnabled) {
                    if (!isServiceRunning(ForegroundService.class)) {
                        Context context = getContext();
                        Intent intent = new Intent(getActivity(), ForegroundService.class);

                        if (context != null) {
                            context.startForegroundService(intent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "startBackgroundService: " + e);
        }
    }

    private boolean isServiceRunning(Class<ForegroundService> serviceClass) {
        ActivityManager activityManager = (ActivityManager) requireActivity().getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceInfo.service.getClassName().equals(serviceClass.getName())) {
                return true;
            }
        }
        return false;
    }

    private void checkInternetConnectivity() {
        if (!globalObject.isReliableInternetAvailable()) {
            showDialog(DialogType.WARNING, "No Internet Connection", "Please check your internet connection.", Animation.POP, true, "OK");
        }
    }

    private void checkAuthenticatedUser() {
        try {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Log.d(LOG_TAG, "JUST A NORMAL DAY CAPTAIN!");
                Navigation.findNavController(requireView()).navigate(R.id.action_dashboardFragment_to_signinFragment);
            } else {
                Log.d(LOG_TAG, "This is an existing user.");
                String current_name = WordUtils.capitalizeFully(mAuth.getCurrentUser().getDisplayName());
                binding.usernameText.setText(current_name);
                checkInternetConnectivity();
            }
        } catch (Exception e){
            Log.d(LOG_TAG, "Catch Error in DashboardFragment OnCreate: " + e);
        }
    }

    private void showDialog (com.nordan.dialog.DialogType dialogType, String title,
                            String message, Animation animation, boolean cancellable, String btnPos) {
        new NordanAlertDialog.Builder(requireActivity())
                .setDialogType(dialogType)
                .setAnimation(animation)
                .isCancellable(cancellable)
                .setTitle(title)
                .setMessage(message)
                .setPositiveBtnText(btnPos)
                .build().show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(LOG_TAG, LOG_TAG + " onDestroyView");
        globalObject.unregisterListener();
    }
}