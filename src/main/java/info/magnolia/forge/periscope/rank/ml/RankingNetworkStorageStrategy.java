/**
 * This file Copyright (c) 2019 Magnolia International
 * Ltd.  (http://www.magnolia-cms.com). All rights reserved.
 *
 *
 * This program and the accompanying materials are made
 * available under the terms of the Magnolia Network Agreement
 * which accompanies this distribution, and is available at
 * http://www.magnolia-cms.com/mna.html
 *
 * Any modifications to this file must keep this entire header
 * intact.
 *
 */
package info.magnolia.forge.periscope.rank.ml;

import info.magnolia.cms.security.User;

import java.util.Optional;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;

import lombok.Value;

/**
 * Allows to plugin different loading and persistence strategies for the search result ranking neural network.
 *
 * <p>A ranking neural network is assumed to be bound to a {@link User}, whether by name, role
 * or other user properties.
 */
public interface RankingNetworkStorageStrategy {

    void store(RankingInfo rankingInfo) throws RankingNetworkStorageException;

    Optional<RankingInfo> load(User user) throws RankingNetworkStorageException;

    /**
     * Container for information around ranking. That is, a neural network, corresponding labels and a user.
     */
    @Value
    final class RankingInfo {
        final MultiLayerNetwork network;
        final IndexedBuffer<String> labels;
        final User user;
    }
}
