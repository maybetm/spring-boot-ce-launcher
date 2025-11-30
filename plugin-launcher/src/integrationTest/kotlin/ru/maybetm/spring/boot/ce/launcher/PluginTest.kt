package ru.maybetm.spring.boot.ce.launcher

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.platform.commons.logging.Logger
import org.junit.platform.commons.logging.LoggerFactory
import java.io.File
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Remote("com.intellij.execution.RunManager")
interface RemoteRunManager {
    fun findConfigurationByName(name: String?): RemoteRunnerAndConfigurationSettings?
}

@Remote("com.intellij.execution.RunnerAndConfigurationSettings")
interface RemoteRunnerAndConfigurationSettings

@Remote("com.intellij.execution.ui.RunContentManager")
interface MyRunContentManager {
    fun getAllDescriptors(): List<MyRunContentDescriptorRef>
}

@Remote("com.intellij.execution.ui.RunContentDescriptor")
interface MyRunContentDescriptorRef {
    fun getProcessHandler(): MYProcessHandlerRef?
}

@Remote("com.intellij.execution.process.ProcessHandler")
interface MYProcessHandlerRef {
    fun destroyProcess()
}

@Remote("com.intellij.execution.ProgramRunnerUtil")
interface RemoteProgramRunnerUtil {
    fun executeConfiguration(
        settings: RemoteRunnerAndConfigurationSettings,
        executor: RemoteExecutor
    )
}

@Remote("com.intellij.execution.executors.DefaultRunExecutor")
interface RemoteDefaultRunExecutor {
    fun getRunExecutorInstance(): RemoteExecutor
}

@Remote("com.intellij.execution.Executor")
interface RemoteExecutor

class HealthChecker(
    private val httpClient: HttpClient = HttpClient(),
    private val logger: Logger = LoggerFactory.getLogger(HealthChecker::class.java)
) {

    suspend fun hasStarted(): Boolean {
        return runCatching {
            httpClient.get("http://localhost:8080/actuator/health")
                .body<String>()
                .equals("{\"status\":\"UP\"}", ignoreCase = true)
        }.getOrElse {
            logger.info { "unknown error, exception class: ${it.javaClass}" }
            return false
        }
    }
}

class PluginTest {

    val healthChecker = HealthChecker()

    @Test
    fun checkRunConfiguration() {
        Starter.newContext(
            testName = "CheckRunConfiguration",
            testCase = TestCase(
                IdeProductProvider.IC,
                LocalProjectInfo(Path("").toAbsolutePath().resolveSibling("spring-boot-demo"))
            ).withVersion("2025.2")
        ).apply {
            val pathToPlugin = System.getProperty("path.to.build.plugin")
            PluginConfigurator(this).installPluginFromFolder(
                File(pathToPlugin)
            )
        }.runIdeWithDriver().useDriverAndCloseIde {
            waitForIndicators(1.minutes)
            val project = singleProject()
            val runManager = service(RemoteRunManager::class, project)
            val configuration = "demo-configuration".let {
                runManager.findConfigurationByName(it)
                    ?: throw IllegalStateException("Run configuration '$it' not found in project.")
            }

            utility(RemoteDefaultRunExecutor::class).getRunExecutorInstance().let {
                utility(RemoteProgramRunnerUtil::class).executeConfiguration(configuration, it)
            }
            waitFor(
                "wait for start up",
                30.seconds,
                5.seconds,
                { "start failure" },
                { runBlocking { healthChecker.hasStarted() } }
            )

            service(MyRunContentManager::class, project)
                .getAllDescriptors().first().getProcessHandler()!!
                .destroyProcess()
            waitFor(
                "wait for shutdown",
                10.seconds,
                1.seconds,
                { "shutdown failure" },
                { runBlocking { !healthChecker.hasStarted() } }
            )
        }
    }

}
