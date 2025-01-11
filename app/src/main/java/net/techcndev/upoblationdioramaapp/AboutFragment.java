package net.techcndev.upoblationdioramaapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.techcndev.upoblationdioramaapp.databinding.FragmentAboutBinding;
import net.techcndev.upoblationdioramaapp.databinding.FragmentControlsBinding;


public class AboutFragment extends Fragment {

    FragmentAboutBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.aboutBackBtn.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_aboutFragment_to_settingsFragment3));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
//        globalObject.unregisterListener();
    }
}