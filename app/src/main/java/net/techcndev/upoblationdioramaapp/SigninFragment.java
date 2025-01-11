package net.techcndev.upoblationdioramaapp;

import static android.content.ContentValues.TAG;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.BeginSignInResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.nordan.dialog.Animation;
import com.nordan.dialog.NordanAlertDialog;
import com.nordan.dialog.NordanAlertDialogListener;
import com.nordan.dialog.DialogType;

import net.techcndev.upoblationdioramaapp.databinding.FragmentSigninBinding;

import java.util.Objects;


public class SigninFragment extends Fragment {

    FragmentSigninBinding binding;

    private FirebaseAuth mAuth;
    private SignInClient signInClient;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    GlobalObject globalObject;

    public static final String LOG_TAG = SigninFragment.class.getSimpleName();

    private final ActivityResultLauncher<IntentSenderRequest> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    handleSignInResult(result.getData());
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentSigninBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Configure Google Sign In
        signInClient = Identity.getSignInClient(requireContext());

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        binding.googleBtn.setOnClickListener(v -> {
            if (globalObject.isReliableInternetAvailable()) {
                signIn();
            } else {
                Snackbar.make(binding.mainLayout, "No Internet Connection", Snackbar.LENGTH_SHORT).show();
            }
        });

        // Display One-Tap Sign In if user isn't logged in
        boolean res = !globalObject.isReliableInternetAvailable();
        Log.d(LOG_TAG, "isReliableInternetAvailable: " + res);
        if (res) {
            Log.d(LOG_TAG, "No Internet Connection");
            showNetworkErrorDialog();
        } else {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                oneTapSignIn();

            } else {
                Log.d(LOG_TAG, "This is an existing user.");
                Log.d(LOG_TAG, LOG_TAG + "update_active_user: " + currentUser.getEmail());

            }
        }
//        String email = sharedPreferences.getString("current_email", null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        sharedPreferences = context.getSharedPreferences("PREFS_DATA", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        globalObject = new GlobalObject(context.getApplicationContext());
    }

    private void checkInternetConnectivity() {
//        boolean res = !globalObject.isReliableInternetAvailable();
        if (!globalObject.isReliableInternetAvailable()) {
            showNetworkErrorDialog();
        }
    }

    private void showNetworkErrorDialog() {
        showDialog(DialogType.WARNING, "No Internet Connection", "Please check your internet connection.", Animation.POP, true, "OK");
    }

    private void handleSignInResult(Intent data) {
        try {
            // Google Sign In was successful, authenticate with Firebase
            SignInCredential credential = signInClient.getSignInCredentialFromIntent(data);
            String idToken = credential.getGoogleIdToken();
            Log.d(LOG_TAG, "firebaseAuthWithGoogle:" + credential.getId());
            firebaseAuthWithGoogle(idToken);
        } catch (ApiException e) {
            // Google Sign In failed, update UI appropriately
            Log.w(LOG_TAG, "Google sign in failed", e);
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        showProgressBar();
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(requireActivity(), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            checkDevice();
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Snackbar.make(binding.mainLayout, "Authentication Failed.", Snackbar.LENGTH_SHORT).show();

                        }

                        hideProgressBar();
                    }
                });
    }

    private void checkDevice() {
        String current_email = mAuth.getCurrentUser().getEmail();
        if (current_email != null && !current_email.isBlank()) {
            globalObject.rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot childSnapshot : dataSnapshot.getChildren()) {
                        String value = childSnapshot.child("registeredUser").getValue(String.class);

                        if (Objects.equals(value, current_email)) {
                            // If the value matches, do something
                            editor.putString("user_device", childSnapshot.getKey());
                            editor.commit();
                            Log.d(LOG_TAG, "Node with specific value found: " + childSnapshot.getKey());
                            break;
                        }
                    }
                    String user_device = sharedPreferences.getString("user_device", "");
                    if (!user_device.isBlank()) {
                        Navigation.findNavController(requireView()).navigate(R.id.action_signinFragment_to_dashboardFragment);
                    } else {
                        showDialog(DialogType.INFORMATION, "No Linked Device", "In Settings, scan your device QR code to link your device to UP Oblation Diorama App.", Animation.POP, true, "OK");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                }
            });
        }
    }

    private void hideProgressBar() {
        binding.progressBar.setVisibility(View.INVISIBLE);
    }

    private void showProgressBar() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.progressBar.setIndeterminate(true);
    }

    private void signIn() {
        GetSignInIntentRequest signInRequest = GetSignInIntentRequest.builder()
                .setServerClientId(getString(R.string.client_id))
                .build();

        signInClient.getSignInIntent(signInRequest)
                .addOnSuccessListener(new OnSuccessListener<PendingIntent>() {
                    @Override
                    public void onSuccess(PendingIntent pendingIntent) {
                        launchSignIn(pendingIntent);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Google Sign-in failed", e);
                        Snackbar.make(binding.mainLayout, "Google Sign-in failed", Snackbar.LENGTH_SHORT).show();
                    }
                });
    }

    private void oneTapSignIn() {
        // Configure One Tap UI
        BeginSignInRequest oneTapRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true)
                                .setServerClientId(BuildConfig.API_KEY) // R.string.client_id
                                .setFilterByAuthorizedAccounts(true)
                                .build()
                )
                .build();

        // Display the One Tap UI
        signInClient.beginSignIn(oneTapRequest)
                .addOnSuccessListener(new OnSuccessListener<BeginSignInResult>() {
                    @Override
                    public void onSuccess(BeginSignInResult beginSignInResult) {
                        launchSignIn(beginSignInResult.getPendingIntent());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // No saved credentials found. Launch the One Tap sign-up flow, or
                        // do nothing and continue presenting the signed-out UI.
                    }
                });

    }

    private void launchSignIn(PendingIntent pendingIntent) {
        try {
            IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(pendingIntent)
                    .build();
            signInLauncher.launch(intentSenderRequest);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't start Sign In: " + e.getLocalizedMessage());
        }
    }

    private void signOut() {
        // Firebase sign out
        mAuth.signOut();

        // Google sign out
        signInClient.signOut().addOnCompleteListener(requireActivity(),
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        //updateUI(null);
                    }
                });
    }

    private void showDialog (DialogType dialogType, String title,
                             String message, Animation animation, boolean cancellable, String btnPos) {
        new NordanAlertDialog.Builder(requireActivity())
                .setDialogType(dialogType)
                .setAnimation(animation)
                .isCancellable(cancellable)
                .setTitle(title)
                .setMessage(message)
                .setPositiveBtnText(btnPos)
                .onPositiveClicked(new NordanAlertDialogListener() {
                    @Override
                    public void onClick() {
//                        Toast.makeText(requireContext(), "Positive button clicked", Toast.LENGTH_SHORT).show();
                    }
                })
                .build().show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        globalObject.unregisterListener();
    }

}