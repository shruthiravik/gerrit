// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestResource.HasETag;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.Assisted;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

public class ChangeResource implements RestResource, HasETag {
  /**
   * JSON format version number for ETag computations.
   *
   * <p>Should be bumped on any JSON format change (new fields, etc.) so that otherwise unmodified
   * changes get new ETags.
   */
  public static final int JSON_FORMAT_VERSION = 1;

  public static final TypeLiteral<RestView<ChangeResource>> CHANGE_KIND =
      new TypeLiteral<RestView<ChangeResource>>() {};

  public interface Factory {
    ChangeResource create(ChangeControl ctl);
  }

  private static final String ZERO_ID_STRING = ObjectId.zeroId().name();

  private final Provider<ReviewDb> db;
  private final AccountCache accountCache;
  private final ApprovalsUtil approvalUtil;
  private final PatchSetUtil patchSetUtil;
  private final PermissionBackend permissionBackend;
  private final StarredChangesUtil starredChangesUtil;
  private final ChangeControl control;

  @Inject
  ChangeResource(
      Provider<ReviewDb> db,
      AccountCache accountCache,
      ApprovalsUtil approvalUtil,
      PatchSetUtil patchSetUtil,
      PermissionBackend permissionBackend,
      StarredChangesUtil starredChangesUtil,
      @Assisted ChangeControl control) {
    this.db = db;
    this.accountCache = accountCache;
    this.approvalUtil = approvalUtil;
    this.patchSetUtil = patchSetUtil;
    this.permissionBackend = permissionBackend;
    this.starredChangesUtil = starredChangesUtil;
    this.control = control;
  }

  public PermissionBackend.ForChange permissions() {
    return permissionBackend.user(getControl().getUser()).change(getNotes());
  }

  public ChangeControl getControl() {
    return control;
  }

  public CurrentUser getUser() {
    return getControl().getUser();
  }

  public Change.Id getId() {
    return getControl().getId();
  }

  /** @return true if {@link #getUser()} is the change's owner. */
  public boolean isUserOwner() {
    CurrentUser user = getControl().getUser();
    Account.Id owner = getChange().getOwner();
    return user.isIdentifiedUser() && user.asIdentifiedUser().getAccountId().equals(owner);
  }

  public Change getChange() {
    return getControl().getChange();
  }

  public Project.NameKey getProject() {
    return getChange().getProject();
  }

  public ChangeNotes getNotes() {
    return getControl().getNotes();
  }

  // This includes all information relevant for ETag computation
  // unrelated to the UI.
  public void prepareETag(Hasher h, CurrentUser user) {
    h.putInt(JSON_FORMAT_VERSION)
        .putLong(getChange().getLastUpdatedOn().getTime())
        .putInt(getChange().getRowVersion())
        .putInt(user.isIdentifiedUser() ? user.getAccountId().get() : 0);

    if (user.isIdentifiedUser()) {
      for (AccountGroup.UUID uuid : user.getEffectiveGroups().getKnownGroups()) {
        h.putBytes(uuid.get().getBytes(UTF_8));
      }
    }

    byte[] buf = new byte[20];
    Set<Account.Id> accounts = new HashSet<>();
    accounts.add(getChange().getOwner());
    if (getChange().getAssignee() != null) {
      accounts.add(getChange().getAssignee());
    }
    try {
      patchSetUtil
          .byChange(db.get(), getNotes())
          .stream()
          .map(ps -> ps.getUploader())
          .forEach(accounts::add);

      // It's intentional to include the states for *all* reviewers into the ETag computation.
      // We need the states of all current reviewers and CCs because they are part of ChangeInfo.
      // Including removed reviewers is a cheap way of making sure that the states of accounts that
      // posted a message on the change are included. Loading all change messages to find the exact
      // set of accounts that posted a message is too expensive. However everyone who posts a
      // message is automatically added as reviewer. Hence if we include removed reviewers we can
      // be sure that we have all accounts that posted messages on the change.
      accounts.addAll(approvalUtil.getReviewers(db.get(), getNotes()).all());
    } catch (OrmException e) {
      // This ETag will be invalidated if it loads next time.
    }
    accounts.stream().forEach(a -> hashAccount(h, accountCache.get(a), buf));

    ObjectId noteId;
    try {
      noteId = getNotes().loadRevision();
    } catch (OrmException e) {
      noteId = null; // This ETag will be invalidated if it loads next time.
    }
    hashObjectId(h, noteId, buf);
    // TODO(dborowitz): Include more NoteDb and other related refs, e.g. drafts
    // and edits.

    for (ProjectState p : control.getProjectControl().getProjectState().tree()) {
      hashObjectId(h, p.getConfig().getRevision(), buf);
    }
  }

  @Override
  public String getETag() {
    CurrentUser user = control.getUser();
    Hasher h = Hashing.murmur3_128().newHasher();
    if (user.isIdentifiedUser()) {
      h.putString(starredChangesUtil.getObjectId(user.getAccountId(), getId()).name(), UTF_8);
    }
    prepareETag(h, user);
    return h.hash().toString();
  }

  private void hashObjectId(Hasher h, ObjectId id, byte[] buf) {
    MoreObjects.firstNonNull(id, ObjectId.zeroId()).copyRawTo(buf, 0);
    h.putBytes(buf);
  }

  private void hashAccount(Hasher h, AccountState accountState, byte[] buf) {
    h.putString(
        MoreObjects.firstNonNull(accountState.getAccount().getMetaId(), ZERO_ID_STRING), UTF_8);
    accountState.getExternalIds().stream().forEach(e -> hashObjectId(h, e.blobId(), buf));
  }
}
