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

/**
 * Thrown when an error occurs while storing or loading a neural network.
 *
 * @see RankingNetworkStorageStrategy
 */
public class RankingNetworkStorageException extends Exception {

    public RankingNetworkStorageException(Throwable cause) {
        super(cause);
    }
}
