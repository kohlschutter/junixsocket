/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
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
package org.newsclub.net.unix;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SystemPropertyExecutionCondition implements ExecutionCondition {
  @SuppressWarnings("exports")
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    String property = null;
    String value = null;

    String message = "";

    Optional<AnnotatedElement> element = context.getElement();
    if (element.isPresent()) {
      @SuppressWarnings("null")
      SystemPropertyRequirement requirement = element.get().getAnnotation(
          SystemPropertyRequirement.class);
      if (requirement != null) {
        property = requirement.property();
        value = requirement.value();
        message = requirement.message();
      }
    }

    if (property == null) {
      return ConditionEvaluationResult.enabled("Unconditional execution");
    }

    String propVal = System.getProperty(property);
    if (value == null || value.isEmpty()) {
      if (propVal == null || propVal.isEmpty()) {
        return ConditionEvaluationResult.enabled("Property not set, as expected");
      } else {
        return ConditionEvaluationResult.disabled(message);
      }
    }

    if (value.equals(propVal)) {
      return ConditionEvaluationResult.enabled("Property set to correct value, as expected");
    } else {
      return ConditionEvaluationResult.disabled(message);
    }
  }
}
