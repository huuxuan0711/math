package com.danghung.imageflow.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.danghung.imageflow.R;
import com.danghung.imageflow.databinding.FragmentSyncLabBinding;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncLabFragment extends Fragment {

    private static final int THREADS = 4;
    private static final int INCREMENTS = 100_000;
    private static final int EXPECTED = THREADS * INCREMENTS;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FragmentSyncLabBinding binding;
    private ExecutorService executor;

    private interface Counter {
        void increment();
        int get();
    }

    private static class UnsafeCounter implements Counter {
        private int c = 0;
        public void increment() { c++; }
        public int get() { return c; }
    }

    private static class VolatileCounter implements Counter {
        private volatile int c = 0;
        public void increment() { c++; }
        public int get() { return c; }
    }

    private static class SyncCounter implements Counter {
        private int c = 0;
        public synchronized void increment() { c++; }
        public synchronized int get() { return c; }
    }

    private static class AtomicCounter implements Counter {
        // Khởi tạo biến Atomic
        private final AtomicInteger c = new AtomicInteger(0);

        public void increment() {
            // Thực thi chỉ thị CAS nguyên tử mức CPU
            c.incrementAndGet();
        }
        public int get() { return c.get(); }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSyncLabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        binding.btnRunSyncDemo.setOnClickListener(v -> runTests());
    }

    private void runTests() {
        binding.btnRunSyncDemo.setEnabled(false);
        binding.pbSyncDemo.setVisibility(View.VISIBLE);
        resetUI();

        executor.execute(() -> {
            try {
                TestResult unsafe = runTest(new UnsafeCounter());
                TestResult volatileResult = runTest(new VolatileCounter());
                TestResult sync = runTest(new SyncCounter());
                TestResult atomic = runTest(new AtomicCounter());

                mainHandler.post(() -> showResults(unsafe, volatileResult, sync, atomic));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private TestResult runTest(Counter counter) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);
        long start = SystemClock.elapsedRealtime();

        try {
            for (int i = 0; i < THREADS; i++) {
                pool.execute(() -> {
                    try {
                        for (int j = 0; j < INCREMENTS; j++) {
                            counter.increment();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            return new TestResult(counter.get(), SystemClock.elapsedRealtime() - start);
        } finally {
            pool.shutdownNow();
        }
    }

    private void resetUI() {
        binding.tvNoSyncResult.setText("0");
        binding.tvVolatileResult.setText("0");
        binding.tvSyncResult.setText("0");
        binding.tvAtomicResult.setText("0");

        binding.tvNoSyncTime.setText("-- ms");
        binding.tvVolatileTime.setText("-- ms");
        binding.tvSyncTime.setText("-- ms");
        binding.tvAtomicTime.setText("-- ms");
    }

    private void showResults(TestResult unsafe, TestResult volatileResult, TestResult sync, TestResult atomic) {
        if (binding == null) return;

        setResult(binding.tvNoSyncResult, binding.tvNoSyncTime, unsafe);
        setResult(binding.tvVolatileResult, binding.tvVolatileTime, volatileResult);
        setResult(binding.tvSyncResult, binding.tvSyncTime, sync);
        setResult(binding.tvAtomicResult, binding.tvAtomicTime, atomic);

        int lost = EXPECTED - unsafe.value;
        binding.tvNoSyncResult.setTextColor(requireContext().getColor(lost > 0 ? R.color.status_failed : R.color.text_primary));

        binding.pbSyncDemo.setVisibility(View.GONE);
        binding.btnRunSyncDemo.setEnabled(true);
    }

    private void setResult(TextView resultView, TextView timeView, TestResult result) {
        resultView.setText(NumberFormat.getInstance(Locale.US).format(result.value));
        timeView.setText(result.elapsed + " ms");
    }

    @Override
    public void onDestroyView() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }

    private static class TestResult {
        final int value;
        final long elapsed;

        TestResult(int value, long elapsed) {
            this.value = value;
            this.elapsed = elapsed;
        }
    }
}