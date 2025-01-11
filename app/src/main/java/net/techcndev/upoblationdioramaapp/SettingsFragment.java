package net.techcndev.upoblationdioramaapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.nordan.dialog.Animation;
import com.nordan.dialog.DialogType;
import com.nordan.dialog.NordanAlertDialog;

import net.techcndev.upoblationdioramaapp.databinding.FragmentSettingsBinding;

import java.util.Objects;


public class SettingsFragment extends Fragment {

    FragmentSettingsBinding binding;

    public static final String LOG_TAG = SettingsFragment.class.getSimpleName();
    private FirebaseAuth mAuth;
    private SignInClient signInClient;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    GlobalObject globalObject;

    private String userEmail;
    String user_device;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        sharedPreferences = context.getSharedPreferences("PREFS_DATA", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        globalObject = new GlobalObject(context.getApplicationContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        user_device = sharedPreferences.getString("user_device", "");

        binding.settingsBackBtn.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_settingsFragment_to_dashboardFragment2));

        binding.aboutBtn.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_settingsFragment3_to_aboutFragment));

        binding.logoutBtn.setOnClickListener(v -> {
            new NordanAlertDialog.Builder(requireActivity())
                    .setDialogType(DialogType.QUESTION)
                    .setAnimation(Animation.POP)
                    .setTitle("Sign out")
                    .setMessage("Continue to Sign out?")
                    .setPositiveBtnText("YES")
                    .setNegativeBtnText("NO")
                    .onPositiveClicked(()->signOut(v))
                    .onNegativeClicked(()-> Snackbar.make(binding.mainLayout, "Sign out cancelled", Snackbar.LENGTH_SHORT).show())
                    .build().show();
        });

        binding.contextMenuImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    showPopupMenu(v);
            }
        });

        binding.notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!user_device.isBlank()) {
                    if (isChecked) {
                        editor.putBoolean("is_notif_enabled", true);
                        editor.commit();
                        startBackgroundService();
                        Snackbar.make(binding.mainLayout, "Notification Enabled", Snackbar.LENGTH_SHORT).show();
                    } else {
                        editor.putBoolean("is_notif_enabled", false);
                        editor.commit();
                        stopBackgroundService();
                        Snackbar.make(binding.mainLayout, "Notification Disabled", Snackbar.LENGTH_SHORT).show();
                    }
                    Log.d(LOG_TAG, "isChecked: " + isChecked);
                }
            }
        });

        // Configure Google Sign In
        signInClient = Identity.getSignInClient(requireContext());

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        userEmail = mAuth.getCurrentUser().getEmail();

        if (!user_device.isBlank()) {
            binding.deviceNameTextview.setText(user_device);
        }

        boolean status = sharedPreferences.getBoolean("is_notif_enabled",false);
        binding.notificationSwitch.setChecked(status);

        binding.emailTextview.setText(mAuth.getCurrentUser().getEmail());
    }

    private void startBackgroundService() {
        Context context = getContext();
        Intent intent = new Intent(getActivity(), ForegroundService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void stopBackgroundService() {
        Context context = getContext();
        Intent intent = new Intent(getActivity(), ForegroundService.class);
        context.stopService(intent);
    }

    private void showPopupMenu(View view) {
        PopupMenu popupMenu = new PopupMenu(getContext(), view);
        MenuInflater inflater = popupMenu.getMenuInflater();
        inflater.inflate(R.menu.menu_item, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.change_device) {
                if (globalObject.isReliableInternetAvailable()) {
//                    Toast.makeText(getActivity(), "Change device", Toast.LENGTH_SHORT).show();
                    scanCode();
                    return true;
                } else {
                    Snackbar.make(binding.mainLayout, "No Internet Connection", Snackbar.LENGTH_SHORT).show();
                    return false;
                }

            } else if (itemId == R.id.remove_device) {
                if (globalObject.isReliableInternetAvailable()) {
//                    Toast.makeText(getActivity(), "Remove device", Toast.LENGTH_SHORT).show();
                    removeDevice();
                    return true;
                } else {
                    Snackbar.make(binding.mainLayout, "No Internet Connection", Snackbar.LENGTH_SHORT).show();
                    return false;
                }
            } else {
                return false;
            }
        });

        popupMenu.show();
    }

    private void scanCode() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Volume up to flash on");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barLaucher.launch(options);
    }

    ActivityResultLauncher<ScanOptions> barLaucher = registerForActivityResult(new ScanContract(), result->
    {
        try {
            String deviceName = result.getContents();
            if(deviceName != null && !deviceName.isBlank()) {
                if (globalObject.isReliableInternetAvailable()) {
                    globalObject.rootRef.child(deviceName).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Log.d(LOG_TAG, "snapshot.child(deviceName).exists(): " + snapshot.child(deviceName).exists());
                            if (snapshot.exists()) {
                                String user = snapshot.child("registeredUser").getValue(String.class);
                                if (Objects.equals(user, userEmail)) {
                                    showDialog(DialogType.WARNING, "Device Already Used", "The device with model name \"" + deviceName + "\" is already used by you.", Animation.POP, true, "OK");
                                } else if (user != null && !user.isBlank()) {
                                    showDialog(DialogType.WARNING, "Device Already Used", "The device with model name \"" + deviceName + "\" is already used by other user.", Animation.POP, true, "OK");
                                } else {
                                    editor.putString("user_device", deviceName);
                                    editor.commit();
                                    globalObject.registeredUserRef.setValue(userEmail);
                                    binding.deviceNameTextview.setText(deviceName);
                                    showDialog(DialogType.SUCCESS, "Device Set", "The device with model name \"" + deviceName + "\" has been set as your new device.", Animation.POP, true, "OK");
                                }
                            } else {
                                showDialog(DialogType.WARNING, "Device Not Found", "The device with model name \"" + deviceName + "\" does not exist.", Animation.POP, true, "OK");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Snackbar.make(binding.mainLayout, "Database Error: " + error.getMessage(), Snackbar.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    showDialog(DialogType.WARNING, "QRCode Scan Failed", "Please check your internet connection.", Animation.POP, true, "OK");
                }
            } else {
                showDialog(DialogType.WARNING, "QR Code Scan Failed", "Please try again.", Animation.POP, true, "OK");
            }
        } catch (Exception e) {
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    });

    private void removeDevice() {
        if (globalObject.isReliableInternetAvailable()) {
            binding.deviceNameTextview.setText("N/A");
            globalObject.registeredUserRef.setValue("");

            editor.putString("user_device", "");
            editor.commit();
            showDialog(DialogType.INFORMATION, "Device Removed", "Your device has been removed successfully.", Animation.POP, true, "OK");
        } else {
            showDialog(DialogType.WARNING, "Device Remove Failed", "Please check your internet connection.", Animation.POP, true, "OK");
        }
    }
    private void signOut(View v) {
        // Firebase sign out
        mAuth.signOut();

        // Google sign out
        signInClient.signOut().addOnCompleteListener(requireActivity(),
        new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                stopBackgroundService();

                editor.putString("user_device", "");
                editor.commit();
                editor.putBoolean("isWelcomed", false);
                editor.commit();
                editor.putBoolean("is_notif_enabled", false);
                editor.commit();

                Navigation.findNavController(v).navigate(R.id.action_settingsFragment3_to_signinFragment);
                showDialog(DialogType.SUCCESS, "Sign-out Success", "You have successfully signed out.", Animation.POP, true, "OK");
            }
        });
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
        globalObject.unregisterListener();
    }
}