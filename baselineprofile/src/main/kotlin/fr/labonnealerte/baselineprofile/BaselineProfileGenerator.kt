package fr.labonnealerte.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Parcours critique : demarrage a froid, accueil passe, defilement du kiosque.
 * Lancer : gradlew :baselineprofile:connectedFossBenchmarkReleaseAndroidTest
 * (ou la tache generateBaselineProfile), emulateur API 33+ connecte.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun demarrageEtKiosque() = rule.collect(packageName = "fr.labonnealerte.app") {
        pressHome()
        startActivityAndWait()
        device.findObject(By.text("Passer"))?.click()
        device.wait(Until.hasObject(By.text("Kiosque")), ATTENTE_MS)
        val liste = device.findObject(By.scrollable(true))
        if (liste != null) {
            repeat(DEFILEMENTS) { liste.fling(Direction.DOWN) }
            liste.fling(Direction.UP)
        }
        device.waitForIdle()
    }

    private companion object {
        const val ATTENTE_MS = 10_000L
        const val DEFILEMENTS = 3
    }
}
