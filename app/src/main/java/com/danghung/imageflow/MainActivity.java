package com.danghung.imageflow;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.danghung.imageflow.databinding.ActivityMainBinding;
import com.danghung.imageflow.ui.BenchmarkFragment;
import com.danghung.imageflow.ui.MathLabFragment;
import com.danghung.imageflow.ui.SyncLabFragment;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private static final String[] TAB_TITLES = {
        "🧮 Tính Toán",
        "🔒 Đồng Bộ Hóa",
        "⚡ Đa Nhân"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        binding.viewPager.setAdapter(new MathLabPagerAdapter(this));
        binding.viewPager.setOffscreenPageLimit(2);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
            (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private static class MathLabPagerAdapter extends FragmentStateAdapter {
        public MathLabPagerAdapter(FragmentActivity fa) { super(fa); }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new MathLabFragment();
                case 1: return new SyncLabFragment();
                case 2: return new BenchmarkFragment();
                default: return new MathLabFragment();
            }
        }

        @Override
        public int getItemCount() { return 3; }
    }
}

