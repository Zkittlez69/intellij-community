// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.SettingsController
import com.intellij.serviceContainer.ComponentManagerImpl
import org.jdom.Element
import java.io.InputStream
import java.io.Writer
import java.nio.file.Path

const val PROJECT_DEFAULT_FILE_NAME = "project.default.xml"
const val PROJECT_DEFAULT_FILE_SPEC = "$APP_CONFIG/${PROJECT_DEFAULT_FILE_NAME}"

internal class DefaultProjectStoreImpl(override val project: Project) : ComponentStoreWithExtraComponents() {
  // see note about default state in project store
  private val compoundStreamProvider = CompoundStreamProvider()

  override val loadPolicy: StateLoadPolicy
    get() = if (ApplicationManager.getApplication().isUnitTestMode) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD

  private val storage by lazy {
    val file = ApplicationManager.getApplication().stateStore.storageManager.expandMacro(PROJECT_DEFAULT_FILE_SPEC)
    DefaultProjectStorage(
      file = file,
      fileSpec = PROJECT_DEFAULT_FILE_SPEC,
      pathMacroManager = PathMacroManager.getInstance(project),
      streamProvider = compoundStreamProvider,
    )
  }

  override val serviceContainer: ComponentManagerImpl
    get() = project as ComponentManagerImpl

  override val storageManager: StateStorageManager = object : StateStorageManager {
    override val componentManager: ComponentManager?
      get() = null

    override fun addStreamProvider(provider: StreamProvider, first: Boolean) {
      compoundStreamProvider.addStreamProvider(provider, first)
    }

    override fun removeStreamProvider(aClass: Class<out StreamProvider>) {
      compoundStreamProvider.removeStreamProvider(aClass)
    }

    override fun getStateStorage(storageSpec: Storage): DefaultProjectStorage = storage

    override fun expandMacro(collapsedPath: String) = throw UnsupportedOperationException()

    override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): DefaultProjectStorage = storage
  }

  override fun isUseLoadedStateAsExisting(storage: StateStorage) = false

  // don't want to optimize and use already loaded data - it will add unnecessary complexity and implementation-lock
  // (currently we store loaded archived state in memory, but later implementation can be changed)
  fun getStateCopy(): Element? = storage.loadLocalData()

  override fun getPathMacroManagerForDefaults(): PathMacroManager = PathMacroManager.getInstance(project)

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>,
                                   stateSpec: State,
                                   operation: StateStorageOperation): List<FileStorageAnnotation> {
    return listOf(PROJECT_FILE_STORAGE_ANNOTATION)
  }

  override fun setPath(path: Path) {}

  override fun toString(): String = "default project"

  private class DefaultProjectStorage(
    file: Path,
    fileSpec: String,
    pathMacroManager: PathMacroManager,
    streamProvider: StreamProvider,
  ) : FileBasedStorage(
    file = file,
    fileSpec = fileSpec,
    rootElementName = "defaultProject",
    pathMacroManager = pathMacroManager.createTrackingSubstitutor(),
    roamingType = RoamingType.DISABLED,
    provider = streamProvider,
  ) {
    override val controller: SettingsController?
      get() = null

    public override fun loadLocalData(): Element? {
      return postProcessLoadedData { super.loadLocalData() }
    }

    override fun loadFromStreamProvider(stream: InputStream): Element? {
      return postProcessLoadedData { super.loadFromStreamProvider(stream) }
    }

    private fun postProcessLoadedData(elementProvider: () -> Element?): Element? {
      try {
        return elementProvider()?.getChild("component")?.getChild("defaultProject")
      }
      catch (_: NullPointerException) {
        LOG.warn("Cannot read default project")
        return null
      }
    }

    override fun createSaveSession(states: StateMap): FileSaveSessionProducer {
      return object : FileSaveSessionProducer(storageData = states, storage = this) {
        override fun saveLocally(dataWriter: DataWriter?) {
          super.saveLocally(
            dataWriter = if (dataWriter == null) null
            else object : StringDataWriter() {
              override fun hasData(filter: DataWriterFilter): Boolean = dataWriter.hasData(filter)

              override fun writeTo(writer: Writer, lineSeparator: String, filter: DataWriterFilter?) {
                val lineSeparatorWithIndent = "${lineSeparator}    "
                writer.append("<application>").append(lineSeparator)
                writer.append("""  <component name="ProjectManager">""")
                writer.append(lineSeparatorWithIndent)
                (dataWriter as StringDataWriter).writeTo(writer, lineSeparatorWithIndent, filter)
                writer.append(lineSeparator)
                writer.append("  </component>").append(lineSeparator)
                writer.append("</application>")
              }
            })
        }
      }
    }
  }
}
