/*
 * Copyright 2018 Shingo Omura <https://github.com/everpeace>
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

package com.github.everpeace.k8s.throttler.crd.v1alpha1

import com.github.everpeace.k8s.throttler
import com.github.everpeace.k8s.throttler.crd.v1alpha1
import com.github.everpeace.k8s.throttler.crd.v1alpha1.Implicits._
import skuber.ResourceSpecification.Subresources
import skuber.apiextensions.CustomResourceDefinition
import skuber.{
  CustomResource,
  HasStatusSubresource,
  LabelSelector,
  Pod,
  ResourceDefinition,
  ResourceSpecification
}
import com.github.everpeace.k8s._

object Throttle {

  case class Selector(selectorTerms: List[SelectorItem])
  case class SelectorItem(podSelector: LabelSelector)

  case class Spec(
      throttlerName: String,
      selector: Selector,
      threshold: ResourceAmount,
      temporaryThresholdOverrides: List[TemporaryThresholdOverride] = List.empty)
      extends v1alpha1.Spec[Selector]

  case class Status(
      throttled: IsResourceAmountThrottled,
      used: ResourceAmount,
      calculatedThreshold: Option[CalculatedThreshold] = None)
      extends v1alpha1.Status

  val crd: CustomResourceDefinition = CustomResourceDefinition[v1alpha1.Throttle]

  def apply(name: String, spec: Spec) = CustomResource[Spec, Status](spec).withName(name)

  trait JsonFormat
      extends CalculatedThreshold.JsonFormat
      with TemporaryThresholdOverride.JsonFormat
      with ResourceAmount.JsonFormat {
    import play.api.libs.functional.syntax._
    import play.api.libs.json._
    import skuber.json.format.{maybeEmptyFormatMethods, jsPath2LabelSelFormat}

    implicit val throttleSelectorItemFmt: Format[SelectorItem] =
      (JsPath \ "podSelector").formatLabelSelector
        .inmap(v1alpha1.Throttle.SelectorItem.apply, unlift(v1alpha1.Throttle.SelectorItem.unapply))

    implicit val throttleSelectorFmt: Format[Selector] =
      (JsPath \ "selectorTerms")
        .formatMaybeEmptyList[SelectorItem]
        .inmap(v1alpha1.Throttle.Selector.apply, unlift(v1alpha1.Throttle.Selector.unapply))

    implicit val throttleSpecFmt: Format[v1alpha1.Throttle.Spec] = (
      (JsPath \ "throttlerName").formatMaybeEmptyString(true) and
        (JsPath \ "selector").format[v1alpha1.Throttle.Selector] and
        (JsPath \ "threshold").format[ResourceAmount] and
        (JsPath \ "temporaryThresholdOverrides")
          .formatMaybeEmptyList[v1alpha1.TemporaryThresholdOverride]
    )(v1alpha1.Throttle.Spec.apply, unlift(v1alpha1.Throttle.Spec.unapply))

    implicit val throttleStatusFmt: Format[v1alpha1.Throttle.Status] =
      Json.format[v1alpha1.Throttle.Status]
  }

  trait Syntax {

    implicit class ThrottleSelectorItemSyntax(selectorItem: SelectorItem) {
      def matches(pod: skuber.Pod): Boolean = {
        selectorItem.podSelector.matches(pod.metadata.labels)
      }
    }

    implicit class ThrottleSelectorSyntax(selector: Selector) {
      def matches(pod: skuber.Pod): Boolean = {
        selector.selectorTerms.exists(_.matches(pod))
      }
    }

    implicit class ThrottleSyntax(throttle: Throttle) {
      def isTarget(pod: Pod): Boolean = throttle.spec.selector.matches(pod)
    }
  }

  trait ResourceDefinitions {
    implicit val throttleResourceDefinition: ResourceDefinition[Throttle] =
      ResourceDefinition[Throttle](
        group = throttler.crd.Group,
        version = "v1alpha1",
        kind = throttler.crd.Throttle.Kind,
        scope = ResourceSpecification.Scope.Namespaced,
        singular = Option(throttler.crd.Throttle.SingularName),
        plural = Option(throttler.crd.Throttle.PluralName),
        shortNames = throttler.crd.Throttle.ShortNames,
        subresources = Some(Subresources().withStatusSubresource)
      )

    implicit val throttleStatusSubEnabled: HasStatusSubresource[Throttle] =
      CustomResource.statusMethodsEnabler[Throttle]
  }
}
