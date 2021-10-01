/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.util;

import android.annotation.NonNull;

/**
 * Objects that contains a list of {@link Dumpable}, which will be dumped when the object itself
 * is dumped.
 */
public interface DumpableContainer {

    /**
     * Adds the given {@link Dumpable dumpable} to the container.
     *
     * <p>If a dumpable with the same {@link Dumpable#getDumpableName() name} was added before, this
     * call is ignored.
     *
     * @param dumpable dumpable to be added.
     *
     * @throws IllegalArgumentException if the {@link Dumpable#getDumpableName() dumpable name} is
     * {@code null}.
     *
     * @return {@code true} if the dumpable was added, {@code false} if the call was ignored.
     */
    boolean addDumpable(@NonNull Dumpable dumpable);
}
