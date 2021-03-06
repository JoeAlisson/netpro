/*
 * Copyright 2011-2015 L2EMU UNIQUE
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
package net.l2emuproject.proxy.network.meta.modifier;

import net.l2emuproject.proxy.network.meta.FieldValueModifier;

/**
 * A modifier for string values.
 * 
 * @author _dev_
 */
public interface StringModifier extends FieldValueModifier
{
	/**
	 * Applies value modifications.
	 * 
	 * @param value value
	 * @return modified value
	 */
	String apply(String value);
}
