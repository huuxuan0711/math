package com.danghung.imageflow.ui;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.danghung.imageflow.databinding.FragmentMathLabBinding;

import java.math.BigInteger;

public class MathLabFragment extends Fragment {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FragmentMathLabBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMathLabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnRunThread.setOnClickListener(v -> {
            Integer n = getValidInputN();
            if (n != null) {
                setLoadingState(true);
                runWithThread(n);
            }
        });

        binding.btnRunAsync.setOnClickListener(v -> {
            Integer n = getValidInputN();
            if (n != null) {
                setLoadingState(true);
                runWithAsyncTask(n);
            }
        });
    }

    private Integer getValidInputN() {
        String inputStr = binding.edtInputN.getText() != null ? binding.edtInputN.getText().toString().trim() : "";
        if (TextUtils.isEmpty(inputStr)) {
            Toast.makeText(requireContext(), "Vui lòng nhập số N!", Toast.LENGTH_SHORT).show();
            return null;
        }

        try {
            int n = Integer.parseInt(inputStr);
            if (n < 0) {
                Toast.makeText(requireContext(), "N phải >= 0!", Toast.LENGTH_SHORT).show();
                return null;
            }
            return n;
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Số N quá lớn!", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // 1. Chạy với Raw Thread
// 1. Chạy với Raw Thread
    private void runWithThread(int n) {
        long startTime = SystemClock.elapsedRealtime();

        new Thread(() -> {
            BigInteger result = calculateFibonacci(n);
            String preview = formatResult(result);

            // Đo duration ngay trên Main Thread tương tự onPostExecute của AsyncTask
            mainHandler.post(() -> {
                if (binding == null) return;
                long duration = SystemClock.elapsedRealtime() - startTime;
                displayResult("1. Raw Thread", duration, preview);
                setLoadingState(false);
            });
        }).start();
    }

    // 2. Chạy với AsyncTask
    private void runWithAsyncTask(int n) {
        long startTime = SystemClock.elapsedRealtime();

        new AsyncTask<Integer, Void, String>() {
            @Override
            protected String doInBackground(Integer... params) {
                BigInteger result = calculateFibonacci(params[0]);
                return formatResult(result);
            }

            @Override
            protected void onPostExecute(String preview) {
                if (binding == null) return;
                long duration = SystemClock.elapsedRealtime() - startTime;
                displayResult("2. AsyncTask", duration, preview);
                setLoadingState(false);
            }
        }.execute(n);
    }

    // Tính Fibonacci vòng lặp đơn giản
    private BigInteger calculateFibonacci(int n) {
        if (n <= 0) return BigInteger.ZERO;
        if (n == 1) return BigInteger.ONE;

        BigInteger a = BigInteger.ZERO;
        BigInteger b = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            BigInteger c = a.add(b);
            a = b;
            b = c;
        }
        return b;
    }

    private String formatResult(BigInteger result) {
        String str = result.toString();
        if (str.length() > 30) {
            return str.substring(0, 25) + "... (" + str.length() + " chữ số)";
        }
        return str;
    }

    private void displayResult(String engineName, long duration, String result) {
        binding.tvEngineUsed.setText("Cơ chế: " + engineName);
        binding.tvExecutionTime.setText("Thời gian chạy: " + duration + " ms");
        binding.tvResultValue.setText("Kết quả: " + result);
    }

    private void setLoadingState(boolean isLoading) {
        binding.pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRunThread.setEnabled(!isLoading);
        binding.btnRunAsync.setEnabled(!isLoading);
        binding.edtInputN.setEnabled(!isLoading);
    }

    @Override
    public void onDestroyView() {
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }
}