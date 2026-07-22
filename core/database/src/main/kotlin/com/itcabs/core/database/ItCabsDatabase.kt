package com.itcabs.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [LegEntity::class, UserEntity::class], version = 1, exportSchema = false)
abstract class ItCabsDatabase : RoomDatabase() {
    abstract fun legDao(): LegDao
    abstract fun userDao(): UserDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ItCabsDatabase =
        Room.databaseBuilder(context, ItCabsDatabase::class.java, "itcabs.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun legDao(db: ItCabsDatabase): LegDao = db.legDao()

    @Provides
    fun userDao(db: ItCabsDatabase): UserDao = db.userDao()
}
