package ru.quipy.payments.logic

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.util.concurrent.TimeUnit

class Semaphore(private var permissions: Int) {
    private val lock = ReentrantLock()
    private val semaphoreLock = lock.newCondition()

    @Throws(InterruptedException::class)
    fun acquire() {
        lock.withLock {
            while (permissions < 1) {
                semaphoreLock.await()
            }
            permissions--
        }
    }

    fun tryAcquire(): Boolean {
        lock.withLock {
            if (permissions > 0) {
                permissions--
                return true
            }
            return false
        }
    }

    @Throws(InterruptedException::class)
    fun tryAcquire(timeout: Long, unit: TimeUnit): Boolean {
        var tillTimeout = unit.toNanos(timeout)
        lock.withLock {
            while (permissions < 1) {
                if (tillTimeout < 1) {
                    return false
                }
                tillTimeout = semaphoreLock.awaitNanos(tillTimeout)
            }
            permissions--
            return true
        }
    }

    fun release() {
        lock.withLock {
            permissions++
            semaphoreLock.signal()
        }
    }
}