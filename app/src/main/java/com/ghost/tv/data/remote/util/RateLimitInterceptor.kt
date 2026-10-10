package com.ghost.tv.data.remote.util

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RateLimitInterceptor(private val requestsPerSecond: Int) : Interceptor {
    private val intervalNanos = TimeUnit.SECONDS.toNanos(1) / requestsPerSecond
    private val lastRequestTime = AtomicLong(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val now = System.nanoTime()
        val last = lastRequestTime.get()
        val waitTime = intervalNanos - (now - last)
        
        if (waitTime > 0) {
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(waitTime))
        }
        
        lastRequestTime.set(System.nanoTime())
        return chain.proceed(chain.request())
    }
}
