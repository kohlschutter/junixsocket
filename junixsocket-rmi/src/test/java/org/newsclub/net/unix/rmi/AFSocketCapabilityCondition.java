/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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
package org.newsclub.net.unix.rmi;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.newsclub.net.unix.AFSocket;
import org.newsclub.net.unix.AFSocketCapability;

// FIXME: Move this class into a unit-testing helper artifact.
// This is a deliberate copy of the same class from junixsocket-common's tests.
// CPD-OFF
public class AFSocketCapabilityCondition implements ExecutionCondition {
  @SuppressWarnings("exports")
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    AFSocketCapability[] requiredCapabilities = {};

    Optional<AnnotatedElement> element = context.getElement();
    if (element.isPresent()) {
      @SuppressWarnings("null")
      AFSocketCapabilityRequirement requirement = element.get().getAnnotation(
          AFSocketCapabilityRequirement.class);
      if (requirement != null) {
        requiredCapabilities = requirement.value();
      }
    }

    List<AFSocketCapability> unsupported = new ArrayList<>(requiredCapabilities.length);
    for (AFSocketCapability capability : requiredCapabilities) {
      if (!AFSocket.supports(capability)) {
        unsupported.add(capability);
      }
    }

    ConditionEvaluationResult result;
    if (unsupported.isEmpty()) {
      result = ConditionEvaluationResult.enabled("All capability requirements met: " + Arrays
          .toString(requiredCapabilities));
    } else {
      result = ConditionEvaluationResult.disabled("Missing capabilities: " + unsupported);
    }

    return result;
  }
}
