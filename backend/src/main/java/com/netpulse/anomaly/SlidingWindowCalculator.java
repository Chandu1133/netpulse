package com.netpulse.anomaly;

import com.netpulse.config.NetPulseProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SlidingWindowCalculator {

    private static final double MIN_STD_DEV_FLOOR_MS = 5.0;
    private static final double RELATIVE_STD_DEV_FLOOR_RATIO = 0.05;

    private final NetPulseProperties properties;
    private final Map<Long, Deque<Double>> windows = new ConcurrentHashMap<>();

    public SlidingWindowCalculator(NetPulseProperties properties) {
        this.properties = properties;
    }

    public boolean hasWindow(Long targetId) {
        return windows.containsKey(targetId);
    }

    public void seedWindow(Long targetId, Collection<Double> chronologicalHealthySamples) {
        Deque<Double> deque = windows.computeIfAbsent(targetId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.clear();
            int maxSize = Math.max(1, properties.getAnomaly().getWindowSize());
            for (Double sample : chronologicalHealthySamples) {
                if (sample != null && sample >= 0.0) {
                    if (deque.size() >= maxSize) {
                        deque.removeFirst();
                    }
                    deque.addLast(sample);
                }
            }
        }
    }

    public RollingWindowStats evaluate(Long targetId, Double currentLatencyMs) {
        Deque<Double> deque = windows.get(targetId);
        if (deque == null) {
            return RollingWindowStats.empty();
        }

        double[] snapshot;
        synchronized (deque) {
            if (deque.isEmpty()) {
                return RollingWindowStats.empty();
            }
            snapshot = new double[deque.size()];
            int idx = 0;
            for (Double val : deque) {
                snapshot[idx++] = val;
            }
        }

        return computeStats(snapshot, currentLatencyMs);
    }

    public void recordHealthySample(Long targetId, Double latencyMs) {
        if (latencyMs == null || latencyMs < 0.0) {
            return;
        }
        int maxSize = Math.max(1, properties.getAnomaly().getWindowSize());
        Deque<Double> deque = windows.computeIfAbsent(targetId, k -> new ArrayDeque<>());
        synchronized (deque) {
            if (deque.size() >= maxSize) {
                deque.removeFirst();
            }
            deque.addLast(latencyMs);
        }
    }

    public void clearWindow(Long targetId) {
        windows.remove(targetId);
    }

    public RollingWindowStats computeStats(double[] samples, Double currentLatencyMs) {
        if (samples == null || samples.length == 0) {
            return RollingWindowStats.empty();
        }

        int n = samples.length;
        double sum = 0.0;
        for (double s : samples) {
            sum += s;
        }
        double mean = sum / n;

        double varianceSum = 0.0;
        for (double s : samples) {
            double diff = s - mean;
            varianceSum += diff * diff;
        }
        double rawStdDev = Math.sqrt(varianceSum / n);
        double effectiveStdDev = Math.max(rawStdDev, Math.max(mean * RELATIVE_STD_DEV_FLOOR_RATIO, MIN_STD_DEV_FLOOR_MS));

        double warningZ = properties.getAnomaly().getWarningZThreshold();
        double criticalZ = properties.getAnomaly().getCriticalZThreshold();

        double warningThreshold = mean + (warningZ * effectiveStdDev);
        double criticalThreshold = mean + (criticalZ * effectiveStdDev);

        Double zScore = null;
        if (currentLatencyMs != null) {
            zScore = round2((currentLatencyMs - mean) / effectiveStdDev);
        }

        return new RollingWindowStats(
                n,
                round2(mean),
                round2(effectiveStdDev),
                round2(warningThreshold),
                round2(criticalThreshold),
                zScore
        );
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
