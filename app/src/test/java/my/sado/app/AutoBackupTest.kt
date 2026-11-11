package my.sado.app

import my.sado.app.data.AutoBackup
import my.sado.app.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    @Test fun keepsNewestFour() {
        val names = listOf("sado-auto-2026-10-01.sado", "sado-auto-2026-10-08.sado", "notes.txt", "sado-auto-2026-10-15.sado",
            "sado-auto-2026-10-22.sado", "sado-auto-2026-10-29.sado", "sado-backup-2026-10-02.sado")
        assertEquals(listOf("sado-auto-2026-10-01.sado"), AutoBackup.toPrune(names))
    }

    @Test fun dueAfterTheGap() {
        val on = Settings(autoBackup = true, backupFolder = "content://x", backupEveryDays = 7, lastAutoBackup = 100)
        assertFalse(AutoBackup.due(on, 106))
        assertTrue(AutoBackup.due(on, 107))
        assertTrue(AutoBackup.due(on.copy(lastAutoBackup = null), 1))
        assertFalse(AutoBackup.due(on.copy(autoBackup = false), 200))
        assertFalse(AutoBackup.due(on.copy(backupFolder = null), 200))
    }
}
