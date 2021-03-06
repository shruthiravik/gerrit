// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.api.projects;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.access.ProjectAccessInfo;
import com.google.gerrit.extensions.api.access.ProjectAccessInput;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ChildProjectApi;
import com.google.gerrit.extensions.api.projects.CommitApi;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.api.projects.DeleteTagsInput;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.TagApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChildProjectsCollection;
import com.google.gerrit.server.project.CommitsCollection;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.DeleteBranches;
import com.google.gerrit.server.project.DeleteTags;
import com.google.gerrit.server.project.GetAccess;
import com.google.gerrit.server.project.GetConfig;
import com.google.gerrit.server.project.GetDescription;
import com.google.gerrit.server.project.ListBranches;
import com.google.gerrit.server.project.ListChildProjects;
import com.google.gerrit.server.project.ListTags;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsCollection;
import com.google.gerrit.server.project.PutConfig;
import com.google.gerrit.server.project.PutDescription;
import com.google.gerrit.server.project.SetAccess;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.List;

public class ProjectApiImpl implements ProjectApi {
  interface Factory {
    ProjectApiImpl create(ProjectResource project);

    ProjectApiImpl create(String name);
  }

  private final CurrentUser user;
  private final PermissionBackend permissionBackend;
  private final CreateProject.Factory createProjectFactory;
  private final ProjectApiImpl.Factory projectApi;
  private final ProjectsCollection projects;
  private final GetDescription getDescription;
  private final PutDescription putDescription;
  private final ChildProjectApiImpl.Factory childApi;
  private final ChildProjectsCollection children;
  private final ProjectResource project;
  private final ProjectJson projectJson;
  private final String name;
  private final BranchApiImpl.Factory branchApi;
  private final TagApiImpl.Factory tagApi;
  private final GetAccess getAccess;
  private final SetAccess setAccess;
  private final GetConfig getConfig;
  private final PutConfig putConfig;
  private final ListBranches listBranches;
  private final ListTags listTags;
  private final DeleteBranches deleteBranches;
  private final DeleteTags deleteTags;
  private final CommitsCollection commitsCollection;
  private final CommitApiImpl.Factory commitApi;

  @AssistedInject
  ProjectApiImpl(
      CurrentUser user,
      PermissionBackend permissionBackend,
      CreateProject.Factory createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      TagApiImpl.Factory tagApiFactory,
      GetAccess getAccess,
      SetAccess setAccess,
      GetConfig getConfig,
      PutConfig putConfig,
      ListBranches listBranches,
      ListTags listTags,
      DeleteBranches deleteBranches,
      DeleteTags deleteTags,
      CommitsCollection commitsCollection,
      CommitApiImpl.Factory commitApi,
      @Assisted ProjectResource project) {
    this(
        user,
        permissionBackend,
        createProjectFactory,
        projectApi,
        projects,
        getDescription,
        putDescription,
        childApi,
        children,
        projectJson,
        branchApiFactory,
        tagApiFactory,
        getAccess,
        setAccess,
        getConfig,
        putConfig,
        listBranches,
        listTags,
        deleteBranches,
        deleteTags,
        project,
        commitsCollection,
        commitApi,
        null);
  }

  @AssistedInject
  ProjectApiImpl(
      CurrentUser user,
      PermissionBackend permissionBackend,
      CreateProject.Factory createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      TagApiImpl.Factory tagApiFactory,
      GetAccess getAccess,
      SetAccess setAccess,
      GetConfig getConfig,
      PutConfig putConfig,
      ListBranches listBranches,
      ListTags listTags,
      DeleteBranches deleteBranches,
      DeleteTags deleteTags,
      CommitsCollection commitsCollection,
      CommitApiImpl.Factory commitApi,
      @Assisted String name) {
    this(
        user,
        permissionBackend,
        createProjectFactory,
        projectApi,
        projects,
        getDescription,
        putDescription,
        childApi,
        children,
        projectJson,
        branchApiFactory,
        tagApiFactory,
        getAccess,
        setAccess,
        getConfig,
        putConfig,
        listBranches,
        listTags,
        deleteBranches,
        deleteTags,
        null,
        commitsCollection,
        commitApi,
        name);
  }

  private ProjectApiImpl(
      CurrentUser user,
      PermissionBackend permissionBackend,
      CreateProject.Factory createProjectFactory,
      ProjectApiImpl.Factory projectApi,
      ProjectsCollection projects,
      GetDescription getDescription,
      PutDescription putDescription,
      ChildProjectApiImpl.Factory childApi,
      ChildProjectsCollection children,
      ProjectJson projectJson,
      BranchApiImpl.Factory branchApiFactory,
      TagApiImpl.Factory tagApiFactory,
      GetAccess getAccess,
      SetAccess setAccess,
      GetConfig getConfig,
      PutConfig putConfig,
      ListBranches listBranches,
      ListTags listTags,
      DeleteBranches deleteBranches,
      DeleteTags deleteTags,
      ProjectResource project,
      CommitsCollection commitsCollection,
      CommitApiImpl.Factory commitApi,
      String name) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.createProjectFactory = createProjectFactory;
    this.projectApi = projectApi;
    this.projects = projects;
    this.getDescription = getDescription;
    this.putDescription = putDescription;
    this.childApi = childApi;
    this.children = children;
    this.projectJson = projectJson;
    this.project = project;
    this.name = name;
    this.branchApi = branchApiFactory;
    this.tagApi = tagApiFactory;
    this.getAccess = getAccess;
    this.setAccess = setAccess;
    this.getConfig = getConfig;
    this.putConfig = putConfig;
    this.listBranches = listBranches;
    this.listTags = listTags;
    this.deleteBranches = deleteBranches;
    this.deleteTags = deleteTags;
    this.commitsCollection = commitsCollection;
    this.commitApi = commitApi;
  }

  @Override
  public ProjectApi create() throws RestApiException {
    return create(new ProjectInput());
  }

  @Override
  public ProjectApi create(ProjectInput in) throws RestApiException {
    try {
      if (name == null) {
        throw new ResourceConflictException("Project already exists");
      }
      if (in.name != null && !name.equals(in.name)) {
        throw new BadRequestException("name must match input.name");
      }
      CreateProject impl = createProjectFactory.create(name);
      permissionBackend.user(user).checkAny(GlobalPermission.fromAnnotation(impl.getClass()));
      impl.apply(TopLevelResource.INSTANCE, in);
      return projectApi.create(projects.parse(name));
    } catch (Exception e) {
      throw asRestApiException("Cannot create project: " + e.getMessage(), e);
    }
  }

  @Override
  public ProjectInfo get() throws RestApiException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return projectJson.format(project.getProjectState());
  }

  @Override
  public String description() throws RestApiException {
    return getDescription.apply(checkExists());
  }

  @Override
  public ProjectAccessInfo access() throws RestApiException {
    try {
      return getAccess.apply(checkExists());
    } catch (Exception e) {
      throw asRestApiException("Cannot get access rights", e);
    }
  }

  @Override
  public ProjectAccessInfo access(ProjectAccessInput p) throws RestApiException {
    try {
      return setAccess.apply(checkExists(), p);
    } catch (Exception e) {
      throw asRestApiException("Cannot put access rights", e);
    }
  }

  @Override
  public void description(DescriptionInput in) throws RestApiException {
    try {
      putDescription.apply(checkExists(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot put project description", e);
    }
  }

  @Override
  public ConfigInfo config() throws RestApiException {
    return getConfig.apply(checkExists());
  }

  @Override
  public ConfigInfo config(ConfigInput in) throws RestApiException {
    return putConfig.apply(checkExists(), in);
  }

  @Override
  public ListRefsRequest<BranchInfo> branches() {
    return new ListRefsRequest<BranchInfo>() {
      @Override
      public List<BranchInfo> get() throws RestApiException {
        return listBranches(this);
      }
    };
  }

  private List<BranchInfo> listBranches(ListRefsRequest<BranchInfo> request)
      throws RestApiException {
    listBranches.setLimit(request.getLimit());
    listBranches.setStart(request.getStart());
    listBranches.setMatchSubstring(request.getSubstring());
    listBranches.setMatchRegex(request.getRegex());
    try {
      return listBranches.apply(checkExists());
    } catch (Exception e) {
      throw asRestApiException("Cannot list branches", e);
    }
  }

  @Override
  public ListRefsRequest<TagInfo> tags() {
    return new ListRefsRequest<TagInfo>() {
      @Override
      public List<TagInfo> get() throws RestApiException {
        return listTags(this);
      }
    };
  }

  private List<TagInfo> listTags(ListRefsRequest<TagInfo> request) throws RestApiException {
    listTags.setLimit(request.getLimit());
    listTags.setStart(request.getStart());
    listTags.setMatchSubstring(request.getSubstring());
    listTags.setMatchRegex(request.getRegex());
    try {
      return listTags.apply(checkExists());
    } catch (Exception e) {
      throw asRestApiException("Cannot list tags", e);
    }
  }

  @Override
  public List<ProjectInfo> children() throws RestApiException {
    return children(false);
  }

  @Override
  public List<ProjectInfo> children(boolean recursive) throws RestApiException {
    ListChildProjects list = children.list();
    list.setRecursive(recursive);
    try {
      return list.apply(checkExists());
    } catch (Exception e) {
      throw asRestApiException("Cannot list children", e);
    }
  }

  @Override
  public ChildProjectApi child(String name) throws RestApiException {
    try {
      return childApi.create(children.parse(checkExists(), IdString.fromDecoded(name)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse child project", e);
    }
  }

  @Override
  public BranchApi branch(String ref) throws ResourceNotFoundException {
    return branchApi.create(checkExists(), ref);
  }

  @Override
  public TagApi tag(String ref) throws ResourceNotFoundException {
    return tagApi.create(checkExists(), ref);
  }

  @Override
  public void deleteBranches(DeleteBranchesInput in) throws RestApiException {
    try {
      deleteBranches.apply(checkExists(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete branches", e);
    }
  }

  @Override
  public void deleteTags(DeleteTagsInput in) throws RestApiException {
    try {
      deleteTags.apply(checkExists(), in);
    } catch (Exception e) {
      throw asRestApiException("Cannot delete tags", e);
    }
  }

  @Override
  public CommitApi commit(String commit) throws RestApiException {
    try {
      return commitApi.create(commitsCollection.parse(checkExists(), IdString.fromDecoded(commit)));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse commit", e);
    }
  }

  private ProjectResource checkExists() throws ResourceNotFoundException {
    if (project == null) {
      throw new ResourceNotFoundException(name);
    }
    return project;
  }
}
