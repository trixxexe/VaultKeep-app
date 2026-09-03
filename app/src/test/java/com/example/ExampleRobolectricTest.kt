package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.PasswordConfig
import com.example.crypto.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VaultKeep", appName)
  }

  @Test
  fun `password generator produces expected length`() {
    val config = PasswordConfig(length = 20)
    val password = PasswordGenerator.generate(config)
    assertEquals(20, password.length)
  }
}

