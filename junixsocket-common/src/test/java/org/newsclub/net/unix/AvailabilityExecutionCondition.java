/**
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AvailabilityExecutionCondition implements ExecutionCondition {
  @SuppressWarnings("exports")
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    String[] requiredClasses = {};

    String message = "";

    Optional<AnnotatedElement> element = context.getElement();
    if (element.isPresent()) {
      AvailabilityRequirement requirement = element.get().getAnnotation(
          AvailabilityRequirement.class);
      if (requirement != null) {
        requiredClasses = requirement.classes();
        message = requirement.message();
      }
    }

    if (requiredClasses.length == 0) {
      return ConditionEvaluationResult.enabled("Unconditional execution");
    }

    List<String> unsupported = new ArrayList<>();
    for (String requiredClass : requiredClasses) {
      try {
        Class.forName(requiredClass, false, null);
      } catch (Exception e) {
        unsupported.add(requiredClass);
      }
    }

    ConditionEvaluationResult result;
    if (unsupported.isEmpty()) {
      result = ConditionEvaluationResult.enabled("All expected classes could be resolved");
    } else {
      if (message.isEmpty()) {
        result = ConditionEvaluationResult.disabled("Skipping test due to missing classes: "
            + unsupported);
      } else {
        result = ConditionEvaluationResult.disabled(message);
      }
    }

    return result;
  }
}
