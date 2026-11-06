package fr.labonnealerte.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migrations Room, a partir des schemas exportes (core/database/schemas, ajoutes aux assets de test).
 * Helper a base de SQLiteDriver : le helper SupportSQLite compare les chemins avec « / » et echoue
 * sous Windows (« This driver is configured to open a database named ... »).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbFile: File = ctx.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = LbaDatabase::class
    )

    private fun insertV1(conn: SQLiteConnection, dedupKey: String, serverId: String) {
        conn.execSQL(
            "INSERT INTO notifications (dedupKey, serverEventId, sourceId, sourceName, kind, emoji, title, text, url, " +
                "receivedAtMillis, read, channel) VALUES ('$dedupKey', $serverId, 'vigilance-meteo', 'Vigilance', 'activated', NULL, " +
                "'Titre', 'Texte', NULL, 1000, 0, 'urgences')"
        )
    }

    @Test
    fun `migration 1 vers 2 ajoute contentKey et son index sans perdre les donnees`() {
        helper.createDatabase(1).use { insertV1(it, "ev:42", "42") }
        val conn = helper.runMigrationsAndValidate(2, listOf(LbaDatabase.MIGRATION_1_2))
        conn.prepare("SELECT dedupKey, serverEventId, title, contentKey FROM notifications").use { st ->
            assertTrue(st.step())
            assertEquals("ev:42", st.getText(0))
            assertEquals(42L, st.getLong(1))
            assertEquals("Titre", st.getText(2))
            assertTrue(st.isNull(3))
            assertTrue(!st.step())
        }
        val indexes = mutableListOf<String>()
        conn.prepare("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'notifications'").use { st ->
            while (st.step()) indexes += st.getText(0)
        }
        assertTrue(indexes.toString(), "index_notifications_contentKey" in indexes)
        conn.close()
    }

    @Test
    fun `base v1 migree s ouvre via Room avec MIGRATIONS`() = runTest {
        helper.createDatabase(1).use { insertV1(it, "c:abc", "NULL") }
        val room = Room.databaseBuilder(ctx, LbaDatabase::class.java, dbFile.absolutePath)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*LbaDatabase.MIGRATIONS)
            .build()
        try {
            val dao = room.notificationDao()
            val latest = dao.latest()!!
            assertEquals("c:abc", latest.dedupKey)
            assertNull(latest.contentKey)
            assertEquals(0, dao.countByKey("c:abc", 0))
            dao.attachServerId("k", 1) // requete v2 executable sur la base migree
        } finally {
            room.close()
        }
    }
}
