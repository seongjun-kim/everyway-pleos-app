package app.carwell.drive

import android.app.Application
import app.carwell.drive.data.*
import app.carwell.drive.domain.Clock
import app.carwell.drive.domain.LocalStore
import java.io.File

class DriveApp : Application() {
    lateinit var controller: AppController
        private set

    override fun onCreate() {
        super.onCreate()
        val pleos = PleosVehicleSource.isAvailable(this)
        val simClock = SimClock()
        controller = if (pleos) {
            AppController(this, PleosVehicleSource(this), sim = null, clock = object : Clock {
                override fun nowMs() = System.currentTimeMillis()
            })
        } else {
            val lastOdo = LocalStore(File(filesDir, AppController.DATA_FILE)).load().lastOdoKm
            val sim = SimulatedVehicleSource(lastOdo ?: 12_000.0)
            AppController(this, sim, sim = sim, clock = simClock)
        }
        simClockRef = if (pleos) null else simClock
    }

    companion object {
        /** 시뮬레이터 패널이 쓰는 시계 (Pleos 소스일 때는 null) */
        var simClockRef: SimClock? = null
    }
}
