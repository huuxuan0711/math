package com.danghung.imageflow.engine;

import com.danghung.imageflow.util.CpuUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MathEngines: Chứa các thuật toán toán học CPU-bound kinh điển từ báo cáo chuyên đề.
 */
public class MathEngines {

    // 1. Kiểm tra số nguyên tố
    public static boolean isPrime(long n) {
        if (n < 2) return false;
        if (n == 2 || n == 3) return true;
        if (n % 2 == 0 || n % 3 == 0) return false;
        long sqrt = (long) Math.sqrt(n);
        for (long i = 5; i <= sqrt; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) return false;
        }
        return true;
    }

    // Đếm số nguyên tố trong dải [from, to]
    public static int countPrimes(long from, long to) {
        int count = 0;
        for (long n = Math.max(2, from); n <= to; n++) {
            if (isPrime(n)) count++;
        }
        return count;
    }


    // 2. Tính số Fibonacci lớn bằng thuật toán Divide-and-Conquer Fast Doubling
    // F(2k) = F(k) * [2*F(k+1) - F(k)]
    // F(2k+1) = F(k+1)^2 + F(k)^2
    public static BigInteger fibonacciFastDoubling(int n) {
        return fibFast(n)[0];
    }

    private static BigInteger[] fibFast(int n) {
        if (n == 0) return new BigInteger[]{BigInteger.ZERO, BigInteger.ONE};
        BigInteger[] p = fibFast(n / 2);
        BigInteger c = p[0].multiply(p[1].shiftLeft(1).subtract(p[0]));
        BigInteger d = p[0].multiply(p[0]).add(p[1].multiply(p[1]));
        if (n % 2 == 0) {
            return new BigInteger[]{c, d};
        } else {
            return new BigInteger[]{d, c.add(d)};
        }
    }

}

