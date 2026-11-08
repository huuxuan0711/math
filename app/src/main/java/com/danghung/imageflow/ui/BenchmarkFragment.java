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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BenchmarkFragment extends Fragment {

    private static final long MAX = 16_000_000L;

    // 0 = Single Thread
    private static final int[] WORKERS = {0, 2, 4, 8, 16};

    private static final int[] COLORS = {Color.parseColor("#607D8B"), // Single
            Color.parseColor("#388E3C"), // 2 Workers
            Color.parseColor("#F57C00"), // 4 Workers
            Color.parseColor("#7B1FA2"), // 8 Workers
            Color.parseColor("#D32F2F")  // 16 Workers
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

        binding.tvCpuInfo.setText(String.format(Locale.US,
                "CPU Core khả dụng: %d\nAndroid: %s",
                CpuUtils.getAvailableProcessors(),
                Build.VERSION.RELEASE));

        binding.benchmarkChart.setItems(new ArrayList<>());
        binding.btnRunBenchmark.setOnClickListener(v -> startBenchmark());
    }


    private void startBenchmark() {
        if (benchmarkExecutor != null) {
            benchmarkExecutor.shutdownNow();
        }

        int currentRun = ++runId;

        binding.btnRunBenchmark.setEnabled(false);
        binding.pbBenchmark.setVisibility(View.VISIBLE);
        binding.benchmarkChart.setItems(new ArrayList<>());

        binding.tvBenchmarkDetails.setText(String.format(Locale.US, "BENCHMARK ĐA NHÂN\n" + "Bài toán: Đếm số nguyên tố từ 2 đến %,d\n" + "Cấu hình: Single Thread, 2, 4, 8, 16 Workers\n" + "----------------------------------------\n", MAX));

        benchmarkExecutor = Executors.newSingleThreadExecutor();
        benchmarkExecutor.execute(() -> runBenchmark(currentRun));
    }

    private void runBenchmark(int currentRun) {
        List<BenchmarkChartView.BarItem> chartItems = new ArrayList<>();

        try {
            for (int i = 0; i < WORKERS.length; i++) {
                if (Thread.currentThread().isInterrupted()) return;
                int workers = WORKERS[i];
                String label = workers == 0 ? "Single" : workers + "W";

                Set<Integer> cores = Collections.synchronizedSet(new HashSet<>());
                appendLog(currentRun, "Đang thử nghiệm: " + label + "...\n");

                long start = SystemClock.elapsedRealtime();
                int totalPrimes = workers == 0 ? countSingleThread(cores) : countMultiThread(workers, cores);
                long elapsed = SystemClock.elapsedRealtime() - start;
                Log.i("BenchmarkFragment", String.format(Locale.US,
                        "[%s] Hoàn tất sau %,d ms | Tổng số nguyên tố: %,d | Cores: %s",
                        label, elapsed, totalPrimes, cores));
                chartItems.add(new BenchmarkChartView.BarItem(label, elapsed, COLORS[i]));
                appendLog(currentRun, String.format(Locale.US, "✓ %s: %,d ms | Số nguyên tố: %,d\n" + "  CPU Core ghi nhận: %s\n", label, elapsed, totalPrimes, cores));
                updateChart(currentRun, chartItems);
            }


        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } catch (ExecutionException e) {
            appendLog(currentRun, "❌ Lỗi benchmark: " + e.getCause() + "\n");
        } finally {
            finishBenchmark(currentRun);
        }
    }

    // Single Thread: Xử lý toàn bộ dữ liệu tuần tự
    private int countSingleThread(Set<Integer> cores) {
        recordCore(cores);

        int result = MathEngines.countPrimes(2, MAX);

        recordCore(cores);
        return result;
    }

    // Multi Worker: Chia dữ liệu cho nhiều Worker
    private int countMultiThread(int workers, Set<Integer> cores) throws InterruptedException, ExecutionException {

        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            long total = MAX - 1;
            long range = total / workers;

            for (int i = 0; i < workers; i++) {
                long from = 2 + i * range;
                long to = (i == workers - 1) ? MAX : from + range - 1;

                futures.add(pool.submit(() -> {
                    recordCore(cores);
                    int result = MathEngines.countPrimes(from, to);
                    recordCore(cores);
                    return result;
                }));}

            int totalPrimes = 0;
            for (Future<Integer> future : futures) {totalPrimes += future.get();}
            return totalPrimes;
        } finally {
            pool.shutdownNow();
        }
    }

    private void recordCore(Set<Integer> cores) {
        int core = CpuUtils.getCurrentCoreId();

        if (core >= 0) {
            cores.add(core);
        }
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
