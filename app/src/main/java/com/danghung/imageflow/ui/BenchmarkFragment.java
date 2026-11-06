package com.danghung.imageflow.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
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

import com.danghung.imageflow.databinding.FragmentBenchmarkBinding;
import com.danghung.imageflow.engine.MathEngines;
import com.danghung.imageflow.util.CpuUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BenchmarkFragment extends Fragment {

    private static final String TAG = "BenchmarkFragment";
    private static final long MAX = 2_000_000L;
    private static final int[] WORKER_CONFIGS = {1, 2, 4, 8, 16};
    private static final int[] BAR_COLORS = {
            Color.parseColor("#1976D2"),
            Color.parseColor("#388E3C"),
            Color.parseColor("#F57C00"),
            Color.parseColor("#7B1FA2"),
            Color.parseColor("#D32F2F")
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FragmentBenchmarkBinding binding;
    private ExecutorService benchmarkExecutor;
    private int runId = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBenchmarkBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupCpuInfo();
        binding.benchmarkChart.setItems(new ArrayList<>());
        binding.btnRunBenchmark.setOnClickListener(v -> startBenchmark());
    }

    private void setupCpuInfo() {
        int cores = CpuUtils.getAvailableProcessors();
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

        if (manager != null) {
            manager.getMemoryInfo(memoryInfo);
        }

        long realRamMB = memoryInfo.totalMem / (1024L * 1024L);
        long allocatedRamMB = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        String info = String.format(
                Locale.US,
                "CPU Core khả dụng: %d\nRAM thực: %,d MB\nRAM giới hạn App Heap: %,d MB\nAndroid: %s",
                cores, realRamMB, allocatedRamMB, Build.VERSION.RELEASE
        );

        Log.i(TAG, "Hardware Info -> Cores: " + cores + ", RAM: " + realRamMB + "MB, HeapLimit: " + allocatedRamMB + "MB");
        binding.tvCpuInfo.setText(info);
    }

    private void startBenchmark() {
        if (benchmarkExecutor != null) {
            Log.d(TAG, "Cancelling previous benchmark execution...");
            benchmarkExecutor.shutdownNow();
        }

        final int currentRun = ++runId;
        Log.i(TAG, "Starting Benchmark Run #" + currentRun + " with MAX=" + MAX);

        binding.btnRunBenchmark.setEnabled(false);
        binding.pbBenchmark.setVisibility(View.VISIBLE);

        binding.tvBenchmarkDetails.setText(String.format(
                Locale.US,
                "BENCHMARK ĐA NHÂN\nBài toán: Đếm số nguyên tố từ 2 đến %,d\nCấu hình: 1, 2, 4, 8 và 16 Worker(s)\n----------------------------------------\n",
                MAX
        ));

        binding.benchmarkChart.setItems(new ArrayList<>());

        benchmarkExecutor = Executors.newSingleThreadExecutor();
        benchmarkExecutor.submit(() -> runBenchmark(currentRun));
    }

    private void runBenchmark(int currentRun) {
        List<BenchmarkChartView.BarItem> chartItems = new ArrayList<>();

        for (int i = 0; i < WORKER_CONFIGS.length; i++) {
            if (Thread.currentThread().isInterrupted()) {
                Log.w(TAG, "Benchmark Run #" + currentRun + " was interrupted.");
                return;
            }

            int workers = WORKER_CONFIGS[i];
            int color = BAR_COLORS[i];

            Log.d(TAG, "Running configuration: " + workers + " Worker(s)");
            appendLog(currentRun, "Đang thử nghiệm với " + workers + " Worker(s)...\n");

            long start = SystemClock.elapsedRealtime();
            Set<Integer> coresUsed = new HashSet<>();
            ExecutorService pool = Executors.newFixedThreadPool(workers);
            List<Future<Integer>> futures = new ArrayList<>();

            long total = MAX - 1;
            long range = total / workers;

            for (int j = 0; j < workers; j++) {
                final long from = 2 + j * range;
                final long to = (j == workers - 1) ? MAX : from + range - 1;

                Callable<Integer> task = () -> {
                    int startCore = CpuUtils.getCurrentCoreId();
                    if (startCore >= 0) {
                        synchronized (coresUsed) { coresUsed.add(startCore); }
                    }

                    int primes = MathEngines.countPrimes(from, to);

                    int endCore = CpuUtils.getCurrentCoreId();
                    if (endCore >= 0) {
                        synchronized (coresUsed) { coresUsed.add(endCore); }
                    }
                    return primes;
                };

                futures.add(pool.submit(task));
            }

            int totalPrimes = 0;

            try {
                for (Future<Integer> future : futures) {
                    totalPrimes += future.get();
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Worker pool interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                pool.shutdownNow();
                return;
            } catch (Exception e) {
                Log.e(TAG, "Error executing benchmark tasks: " + e.getMessage(), e);
                pool.shutdownNow();
                appendLog(currentRun, "❌ Lỗi benchmark: " + e.getMessage() + "\n");
                finishBenchmark(currentRun);
                return;
            }

            pool.shutdown();

            long elapsed = SystemClock.elapsedRealtime() - start;
            chartItems.add(new BenchmarkChartView.BarItem(workers + "W", elapsed, color));

            String result = String.format(
                    Locale.US,
                    "✓ %d Worker(s): %,d ms | Số nguyên tố: %,d\n  CPU Core ghi nhận: %s\n",
                    workers, elapsed, totalPrimes, coresUsed
            );

            Log.i(TAG, String.format(Locale.US, "Config %dW done in %,d ms on Cores %s (Primes: %,d)", workers, elapsed, coresUsed, totalPrimes));

            appendLog(currentRun, result);
            updateChart(currentRun, chartItems);

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        appendLog(currentRun, "\n🏁 Đã hoàn thành tất cả các cấu hình thử nghiệm!\n");
        finishBenchmark(currentRun);
    }

    private void appendLog(int currentRun, String message) {
        mainHandler.post(() -> {
            if (binding == null || currentRun != runId) return;
            binding.tvBenchmarkDetails.append(message);
            binding.scrollBenchmark.post(() -> binding.scrollBenchmark.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void updateChart(int currentRun, List<BenchmarkChartView.BarItem> items) {
        List<BenchmarkChartView.BarItem> copy = new ArrayList<>(items);
        mainHandler.post(() -> {
            if (binding == null || currentRun != runId) return;
            binding.benchmarkChart.setItems(copy);
        });
    }

    private void finishBenchmark(int currentRun) {
        mainHandler.post(() -> {
            if (binding == null || currentRun != runId) return;
            binding.btnRunBenchmark.setEnabled(true);
            binding.pbBenchmark.setVisibility(View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        Log.d(TAG, "onDestroyView - Cleaning up background executor & handlers");
        runId++;

        if (benchmarkExecutor != null) {
            benchmarkExecutor.shutdownNow();
            benchmarkExecutor = null;
        }

        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }
}