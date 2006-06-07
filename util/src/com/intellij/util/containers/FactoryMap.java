/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
 */
public abstract class FactoryMap<T,V> {
  private static final Object NULL_KEY = new Object();
  private final Map<T,V> myMap = new THashMap<T, V>();

  @NotNull
  protected abstract V create(T key);

  @NotNull
  public final V get(T key) {
    V v = myMap.get(key == null ? (T)NULL_KEY : key);
    if (v == null) {
      myMap.put(key == null ? (T)NULL_KEY : key, v = create(key));
    }
    return v;
  }

  public final boolean containsKey(T key) {
    return myMap.containsKey(key);
  }

}
