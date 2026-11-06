package com.danghung.imageflow.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
import java.util.function.IntSupplier;

public class SyncLabFragment extends Fragment {

    private static final String TAG = "SyncLabFragment";

    private static final int THREADS = 4;
    private static final int INCREMENTS = 100_000;
    private static final int EXPECTED = THREADS * INCREMENTS; // 400.000

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger atomicCounter = new AtomicInteger();
    private FragmentSyncLabBinding binding;
    private ExecutorService executor;
    private int unsafeCounter;
    private volatile int volatileCounter;
    private int syncCounter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
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
        Log.i(TAG, "Starting Synchronization Benchmark. Expected value: " + EXPECTED);
        binding.btnRunSyncDemo.setEnabled(false);
        binding.pbSyncDemo.setVisibility(View.VISIBLE);

        resetUI();

        executor.submit(() -> {
            try {
                Log.d(TAG, "Testing: 1. Unsafe Counter...");
                TestResult unsafe = runTest(() -> unsafeCounter = 0, () -> unsafeCounter++, () -> unsafeCounter);
                Log.d(TAG, "Unsafe Result: " + unsafe.value + " (" + unsafe.elapsed + " ms)");

                Log.d(TAG, "Testing: 2. Volatile Counter...");
                TestResult volatileResult = runTest(() -> volatileCounter = 0, () -> volatileCounter++, () -> volatileCounter);
                Log.d(TAG, "Volatile Result: " + volatileResult.value + " (" + volatileResult.elapsed + " ms)");

                Log.d(TAG, "Testing: 3. Synchronized Method...");
                TestResult sync = runTest(() -> syncCounter = 0, this::synchronizedIncrement, () -> syncCounter);
                Log.d(TAG, "Synchronized Result: " + sync.value + " (" + sync.elapsed + " ms)");

                Log.d(TAG, "Testing: 4. AtomicInteger (CAS)...");
                TestResult atomic = runTest(() -> atomicCounter.set(0), atomicCounter::incrementAndGet, atomicCounter::get);
                Log.d(TAG, "Atomic Result: " + atomic.value + " (" + atomic.elapsed + " ms)");

                mainHandler.post(() -> showResults(unsafe, volatileResult, sync, atomic));

            } catch (InterruptedException e) {
                Log.w(TAG, "Sync test interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        });
    }

    private TestResult runTest(Runnable reset, Runnable increment, IntSupplier getResult) throws InterruptedException {
        reset.run();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch latch = new CountDownLatch(THREADS);

        long start = SystemClock.elapsedRealtime();

        try {
            for (int i = 0; i < THREADS; i++) {
                pool.execute(() -> {
                    try {
                        for (int j = 0; j < INCREMENTS; j++) {
                            increment.run();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            return new TestResult(getResult.getAsInt(), SystemClock.elapsedRealtime() - start);

        } finally {
            pool.shutdownNow();
        }
    }

    private synchronized void synchronizedIncrement() {
        syncCounter++;
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

        binding.tvNoSyncMsg.setText("Đang chạy thử nghiệm...");
    }

    private void showResults(TestResult unsafe, TestResult volatileResult, TestResult sync, TestResult atomic) {
        if (binding == null) return;

        setResult(binding.tvNoSyncResult, binding.tvNoSyncTime, unsafe);
        setResult(binding.tvVolatileResult, binding.tvVolatileTime, volatileResult);
        setResult(binding.tvSyncResult, binding.tvSyncTime, sync);
        setResult(binding.tvAtomicResult, binding.tvAtomicTime, atomic);

        int lost = EXPECTED - unsafe.value;
        Log.i(TAG, "Sync comparison finished. Data lost in Unsafe: " + lost);

        binding.tvNoSyncMsg.setText(lost > 0 ? "Race Condition: mất " + lost + " lần tăng." : "Lần chạy này không ghi nhận mất dữ liệu.");
        binding.tvNoSyncResult.setTextColor(requireContext().getColor(lost > 0 ? R.color.status_failed : R.color.text_primary));

        binding.pbSyncDemo.setVisibility(View.GONE);
        binding.btnRunSyncDemo.setEnabled(true);
    }

    private void setResult(android.widget.TextView resultView, android.widget.TextView timeView, TestResult result) {
        resultView.setText(NumberFormat.getInstance(Locale.US).format(result.value));
        timeView.setText(result.elapsed + " ms");
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView - Shutting down sync test executor");
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