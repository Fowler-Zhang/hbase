/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.quotas;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.hbase.classification.InterfaceStability;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.protobuf.generated.QuotaProtos.Quotas;
import org.apache.hadoop.hbase.protobuf.generated.QuotaProtos.Throttle;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;

import com.google.common.collect.Sets;

/**
 * In-Memory state of table or namespace quotas
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class QuotaState {
  protected long lastUpdate = 0;
  protected long lastQuery = 0;

  protected QuotaLimiter globalLimiter = NoopQuotaLimiter.get();

  public QuotaState() {
    this(0);
  }

  public QuotaState(final long updateTs) {
    lastUpdate = updateTs;
  }

  public long getLastUpdate() {
    return lastUpdate;
  }

  public synchronized long getLastQuery() {
    return lastQuery;
  }

  @Override
  public synchronized String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("QuotaState(ts=" + lastUpdate);
    if (isBypass()) {
      builder.append(" bypass");
    } else {
      if (globalLimiter != NoopQuotaLimiter.get()) {
        //builder.append(" global-limiter");
        builder.append(" " + globalLimiter);
      }
    }
    builder.append(')');
    return builder.toString();
  }

  /**
   * @return true if there is no quota information associated to this object
   */
  public synchronized boolean isBypass() {
    return globalLimiter == NoopQuotaLimiter.get();
  }

  /**
   * Setup the global quota information.
   * (This operation is part of the QuotaState setup)
   */
  public void setQuotas(final Quotas quotas) {
    if (quotas.hasThrottle()) {
      globalLimiter = QuotaLimiterFactory.fromThrottle(quotas.getThrottle());
    } else {
      globalLimiter = NoopQuotaLimiter.get();
    }
  }

  /**
   * Perform an update of the quota info based on the other quota info object.
   * (This operation is executed by the QuotaCache)
   */
  public synchronized void update(final QuotaState other) {
    if (globalLimiter == NoopQuotaLimiter.get()) {
      globalLimiter = other.globalLimiter;
    } else if (other.globalLimiter == NoopQuotaLimiter.get()) {
      globalLimiter = NoopQuotaLimiter.get();
    } else {
      globalLimiter = QuotaLimiterFactory.update(globalLimiter, other.globalLimiter);
    }
    lastUpdate = other.lastUpdate;
  }

  /**
   * Return the limiter associated with this quota.
   * @return the quota limiter
   */
  public synchronized QuotaLimiter getGlobalLimiter() {
    lastQuery = EnvironmentEdgeManager.currentTime();
    return globalLimiter;
  }
}
