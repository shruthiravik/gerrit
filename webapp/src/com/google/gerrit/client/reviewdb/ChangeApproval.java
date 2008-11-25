// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

import java.sql.Timestamp;

/** An approval (or negative approval) on a change. */
public final class ChangeApproval {
  public static class Key implements com.google.gwtorm.client.Key<Change.Id> {
    @Column
    protected Change.Id changeId;

    @Column
    protected Account.Id accountId;

    @Column
    protected ApprovalCategory.Id categoryId;

    protected Key() {
      changeId = new Change.Id();
      accountId = new Account.Id();
      categoryId = new ApprovalCategory.Id();
    }

    public Key(final Change.Id change, final Account.Id a,
        final ApprovalCategory.Id c) {
      this.changeId = change;
      this.accountId = a;
      this.categoryId = c;
    }

    public Change.Id getParentKey() {
      return changeId;
    }

    @Override
    public int hashCode() {
      int h = changeId.hashCode();
      h *= 31;
      h += accountId.hashCode();
      h *= 31;
      h += categoryId.hashCode();
      return h;
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof Key && ((Key) o).changeId.equals(changeId)
          && ((Key) o).accountId.equals(accountId)
          && ((Key) o).categoryId.equals(categoryId);
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  /**
   * Value assigned by the user.
   * <p>
   * The precise meaning of "value" is up to each category.
   * <p>
   * In general:
   * <ul>
   * <li><b>&lt; 0:</b> The approval is rejected/revoked.</li>
   * <li><b>= 0:</b> No indication either way is provided.</li>
   * <li><b>&gt; 0:</b> The approval is approved/positive.</li>
   * </ul>
   * and in the negative and positive direction a magnitude can be assumed.The
   * further from 0 the more assertive the approval.
   */
  @Column
  protected short value;

  @Column
  protected Timestamp granted;

  protected ChangeApproval() {
  }

  public ChangeApproval(final ChangeApproval.Key k) {
    key = k;
    setValue((short) 0);
  }

  public Change.Id getChangeId() {
    return key.changeId;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public ApprovalCategory.Id getCategoryId() {
    return key.categoryId;
  }

  public short getValue() {
    return value;
  }

  public void setValue(final short v) {
    value = v;
    granted = new Timestamp(System.currentTimeMillis());
  }
}
