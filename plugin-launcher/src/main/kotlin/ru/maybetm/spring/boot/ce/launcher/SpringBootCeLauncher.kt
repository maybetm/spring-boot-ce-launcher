package ru.maybetm.spring.boot.ce.launcher

import com.intellij.execution.ShortenCommandLine
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getIcon
import org.jdom.Element
import ru.maybetm.spring.boot.ce.launcher.ConfigurationType.Icons.Companion.ACTION_ICON
import javax.swing.Icon

const val PLUGIN_NAME = "Spring Boot CE Launcher"

class ConfigurationType : ConfigurationType {

    interface Icons {
        companion object {
            val ACTION_ICON: Icon = getIcon("/META-INF/actionIcon.svg", Icons::class.java)
        }
    }

    override fun getId(): String = "SpringBootApplicationConfigurationType"
    override fun getDisplayName(): String = PLUGIN_NAME
    override fun getConfigurationTypeDescription(): String = "Spring Boot (handled by plugin)"
    override fun getIcon() = ACTION_ICON
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(ConfigurationFactory(this))
}

class ConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project) = RunConfiguration(project, this)
    override fun getId(): String = "Spring Boot"
}

class RunConfiguration(project: Project, factory: ConfigurationFactory) :
    ApplicationConfiguration(PLUGIN_NAME, project, factory) {

    override fun readExternal(element: Element) {
        super.readExternal(element)
        // fixme тут падает исключение, при открытии меню редактирования
        val mainClass = element.getChildren("option")
            .map { it.attributes }
            .filter { it.isNotEmpty() }
            .first { attributes -> attributes.filter { it.name.equals("name") }
                    .any { it.value.equals("SPRING_BOOT_MAIN_CLASS") }
            }
            .first { it.name.equals("value") }
            .value

        this.options.mainClassName = mainClass
        this.mainClassName = mainClass
        // fixme будто бы это должно быть указано в родительском конфиге и для спрингбута
        this.options.shortenClasspath = ShortenCommandLine.ARGS_FILE
    }
}