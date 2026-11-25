package com.federicopeyrani.trainapis.shared.infrastructure.cache

import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CacheConfig {

    companion object {

        private const val CACHE = "viaggiatreno"
    }

    @Bean
    fun cacheManager(): CacheManager = ConcurrentMapCacheManager(CACHE)

    @Bean
    fun cache(factory: Cache.Factory): Cache = factory.create(CACHE)
}
