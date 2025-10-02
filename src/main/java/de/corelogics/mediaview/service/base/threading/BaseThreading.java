/*
 * MIT License
 *
 * Copyright (c) 2020-2025 Mediatheken DLNA Bridge Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.corelogics.mediaview.service.base.threading;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor(access = AccessLevel.MODULE)
public class BaseThreading {
    private final AtomicInteger scheduledWorkerNumber = new AtomicInteger();
    private final ThreadFactory virtualThreadFactory;
    private final ScheduledExecutorService scheduledExecutor;

    @Getter
    private final ExecutorService webIoExecutor;

    @Getter
    private final ExecutorService upnpIoExecutor;

    public BaseThreading() {
        this(
            Thread.ofVirtual().factory(),
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "scheduler-main")),
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("webio-", 0L).factory()),
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("upnp-", 0L).factory())
        );
    }

    public void schedulePeriodic(Runnable runnable, Duration initialDelay, Duration period) {
        scheduledExecutor.scheduleAtFixedRate(
            () -> startScheduledTask(runnable),
            initialDelay.toSeconds(),
            period.toSeconds(),
            TimeUnit.SECONDS);
    }

    public void schedule(Runnable runnable, Duration delay) {
        scheduledExecutor.schedule(
            () -> startScheduledTask(runnable),
            delay.toSeconds(),
            TimeUnit.SECONDS);
    }

    private void startScheduledTask(Runnable runnable) {
        val thread = virtualThreadFactory.newThread(runnable);
        thread.setName("sched-" + scheduledWorkerNumber.getAndIncrement());
        thread.setUncaughtExceptionHandler(this::exectionInScheduledTask);
        thread.start();
    }

    private void exectionInScheduledTask(Thread thread, Throwable throwable) {

    }
}
