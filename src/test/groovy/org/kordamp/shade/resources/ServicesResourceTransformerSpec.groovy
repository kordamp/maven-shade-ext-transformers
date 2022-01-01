/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2014-2022 Andres Almiray.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kordamp.shade.resources

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ServicesResourceTransformerSpec extends Specification {
    void "Resource #resource with path #path #transform transformed"() {
        given:
        ServicesResourceTransformer transformer = new ServicesResourceTransformer(path: path)

        when:
        boolean actual = transformer.canTransformResource(resource)

        then:
        actual == expected

        where:
        path                | resource                                                   || expected
        'META-INF/services' | 'META-INF/services/java.lang.Object'                       || true
        'META-INF/griffon'  | 'META-INF/griffon/griffon.core.artifact.GriffonController' || true
        'META-INF/services' | 'META-INF/griffon/griffon.core.artifact.GriffonController' || false
        'META-INF/griffon'  | 'META-INF/griffon-core.properties'                         || false

        transform = expected ? 'can be' : 'can not be'
    }
}
