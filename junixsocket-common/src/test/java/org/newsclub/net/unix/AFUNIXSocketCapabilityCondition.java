/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AFUNIXSocketCapabilityCondition implements ExecutionCondition {
  @SuppressWarnings("exports")
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    AFUNIXSocketCapability[] requiredCapabilities = {};

    Optional<AnnotatedElement> element = context.getElement();
    if (element.isPresent()) {
      AFUNIXSocketCapabilityRequirement requirement = element.get().getAnnotation(
          AFUNIXSocketCapabilityRequirement.class);
      if (requirement != null) {
        requiredCapabilities = requirement.value();
      }
    }

    List<AFUNIXSocketCapability> unsupported = new ArrayList<>(requiredCapabilities.length);
    for (AFUNIXSocketCapability capability : requiredCapabilities) {
      if (!AFUNIXSocket.supports(capability)) {
        unsupported.add(capability);
      }
    }

    ConditionEvaluationResult result;
    if (unsupported.isEmpty()) {
      result = ConditionEvaluationResult.enabled(
          "AFUNIXSocket environment supports all required capabilities: " + Arrays.toString(
              requiredCapabilities));
    } else {
      result = ConditionEvaluationResult.disabled(
          "AFUNIXSocket environment does not support the following capabilities: " + unsupported);
    }

    return result;
  }
}
