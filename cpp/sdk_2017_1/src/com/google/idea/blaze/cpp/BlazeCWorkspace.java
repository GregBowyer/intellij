/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** Main entry point for C/CPP configuration data. */
public final class BlazeCWorkspace implements OCWorkspace, ProjectComponent {
  private final BlazeConfigurationResolver configurationResolver;
  private BlazeConfigurationResolverResult resolverResult;
  private final Project project;

  private BlazeCWorkspace(Project project) {
    this.configurationResolver = new BlazeConfigurationResolver(project);
    this.resolverResult = BlazeConfigurationResolverResult.empty(project);
    this.project = project;
  }

  public static BlazeCWorkspace getInstance(Project project) {
    return project.getComponent(BlazeCWorkspace.class);
  }

  @Override
  public void projectOpened() {
    CMakeWorkspaceOverride.undoCMakeModifications(project);
  }

  public void update(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      SyncMode syncMode) {
    BlazeConfigurationResolverResult oldResult = resolverResult;
    resolverResult =
        configurationResolver.update(
            context, workspaceRoot, projectViewSet, blazeProjectData, oldResult);
    incModificationTrackers();
  }

  private void incModificationTrackers() {
    TransactionGuard.submitTransaction(
        project,
        () -> {
          if (project.isDisposed()) {
            return;
          }
          OCWorkspaceModificationTrackers modTrackers =
              OCWorkspaceModificationTrackers.getInstance(project);
          modTrackers.getProjectFilesListTracker().incModificationCount();
          modTrackers.getSourceFilesListTracker().incModificationCount();
          modTrackers.getSelectedResolveConfigurationTracker().incModificationCount();
          modTrackers.getBuildSettingsChangesTracker().incModificationCount();
        });
  }

  @Override
  public Collection<VirtualFile> getLibraryFilesToBuildSymbols() {
    // This method should return all the header files themselves, not the head file directories.
    // (And not header files in the project; just the ones in the SDK and in any dependencies)
    return ImmutableList.of();
  }

  @Override
  public boolean areFromSameProject(@Nullable VirtualFile a, @Nullable VirtualFile b) {
    return false;
  }

  @Override
  public boolean areFromSamePackage(@Nullable VirtualFile a, @Nullable VirtualFile b) {
    return false;
  }

  @Override
  public boolean isInSDK(@Nullable VirtualFile file) {
    return false;
  }

  @Override
  public boolean isFromWrongSDK(OCSymbol symbol, @Nullable VirtualFile contextFile) {
    return false;
  }

  @Override
  public List<BlazeResolveConfiguration> getConfigurations() {
    return resolverResult.getAllConfigurations();
  }

  @Override
  public List<? extends OCResolveConfiguration> getConfigurationsForFile(
      @Nullable VirtualFile sourceFile) {
    if (sourceFile == null || !sourceFile.isValid()) {
      return ImmutableList.of();
    }
    OCResolveConfiguration config = resolverResult.getConfigurationForFile(sourceFile);
    return config == null ? ImmutableList.of() : ImmutableList.of(config);
  }

  @Override
  public void projectClosed() {}

  @Override
  public void initComponent() {}

  @Override
  public void disposeComponent() {}

  @Override
  public String getComponentName() {
    return this.getClass().getName();
  }

  public OCWorkspace getWorkspace() {
    return this;
  }
}
