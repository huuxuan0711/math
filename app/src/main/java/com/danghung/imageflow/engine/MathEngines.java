package com.danghung.imageflow.engine;


import java.math.BigInteger;
public class MathEngines {

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
    public static int countPrimes(long from, long to) {
        int count = 0;
        for (long n = Math.max(2, from); n <= to; n++) {
            if (isPrime(n)) count++;
        }
        return count;
    }

}

