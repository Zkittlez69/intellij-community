// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.libraries.UnknownLibraryKind
import com.intellij.openapi.roots.libraries.LibraryKindRegistry
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.libraryProperties
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.ArrayUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl.Companion.toLibraryRootType
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.FileContainerDescription
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.JarDirectoryDescription
import com.intellij.workspaceModel.ide.toExternalSource
import java.io.StringReader

class LibraryStateSnapshot(
  val libraryEntity: LibraryEntity,
  val storage: EntityStorage,
  val libraryTable: LibraryTable,
) {
  private val roots = collectFiles(libraryEntity)
  private val excludedRootsContainer = if (libraryEntity.excludedRoots.isNotEmpty()) {
    FileContainerDescription(libraryEntity.excludedRoots.map { it.url }, emptyList())
  }
  else null

  private val kindProperties by lazy {
    val customProperties = libraryEntity.libraryProperties
    val k = customProperties?.libraryType?.let {
      LibraryKindRegistry.getInstance().findKindById(it) ?: UnknownLibraryKind.getOrCreate(it)
    } as? PersistentLibraryKind<*>
    val p = loadProperties(k, customProperties)
    k to p
  }

  val kind: PersistentLibraryKind<*>?
    get() = kindProperties.first

  val properties: LibraryProperties<*>?
    get() = kindProperties.second

  private fun loadProperties(kind: PersistentLibraryKind<*>?, customProperties: LibraryPropertiesEntity?): LibraryProperties<*>? {
    if (kind == null) return null
    val properties = kind.createDefaultProperties()
    val propertiesElement = customProperties?.propertiesXmlTag
    if (propertiesElement == null) return properties
    ComponentSerializationUtil.loadComponentState(properties, JDOMUtil.load(StringReader(propertiesElement)))
    return properties
  }

  val name: String?
    get() = LibraryNameGenerator.getLegacyLibraryName(libraryEntity.symbolicId)

  val module: Module?
    get() = (libraryTable as? ModuleLibraryTableBridge)?.module

  fun getFiles(rootType: OrderRootType): Array<VirtualFile> {
    return roots[rootType.toLibraryRootType()]?.getFiles() ?: VirtualFile.EMPTY_ARRAY
  }

  fun getUrls(rootType: OrderRootType): Array<String> = roots[rootType.toLibraryRootType()]?.getUrls() ?: ArrayUtil.EMPTY_STRING_ARRAY

  val excludedRootUrls: Array<String>
    get() = excludedRootsContainer?.getUrls() ?: ArrayUtil.EMPTY_STRING_ARRAY

  val excludedRoots: Array<VirtualFile>
    get() = excludedRootsContainer?.getFiles() ?: VirtualFile.EMPTY_ARRAY

  fun isValid(url: String, rootType: OrderRootType): Boolean {
    return roots[rootType.toLibraryRootType()]
             ?.findByUrl(url)?.isValid ?: false
  }

  fun getInvalidRootUrls(type: OrderRootType): List<String> {
    return roots[type.toLibraryRootType()]?.getList()?.filterNot { it.isValid }?.map { it.url } ?: emptyList()
  }

  fun isJarDirectory(url: String) = isJarDirectory(url, OrderRootType.CLASSES)

  fun isJarDirectory(url: String, rootType: OrderRootType): Boolean {
    return roots[rootType.toLibraryRootType()]?.isJarDirectory(url) ?: false
  }

  val externalSource: ProjectModelExternalSource?
    get() = (libraryEntity.entitySource as? JpsImportedEntitySource)?.toExternalSource()

  companion object {
    private fun collectFiles(libraryEntity: LibraryEntity): Map<Any, FileContainerDescription> = libraryEntity.roots.groupBy { it.type }.mapValues { (_, roots) ->
      val urls = roots.filter { it.inclusionOptions == LibraryRoot.InclusionOptions.ROOT_ITSELF }.map { it.url }
      val jarDirs = roots
        .filter { it.inclusionOptions != LibraryRoot.InclusionOptions.ROOT_ITSELF }
        .map {
          JarDirectoryDescription(it.url, it.inclusionOptions == LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY)
        }
      FileContainerDescription(urls, jarDirs)
    }
  }
}
