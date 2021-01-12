/*
 * Copyright 2019 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.xds;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import io.grpc.LoadBalancer.PickResult;
import io.grpc.LoadBalancer.PickSubchannelArgs;
import io.grpc.LoadBalancer.SubchannelPicker;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class WeightedRandomPicker extends SubchannelPicker {

  @VisibleForTesting
  final List<WeightedChildPicker> weightedChildPickers;

  private final ThreadSafeRandom random;
  private final int totalWeight;

  static final class WeightedChildPicker {
    private final int weight;
    private final SubchannelPicker childPicker;

    WeightedChildPicker(int weight, SubchannelPicker childPicker) {
      checkArgument(weight >= 0, "weight is negative");
      checkNotNull(childPicker, "childPicker is null");

      this.weight = weight;
      this.childPicker = childPicker;
    }

    int getWeight() {
      return weight;
    }

    SubchannelPicker getPicker() {
      return childPicker;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      WeightedChildPicker that = (WeightedChildPicker) o;
      return weight == that.weight && Objects.equals(childPicker, that.childPicker);
    }

    @Override
    public int hashCode() {
      return Objects.hash(weight, childPicker);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("weight", weight)
          .add("childPicker", childPicker)
          .toString();
    }
  }

  WeightedRandomPicker(List<WeightedChildPicker> weightedChildPickers) {
    this(weightedChildPickers, ThreadSafeRandom.ThreadSafeRandomImpl.instance);
  }

  @VisibleForTesting
  WeightedRandomPicker(List<WeightedChildPicker> weightedChildPickers, ThreadSafeRandom random) {
    checkNotNull(weightedChildPickers, "weightedChildPickers in null");
    checkArgument(!weightedChildPickers.isEmpty(), "weightedChildPickers is empty");

    this.weightedChildPickers = Collections.unmodifiableList(weightedChildPickers);

    int totalWeight = 0;
    for (WeightedChildPicker weightedChildPicker : weightedChildPickers) {
      int weight = weightedChildPicker.getWeight();
      totalWeight += weight;
    }
    this.totalWeight = totalWeight;

    this.random = random;
  }

  @Override
  public final PickResult pickSubchannel(PickSubchannelArgs args) {
    SubchannelPicker childPicker = null;

    if (totalWeight == 0) {
      childPicker =
          weightedChildPickers.get(random.nextInt(weightedChildPickers.size())).getPicker();
    } else {
      int rand = random.nextInt(totalWeight);

      // Find the first idx such that rand < accumulatedWeights[idx]
      // Not using Arrays.binarySearch for better readability.
      int accumulatedWeight = 0;
      for (int idx = 0; idx < weightedChildPickers.size(); idx++) {
        accumulatedWeight += weightedChildPickers.get(idx).getWeight();
        if (rand < accumulatedWeight) {
          childPicker = weightedChildPickers.get(idx).getPicker();
          break;
        }
      }
      checkNotNull(childPicker, "childPicker not found");
    }

    return childPicker.pickSubchannel(args);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("weightedChildPickers", weightedChildPickers)
        .add("totalWeight", totalWeight)
        .toString();
  }
}
