package com.klcn.mobilecaptioning

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the same Central API route used by login and registration. */
@RunWith(AndroidJUnit4::class)
class CentralApiConnectivityTest {
    @Test
    fun centralApiHealthIsReachableFromAndroid(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(CentralApiClient(context).health())
    }
}
