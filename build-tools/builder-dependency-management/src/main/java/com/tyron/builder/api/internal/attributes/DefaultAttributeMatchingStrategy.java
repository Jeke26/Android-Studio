/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tyron.builder.api.internal.attributes;

import com.tyron.builder.api.attributes.AttributeMatchingStrategy;
import com.tyron.builder.api.attributes.CompatibilityRuleChain;
import com.tyron.builder.api.attributes.DisambiguationRuleChain;
import com.tyron.builder.internal.instantiation.InstantiatorFactory;
import com.tyron.builder.internal.Cast;
import com.tyron.builder.internal.isolation.IsolatableFactory;

import java.util.Comparator;

public class DefaultAttributeMatchingStrategy<T> implements AttributeMatchingStrategy<T> {
    private final CompatibilityRuleChain<T> compatibilityRules;
    private final DisambiguationRuleChain<T> disambiguationRules;

    public DefaultAttributeMatchingStrategy(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory) {
        compatibilityRules = Cast.uncheckedCast(instantiatorFactory.decorateLenient().newInstance(DefaultCompatibilityRuleChain.class, instantiatorFactory.inject(), isolatableFactory));
        disambiguationRules = Cast.uncheckedCast(instantiatorFactory.decorateLenient().newInstance(DefaultDisambiguationRuleChain.class, instantiatorFactory.inject(), isolatableFactory));
    }

    @Override
    public CompatibilityRuleChain<T> getCompatibilityRules() {
        return compatibilityRules;
    }

    @Override
    public DisambiguationRuleChain<T> getDisambiguationRules() {
        return disambiguationRules;
    }

    @Override
    public void ordered(Comparator<T> comparator) {
        ordered(true, comparator);
    }

    @Override
    public void ordered(boolean pickLast, Comparator<T> comparator) {
        compatibilityRules.ordered(comparator);
        if (pickLast) {
            disambiguationRules.pickLast(comparator);
        } else {
            disambiguationRules.pickFirst(comparator);
        }
    }
}
