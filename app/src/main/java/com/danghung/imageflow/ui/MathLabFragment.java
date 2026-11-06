package com.danghung.imageflow.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.danghung.imageflow.databinding.FragmentMathLabBinding;
import com.danghung.imageflow.engine.MathEngines;
import com.danghung.imageflow.util.CpuUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MathLabFragment extends Fragment {

    private static final int BURST_TASK_COUNT = 900;
    private static final String[] ENGINES = {
            "1. Raw Thread",
            "2. HandlerThread",
            "3. AsyncTask"
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FragmentMathLabBinding binding;
    private ExecutorService labExecutor;
    private Runnable cancelCleanup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMathLabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, ENGINES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerEngine.setAdapter(adapter);

        binding.btnRunFibonacci.setOnClickListener(v -> runFibonacci());
        binding.btnRunBurst.setOnClickListener(v -> runBurstTasks());
    }

    private Executor getSelectedExecutor(int engineIndex) {
        switch (engineIndex) {
            case 0: // 1. Raw Thread
                List<Thread> rawThreads = new ArrayList<>();
                cancelCleanup = () -> {
                    for (Thread t : rawThreads) t.interrupt();
                };
                return task -> {
                    Thread t = new Thread(task, "RawThread-Worker");
                    rawThreads.add(t);
                    t.start();
                };

            case 1: // 2. HandlerThread
                HandlerThread ht = new HandlerThread("MathLab-HT");
                ht.start();
                Handler handler = new Handler(ht.getLooper());
                cancelCleanup = ht::quitSafely;
                return handler::post;

            case 2: // 3. AsyncTask
            default:
                cancelCleanup = null;
                return AsyncTask.THREAD_POOL_EXECUTOR::execute;
        }
    }

    // Bài toán 1: Tính Fibonacci F(1.000.000)
    private void runFibonacci() {
        int index = binding.spinnerEngine.getSelectedItemPosition();
        startTaskUI("Tính Fibonacci F(1.000.000)", ENGINES[index]);

        Executor executor = getSelectedExecutor(index);
        long startTime = SystemClock.elapsedRealtime();

        executor.execute(() -> {
            try {
                appendLog("Luồng thực thi: " + CpuUtils.getThreadAndCore());
                appendLog("Đang tính Fast-Doubling & nhân số lớn...");

                BigInteger result = MathEngines.fibonacciFastDoubling(1_000_000);
                String str = result.toString();
                String preview = str.substring(0, Math.min(35, str.length()));

                appendLog("✓ F(1.000.000): " + preview + "...");
                appendLog(String.format(Locale.US, "✓ Tổng số chữ số: %,d chữ số", str.length()));
            } catch (Exception e) {
                appendLog("❌ Lỗi: " + e.getMessage());
            } finally {
                finishTaskUI(SystemClock.elapsedRealtime() - startTime);
            }
        });
    }

    // Bài toán 2: Chạy Burst 900 micro-tasks đo chi phí tạo luồng
    private void runBurstTasks() {
        int index = binding.spinnerEngine.getSelectedItemPosition();
        startTaskUI("Burst 900 Micro-tasks (Đo Overhead)", ENGINES[index]);

        Executor executor = getSelectedExecutor(index);
        long startTime = SystemClock.elapsedRealtime();

        // Thu thập thông tin luồng mẫu và toàn bộ core tham gia gánh 900 tasks
        Set<Integer> coresUsed = Collections.synchronizedSet(new HashSet<>());
        final String[] firstThreadSample = new String[1];

        labExecutor = Executors.newSingleThreadExecutor();
        labExecutor.execute(() -> {
            try {
                CountDownLatch latch = new CountDownLatch(BURST_TASK_COUNT);

                for (int i = 1; i <= BURST_TASK_COUNT; i++) {
                    final int taskId = i;
                    executor.execute(() -> {
                        try {
                            // Ghi lại thread info của task đầu tiên
                            if (firstThreadSample[0] == null) {
                                firstThreadSample[0] = CpuUtils.getThreadAndCore();
                            }

                            int coreId = CpuUtils.getCurrentCoreId();
                            if (coreId >= 0) {
                                coresUsed.add(coreId);
                            }

                            MathEngines.countPrimes(2, 5_000);

                            if (taskId % 150 == 0 || taskId == BURST_TASK_COUNT) {
                                appendLog(String.format(
                                        Locale.US,
                                        "• Tiến độ: %d/%d tasks | Đang chạy tại %s",
                                        taskId, BURST_TASK_COUNT, CpuUtils.getThreadAndCore()
                                ));
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();

                appendLog("\n✓ Thông tin luồng mẫu: " + firstThreadSample[0]);
                appendLog("✓ Tập CPU Core ghi nhận: " + coresUsed);
                appendLog("✓ Hoàn thành xử lý toàn bộ " + BURST_TASK_COUNT + " tasks!");

            } catch (Exception e) {
                appendLog("❌ Lỗi trong quá trình chạy: " + e.getMessage());
            } finally {
                finishTaskUI(SystemClock.elapsedRealtime() - startTime);
            }
        });
    }

    private void startTaskUI(String title, String engineName) {
        cancelCurrentTask();
        binding.tvConsole.setText(String.format(Locale.US, "▶ BẮT ĐẦU: %s\nCơ chế: %s\n----------------------------------------\n", title, engineName));
        binding.pbLoading.setVisibility(View.VISIBLE);
        setViewsEnabled(false);
    }

    private void finishTaskUI(long elapsedMs) {
        mainHandler.post(() -> {
            if (binding == null) return;
            binding.tvConsole.append(String.format(Locale.US, "\n🏁 HOÀN TẤT TRONG: %,d ms\n", elapsedMs));
            binding.pbLoading.setVisibility(View.GONE);
            setViewsEnabled(true);
            cancelCurrentTask();
        });
    }

    private void appendLog(String message) {
        mainHandler.post(() -> {
            if (binding == null) return;
            binding.tvConsole.append(message + "\n");
            binding.scrollConsole.post(() -> binding.scrollConsole.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void setViewsEnabled(boolean enabled) {
        binding.btnRunFibonacci.setEnabled(enabled);
        binding.btnRunBurst.setEnabled(enabled);
        binding.spinnerEngine.setEnabled(enabled);
    }

    private void cancelCurrentTask() {
        if (cancelCleanup != null) {
            cancelCleanup.run();
            cancelCleanup = null;
        }
        if (labExecutor != null) {
            labExecutor.shutdownNow();
            labExecutor = null;
        }
    }

    @Override
    public void onDestroyView() {
        cancelCurrentTask();
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }
}