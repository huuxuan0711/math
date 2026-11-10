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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class SyncLabFragment extends Fragment {

    private static final int THREADS = 4;
    private static final int INCREMENTS = 100_000;
    private static final int EXPECTED = THREADS * INCREMENTS;

    private static final int INITIAL_BALANCE = 100;
    private static final int WITHDRAW_AMOUNT = 100;

    private static final int TRANSFER_A_TO_B = 50;
    private static final int TRANSFER_B_TO_A = 30;
    private static final long DEADLOCK_TIMEOUT_MS = 450L;

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

    private interface Account {
        boolean withdraw(int amount);
        int getBalance();
    }

    private static class UnsafeAccount implements Account {
        protected int balance = INITIAL_BALANCE;

        @Override
        public boolean withdraw(int amount) {
            if (balance >= amount) {
                Thread.yield();
                SystemClock.sleep(15);
                balance -= amount;
                return true;
            }
            return false;
        }

        @Override
        public int getBalance() {
            return balance;
        }
    }

    private static class SafeAccount extends UnsafeAccount {
        @Override
        public synchronized boolean withdraw(int amount) {
            return super.withdraw(amount);
        }

        @Override
        public synchronized int getBalance() {
            return super.getBalance();
        }
    }

    private static class TransferAccount {
        final int id;
        int balance;
        final ReentrantLock lock = new ReentrantLock();

        TransferAccount(int id, int balance) {
            this.id = id;
            this.balance = balance;
        }
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
        binding.btnRunWithdrawDemo.setOnClickListener(v -> runWithdrawTests());
        binding.btnRunDeadlockDemo.setOnClickListener(v -> runDeadlockTests());
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

    private void runWithdrawTests() {
        binding.btnRunWithdrawDemo.setEnabled(false);
        binding.pbWithdrawDemo.setVisibility(View.VISIBLE);
        resetWithdrawUI();

        executor.execute(() -> {
            try {
                WithdrawResult unsafe = runWithdrawTest(new UnsafeAccount());
                WithdrawResult safe = runWithdrawTest(new SafeAccount());

                mainHandler.post(() -> showWithdrawResults(unsafe, safe));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private WithdrawResult runWithdrawTest(Account account) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        boolean[] results = new boolean[2];

        try {
            for (int i = 0; i < 2; i++) {
                final int index = i;
                pool.execute(() -> {
                    try {
                        readyGate.countDown();
                        startGate.await();
                        results[index] = account.withdraw(WITHDRAW_AMOUNT);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            readyGate.await();
            long start = SystemClock.elapsedRealtime();
            startGate.countDown();
            doneGate.await();
            return new WithdrawResult(
                    results[0],
                    results[1],
                    account.getBalance(),
                    SystemClock.elapsedRealtime() - start
            );
        } finally {
            pool.shutdownNow();
        }
    }

    private void resetWithdrawUI() {
        binding.tvUnsafeWithdrawDetail.setText("Người A: Đang xử lý... | Người B: Đang xử lý...");
        binding.tvSafeWithdrawDetail.setText("Người A: Đang xử lý... | Người B: Đang xử lý...");
        binding.tvUnsafeWithdrawBalance.setText("Số dư cuối: ---");
        binding.tvSafeWithdrawBalance.setText("Số dư cuối: ---");
        binding.tvUnsafeWithdrawTime.setText("-- ms");
        binding.tvSafeWithdrawTime.setText("-- ms");
    }

    private void showWithdrawResults(WithdrawResult unsafe, WithdrawResult safe) {
        if (binding == null) return;

        bindWithdrawCard(
                binding.tvUnsafeWithdrawDetail,
                binding.tvUnsafeWithdrawBalance,
                binding.tvUnsafeWithdrawTime,
                unsafe
        );
        bindWithdrawCard(
                binding.tvSafeWithdrawDetail,
                binding.tvSafeWithdrawBalance,
                binding.tvSafeWithdrawTime,
                safe
        );

        binding.pbWithdrawDemo.setVisibility(View.GONE);
        binding.btnRunWithdrawDemo.setEnabled(true);
    }

    private void bindWithdrawCard(TextView detailView, TextView balanceView, TextView timeView, WithdrawResult result) {
        String statusA = result.personASuccess ? "Rút 100k (Thành công)" : "Từ chối (Không đủ tiền)";
        String statusB = result.personBSuccess ? "Rút 100k (Thành công)" : "Từ chối (Không đủ tiền)";
        detailView.setText(String.format(Locale.US, "Người A: %s\nNgười B: %s", statusA, statusB));

        boolean hasRaceCondition = (result.personASuccess && result.personBSuccess) || result.finalBalance < 0;
        String statusTag = hasRaceCondition ? "❌ Lỗi Race Condition!" : "✔ An toàn (Chỉ 1 người rút được)";
        balanceView.setText(String.format(Locale.US, "Số dư cuối: %dk — %s", result.finalBalance, statusTag));
        balanceView.setTextColor(requireContext().getColor(
                hasRaceCondition ? R.color.status_failed : R.color.status_completed
        ));
        timeView.setText(result.elapsed + " ms");
    }

    private void runDeadlockTests() {
        binding.btnRunDeadlockDemo.setEnabled(false);
        binding.pbDeadlockDemo.setVisibility(View.VISIBLE);
        resetDeadlockUI();

        executor.execute(() -> {
            try {
                TransferResult deadlockResult = runTransferTest(false);
                TransferResult safeOrderResult = runTransferTest(true);

                mainHandler.post(() -> showDeadlockResults(deadlockResult, safeOrderResult));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private TransferResult runTransferTest(boolean useLockOrdering) throws InterruptedException {
        TransferAccount accA = new TransferAccount(1, INITIAL_BALANCE);
        TransferAccount accB = new TransferAccount(2, INITIAL_BALANCE);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);

        try {
            pool.execute(() -> runTransferWorker(readyGate, startGate, doneGate, accA, accB, TRANSFER_A_TO_B, useLockOrdering));
            pool.execute(() -> runTransferWorker(readyGate, startGate, doneGate, accB, accA, TRANSFER_B_TO_A, useLockOrdering));

            readyGate.await();
            long start = SystemClock.elapsedRealtime();
            startGate.countDown();
            boolean completed = doneGate.await(DEADLOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long elapsed = SystemClock.elapsedRealtime() - start;

            // Dừng an toàn 2 luồng bị tắc nếu deadlock trước khi đọc lại số dư
            pool.shutdownNow();
            pool.awaitTermination(200, TimeUnit.MILLISECONDS);

            return new TransferResult(accA.balance, accB.balance, !completed, elapsed);
        } finally {
            pool.shutdownNow();
        }
    }

    private void runTransferWorker(
            CountDownLatch readyGate,
            CountDownLatch startGate,
            CountDownLatch doneGate,
            TransferAccount from,
            TransferAccount to,
            int amount,
            boolean useLockOrdering
    ) {
        try {
            readyGate.countDown();
            startGate.await();
            transferMoney(from, to, amount, useLockOrdering);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            doneGate.countDown();
        }
    }

    private void transferMoney(TransferAccount from, TransferAccount to, int amount, boolean useLockOrdering) throws InterruptedException {
        TransferAccount first = (useLockOrdering && from.id > to.id) ? to : from;
        TransferAccount second = (first == from) ? to : from;

        boolean firstLocked = false;
        boolean secondLocked = false;
        try {
            first.lock.lockInterruptibly();
            firstLocked = true;

            // Tạo độ trễ nhỏ giữa 2 lần khóa để 2 luồng giữ khóa chéo nhau nếu không sắp thứ tự
            Thread.sleep(30);

            second.lock.lockInterruptibly();
            secondLocked = true;

            if (from.balance >= amount) {
                from.balance -= amount;
                to.balance += amount;
            }
        } finally {
            if (secondLocked) second.lock.unlock();
            if (firstLocked) first.lock.unlock();
        }
    }

    private void resetDeadlockUI() {
        binding.tvDeadlockDetail.setText("Đang thực thi 2 luồng chuyển tiền song song...");
        binding.tvLockOrderDetail.setText("Đang thực thi 2 luồng chuyển tiền song song...");
        binding.tvDeadlockResult.setText("Trạng thái: ---");
        binding.tvLockOrderResult.setText("Trạng thái: ---");
        binding.tvDeadlockTime.setText("-- ms");
        binding.tvLockOrderTime.setText("-- ms");
    }

    private void showDeadlockResults(TransferResult deadlockResult, TransferResult safeOrderResult) {
        if (binding == null) return;

        bindDeadlockCard(
                binding.tvDeadlockDetail,
                binding.tvDeadlockResult,
                binding.tvDeadlockTime,
                deadlockResult,
                "Luồng 1 giữ A chờ B | Luồng 2 giữ B chờ A"
        );
        bindDeadlockCard(
                binding.tvLockOrderDetail,
                binding.tvLockOrderResult,
                binding.tvLockOrderTime,
                safeOrderResult,
                "Cả 2 luồng đều khóa A (ID=1) trước → B (ID=2)"
        );

        binding.pbDeadlockDemo.setVisibility(View.GONE);
        binding.btnRunDeadlockDemo.setEnabled(true);
    }

    private void bindDeadlockCard(TextView detailView, TextView resultView, TextView timeView, TransferResult result, String detailText) {
        detailView.setText(detailText);
        int total = result.balanceA + result.balanceB;
        if (result.deadlocked) {
            resultView.setText(String.format(
                    Locale.US,
                    "A: %dk | B: %dk — ❌ Deadlock (Treo quá %d ms)",
                    result.balanceA, result.balanceB, DEADLOCK_TIMEOUT_MS
            ));
            resultView.setTextColor(requireContext().getColor(R.color.status_failed));
        } else {
            resultView.setText(String.format(
                    Locale.US,
                    "A: %dk | B: %dk (Tổng: %dk) — ✔ Hoàn tất an toàn",
                    result.balanceA, result.balanceB, total
            ));
            resultView.setTextColor(requireContext().getColor(R.color.status_completed));
        }
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

    private static class WithdrawResult {
        final boolean personASuccess;
        final boolean personBSuccess;
        final int finalBalance;
        final long elapsed;

        WithdrawResult(boolean personASuccess, boolean personBSuccess, int finalBalance, long elapsed) {
            this.personASuccess = personASuccess;
            this.personBSuccess = personBSuccess;
            this.finalBalance = finalBalance;
            this.elapsed = elapsed;
        }
    }

    private static class TransferResult {
        final int balanceA;
        final int balanceB;
        final boolean deadlocked;
        final long elapsed;

        TransferResult(int balanceA, int balanceB, boolean deadlocked, long elapsed) {
            this.balanceA = balanceA;
            this.balanceB = balanceB;
            this.deadlocked = deadlocked;
            this.elapsed = elapsed;
        }
    }
}