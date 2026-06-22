package com.automatic.attendance.student.viewmodel

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.Before

class HeartbeatViewModelUnitTest {
    private val context = mockk<Context>(relaxed = true)
    private lateinit var vm: HeartbeatViewModel

    @Before
    fun setup() {
        vm = HeartbeatViewModel(context)
    }

    @Test
    fun `start heartbeats transitions to active`() {
        vm.startHeartbeats("session-1", "student-1")
        verify { context.startForegroundService(any()) }
    }

    @Test
    fun `stop heartbeats stops service`() {
        vm.startHeartbeats("session-1", "student-1")
        vm.stopHeartbeats()
        verify { context.stopService(any()) }
    }

    @Test
    fun `idle state on init`() {
        assert(vm.getState() is HeartbeatState.Idle)
    }

    @Test
    fun `error on null context startForeground`() {
        val contextNull = mockk<Context> {
            every { startForegroundService(any()) } throws Exception("No permission")
        }
        val vm2 = HeartbeatViewModel(contextNull)
        vm2.startHeartbeats("s1", "st1")
        assert(vm2.getState() is HeartbeatState.Error)
    }
}
