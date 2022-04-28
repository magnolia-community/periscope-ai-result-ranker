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

import info.magnolia.cms.beans.config.ServerConfiguration;
import info.magnolia.cms.security.User;
import info.magnolia.objectfactory.ComponentProvider;
import info.magnolia.periscope.PeriscopeModule;
import info.magnolia.periscope.rank.AbstractResultRankerFactory;
import info.magnolia.periscope.rank.ResultRanker;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

/**
 * This factory creates a {@code NeuralNetworkResultRanker} for a given user.
 */
@Singleton
@Slf4j
class NeuralNetworkResultRankerFactory extends AbstractResultRankerFactory {

    private final ComponentProvider componentProvider;
    private final Provider<PeriscopeModule> periscopeModuleProvider;

    @Inject
    NeuralNetworkResultRankerFactory(ServerConfiguration configuration, ComponentProvider componentProvider, Provider<PeriscopeModule> periscopeModuleProvider) {
        super(configuration, periscopeModuleProvider);
        this.componentProvider = componentProvider;
        this.periscopeModuleProvider = periscopeModuleProvider;
    }

    @Override
    protected ResultRanker doCreateRanker(User user) {
        log.debug("Creating ResultRanker for user {}", user.getName());
        return componentProvider.newInstance(ResultRanker.class, user);
    }
}
