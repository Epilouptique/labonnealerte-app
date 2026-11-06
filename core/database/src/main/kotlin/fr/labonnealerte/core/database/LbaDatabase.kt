package fr.labonnealerte.core.database

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

@Database(
    entities = [
        SourceEntity::class,
        CategoryEntity::class,
        CatalogMetaEntity::class,
        FavoriteEntity::class,
        NotificationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LbaDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val NAME = "labonnealerte.db"

        /** Migrations explicites uniquement (testees dans MigrationTest). */
        /** v1 → v2 : empreinte de contenu des notifications (deduplication push/releve). */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notifications ADD COLUMN contentKey TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_contentKey ON notifications (contentKey)")
            }
        }

        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(MIGRATION_1_2)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): LbaDatabase = Room.databaseBuilder(ctx, LbaDatabase::class.java, LbaDatabase.NAME)
        .addMigrations(*LbaDatabase.MIGRATIONS)
        .build()

    @Provides fun catalogDao(db: LbaDatabase) = db.catalogDao()

    @Provides fun favoriteDao(db: LbaDatabase) = db.favoriteDao()

    @Provides fun notificationDao(db: LbaDatabase) = db.notificationDao()
}
