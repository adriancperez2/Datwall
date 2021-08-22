package com.smartsolutions.paquetes.managers

import com.smartsolutions.paquetes.managers.contracts.ISimManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SimManagerTest {

    @get:Rule
    val rule = HiltAndroidRule(this)

    @Inject
    lateinit var simManager: ISimManager

    @Before
    fun setUp() {
        rule.inject()
    }

    @Test
    fun flowInstalledSims() {
        runBlocking {
            val sims = simManager.flowInstalledSims(false)
                .first()

            assertNotNull(sims)
            assertFalse(sims.isEmpty())
        }
    }
}