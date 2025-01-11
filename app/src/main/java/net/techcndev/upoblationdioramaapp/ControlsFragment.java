package net.techcndev.upoblationdioramaapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import net.techcndev.upoblationdioramaapp.databinding.FragmentControlsBinding;

import org.apache.commons.text.WordUtils;

import java.util.Objects;

public class ControlsFragment extends Fragment {

    private static final String LOG_TAG = ControlsFragment.class.getSimpleName();
    private PopupWindow popupWindow;
    private TextView tooltipText;

    FragmentControlsBinding binding;
    GlobalObject globalObject;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentControlsBinding.inflate(inflater, container, false);

        //// Volume seekbar tooltip value
        // Inflate the tooltip layout
        View tooltipView = inflater.inflate(R.layout.tooltip_layout, null);
        tooltipText = tooltipView.findViewById(R.id.tooltip_text);

        // Create the PopupWindow for the tooltip
        popupWindow = new PopupWindow(tooltipView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        // Set up SeekBar listener to show tooltip on thumb movement
        binding.volumeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Update tooltip with the current progress value
                    tooltipText.setText(String.valueOf(progress));

                    // Get the position of the thumb
                    int[] location = new int[2];
                    seekBar.getLocationOnScreen(location);

                    // Get thumb bounds to determine its position
                    int thumbX = location[0] + seekBar.getPaddingLeft() + (seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight()) * progress / seekBar.getMax() - (seekBar.getThumb().getIntrinsicWidth() / 2);
                    int thumbY = location[1] - tooltipView.getHeight() - 20; // Position tooltip above the thumb

                    // Update the PopupWindow's position as the thumb moves
                    popupWindow.update(thumbX, thumbY, popupWindow.getWidth(), popupWindow.getHeight());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Get the initial position of the thumb
                int[] location = new int[2];
                seekBar.getLocationOnScreen(location);

                int thumbX = location[0] + seekBar.getPaddingLeft() + (seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight()) * seekBar.getProgress() / seekBar.getMax() - (seekBar.getThumb().getIntrinsicWidth() / 2);
                int thumbY = location[1] - tooltipView.getHeight() - 20;

                // Show the tooltip at the initial position
                popupWindow.showAtLocation(seekBar, Gravity.NO_GRAVITY, thumbX, thumbY);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Dismiss the tooltip when the user stops sliding the thumb
                int scaledValue = (int) ((seekBar.getProgress() / 100.0) * 30);
//                Snackbar.make(binding.getRoot(), "Volume: " + scaledValue, Snackbar.LENGTH_LONG).show();
                globalObject.soundVolumeRef.setValue(scaledValue);
                popupWindow.dismiss();
            }
        });
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

        String[] modes = getResources().getStringArray(R.array.device_modes);
        String[] compStates = getResources().getStringArray(R.array.component_states);
        String[] musicNames = getResources().getStringArray(R.array.music_names);
        String[] switchStates = getResources().getStringArray(R.array.switch_states);

        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(requireContext(), R.layout.dropdown_item, modes);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(requireContext(), R.layout.dropdown_item, compStates);
        ArrayAdapter<String> adapter3 = new ArrayAdapter<>(requireContext(), R.layout.dropdown_item, musicNames);
        ArrayAdapter<String> adapter4 = new ArrayAdapter<>(requireContext(), R.layout.dropdown_item, switchStates);

        binding.modeTextView.setAdapter(adapter1);
        binding.waterLeftTextView.setAdapter(adapter2);
        binding.waterRightTextView.setAdapter(adapter2);
        binding.laserLeftTextView.setAdapter(adapter2);
        binding.laserRightTextView.setAdapter(adapter2);
        binding.musicTextView.setAdapter(adapter3);
        binding.lightpostTextView.setAdapter(adapter4);
        binding.spotlightTextView.setAdapter(adapter4);

        // Listeners
        binding.modeTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String mode = binding.modeTextView.getText().toString();
                globalObject.deviceModeRef.setValue(mode.replace("Mode", "").toLowerCase().trim());
                if (Objects.equals(mode, "Custom Mode")) {
                    updateDeviceMode();
                } else {
                    disableCustomControls();
                }
            }
        });

        binding.waterLeftTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.waterLeftTextView.getText().toString().replace("Blink", "").toLowerCase().trim();
                globalObject.leftLedPumpModeRef.setValue(viewText);
            }
        });

        binding.waterRightTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.waterRightTextView.getText().toString().replace("Blink", "").toLowerCase().trim();
                globalObject.rightLedPumpModeRef.setValue(viewText);
            }
        });

        binding.laserLeftTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.laserLeftTextView.getText().toString().replace("Blink", "").toLowerCase().trim();
                globalObject.leftLaserModeRef.setValue(viewText);
            }
        });

        binding.laserRightTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.laserRightTextView.getText().toString().replace("Blink", "").toLowerCase().trim();
                globalObject.rightLaserModeRef.setValue(viewText);
            }
        });

        binding.laserRightTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.laserRightTextView.getText().toString().replace("Blink", "").toLowerCase().trim();
                globalObject.rightLaserModeRef.setValue(viewText);
            }
        });

        binding.musicTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.musicTextView.getText().toString().toLowerCase().trim();
                globalObject.musicNameRef.setValue(viewText);
            }
        });

        binding.spotlightTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.spotlightTextView.getText().toString();
                boolean state = viewText.toLowerCase().trim().equals("on");
                globalObject.spotlightStateRef.setValue(state);
            }
        });

        binding.lightpostTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String viewText = binding.lightpostTextView.getText().toString();
                boolean state = viewText.toLowerCase().trim().equals("on");
                globalObject.lightpostStateRef.setValue(state);
            }
        });

        binding.controlsBackBtn.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_controlsFragment_to_dashboardFragment3));

        updateDeviceMode();
    }

    private void updateDeviceMode() {
        if (globalObject.isReliableInternetAvailable()) {
            getSoundVolume();
            getDeviceModeRef();
        } else {
            Snackbar.make(binding.getRoot(), "No Internet Connection", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void updateCustomControls() {
        if (globalObject.isReliableInternetAvailable()) {
            getLeftLaserMode();
            getLeftLedPumpMode();
            getRightLaserMode();
            getRightLedPumpMode();
            getMusicNameRef();
            getLightpostState();
            getSpotlightState();
        } else {
            Snackbar.make(binding.getRoot(), "No Internet Connection", Snackbar.LENGTH_SHORT).show();
        }
    }

    public void getDeviceModeRef() {
        try {
            globalObject.deviceModeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String mode = dataSnapshot.getValue(String.class);
                    binding.modeTextView.setText(WordUtils.capitalizeFully(mode)+" Mode",false);
                    if (Objects.equals(mode, "custom")) {
                        updateCustomControls();
                        enableCustomControls();
                    } else {
                        disableCustomControls();
                    }
                    globalObject.deviceModeRef.setValue(mode+"  ");
                    globalObject.deviceModeRef.setValue(mode);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getLeftLaserMode() {
        try {
            globalObject.leftLaserModeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String dataValue = dataSnapshot.getValue(String.class);
                    binding.laserLeftTextView.setText(WordUtils.capitalizeFully(dataValue),false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getLeftLedPumpMode() {
        try {
            globalObject.leftLedPumpModeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String dataValue = dataSnapshot.getValue(String.class);
                    binding.waterLeftTextView.setText(WordUtils.capitalizeFully(dataValue),false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getMusicNameRef() {
        try {
            globalObject.musicNameRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String dataValue = dataSnapshot.getValue(String.class);
                    binding.musicTextView.setText(WordUtils.capitalizeFully(dataValue),false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getRightLaserMode() {
        try {
            globalObject.rightLaserModeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String dataValue = dataSnapshot.getValue(String.class);
                    binding.laserRightTextView.setText(WordUtils.capitalizeFully(dataValue),false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getRightLedPumpMode() {
        try {
            globalObject.rightLedPumpModeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    String dataValue = dataSnapshot.getValue(String.class);
                    binding.waterRightTextView.setText(WordUtils.capitalizeFully(dataValue),false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getLightpostState() {
        try {
            globalObject.lightpostStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    boolean dataValue = dataSnapshot.getValue(Boolean.class);
                    String state = dataValue ? "On" : "Off";
                    binding.lightpostTextView.setText(state,false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getSpotlightState() {
        try {
            globalObject.spotlightStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    boolean dataValue = dataSnapshot.getValue(Boolean.class);
                    String state = dataValue ? "On" : "Off";
                    binding.spotlightTextView.setText(state,false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void getSoundVolume() {
        try {
            globalObject.soundVolumeRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Integer volume = dataSnapshot.getValue(Integer.class);
                    int scaledValue = (volume / 30) * 100;
                    binding.volumeSeekbar.setProgress(scaledValue);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d("FirebaseData", "Database error: " + databaseError.getMessage());
                    Toast.makeText(requireContext(), "Error: " + databaseError.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void enableCustomControls() {
        binding.musicInputLayout.setEnabled(true);
        binding.leftLaserInputLayout.setEnabled(true);
        binding.rightLaserInputLayout.setEnabled(true);
        binding.leftWaterInputLayout.setEnabled(true);
        binding.rightWaterInputLayout.setEnabled(true);
        binding.lightpostInputLayout.setEnabled(true);
        binding.spotlightInputLayout.setEnabled(true);
    }

    private void disableCustomControls() {
        binding.musicInputLayout.setEnabled(false);
        binding.leftLaserInputLayout.setEnabled(false);
        binding.rightLaserInputLayout.setEnabled(false);
        binding.leftWaterInputLayout.setEnabled(false);
        binding.rightWaterInputLayout.setEnabled(false);
        binding.lightpostInputLayout.setEnabled(false);
        binding.spotlightInputLayout.setEnabled(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
//        globalObject.unregisterListener();
    }
}
