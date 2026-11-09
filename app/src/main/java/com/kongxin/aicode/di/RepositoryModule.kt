package com.kongxin.aicode.di

import com.kongxin.aicode.feature.settings.domain.repository.AIProviderRepository
import com.kongxin.aicode.feature.settings.data.repository.AIProviderRepositoryImpl
import com.kongxin.aicode.feature.credentials.domain.repository.CredentialRepository
import com.kongxin.aicode.feature.credentials.data.repository.FileCredentialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAIProviderRepository(
        aiProviderRepositoryImpl: AIProviderRepositoryImpl
    ): AIProviderRepository

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(
        fileCredentialRepository: FileCredentialRepository
    ): CredentialRepository
}
