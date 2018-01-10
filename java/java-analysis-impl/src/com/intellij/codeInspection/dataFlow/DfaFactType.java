/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A type of the fact which restricts some value.
 *
 * @author Tagir Valeev
 */
public abstract class DfaFactType<T> extends Key<T> {
  private static final List<DfaFactType<?>> ourFactTypes = new ArrayList<>();

  /**
   * This fact specifies whether the value can be null. The absence of the fact means that the nullability is unknown.
   */
  public static final DfaFactType<Boolean> CAN_BE_NULL = new DfaFactType<Boolean>("Can be null") {
    @Override
    String toString(@NotNull Boolean fact) {
      return fact ? "Nullable" : "NotNull";
    }

    @Nullable
    @Override
    Boolean intersectFacts(@NotNull Boolean left, @NotNull Boolean right) {
      if (Boolean.FALSE.equals(left) || Boolean.FALSE.equals(right)) {
        return Boolean.FALSE;
      }
      return super.intersectFacts(left, right);
    }

    @Nullable
    @Override
    Boolean fromDfaValue(DfaValue value) {
      if (value instanceof DfaConstValue) {
        return ((DfaConstValue)value).getValue() == null;
      }
      if (value instanceof DfaBoxedValue || value instanceof DfaUnboxedValue) return false;
      if (value instanceof DfaFactMapValue) {
        DfaFactMapValue factValue = (DfaFactMapValue)value;
        if (factValue.get(OPTIONAL_PRESENCE) != null || factValue.get(RANGE) != null) return false;
      }
      return super.fromDfaValue(value);
    }

    @Nullable
    @Override
    Boolean calcFromVariable(@NotNull DfaVariableValue value) {
      return NullnessUtil.calcCanBeNull(value);
    }
  };

  public static final DfaFactType<Mutability> MUTABILITY = new DfaFactType<Mutability>("Mutable") {
    @Override
    boolean isUnknown(@NotNull Mutability fact) {
      return fact == Mutability.UNKNOWN;
    }

    @NotNull
    @Override
    Mutability calcFromVariable(@NotNull DfaVariableValue value) {
      PsiModifierListOwner variable = value.getPsiVariable();
      return Mutability.getMutability(variable);
    }
  };

  /**
   * This fact is applied to the Optional values (like {@link java.util.Optional} or Guava Optional).
   * When its value is true, then optional is known to be present.
   * When its value is false, then optional is known to be empty (absent).
   */
  public static final DfaFactType<Boolean> OPTIONAL_PRESENCE = new DfaFactType<Boolean>("Optional presense") {

    @Override
    String toString(@NotNull Boolean fact) {
      return fact ? "present Optional" : "absent Optional";
    }
  };

  /**
   * This fact is applied to the integral values (of types byte, char, short, int, long).
   * Its value represents a range of possible values.
   */
  public static final DfaFactType<LongRangeSet> RANGE = new DfaFactType<LongRangeSet>("Range") {
    @Override
    boolean isSuper(@Nullable LongRangeSet superFact, @Nullable LongRangeSet subFact) {
      return superFact == null || subFact != null && superFact.contains(subFact);
    }

    @Override
    boolean isUnknown(@NotNull LongRangeSet fact) {
      return LongRangeSet.all().equals(fact);
    }

    @Nullable
    @Override
    LongRangeSet fromDfaValue(DfaValue value) {
      if(value instanceof DfaVariableValue) {
        return calcFromVariable((DfaVariableValue)value);
      }
      return LongRangeSet.fromDfaValue(value);
    }

    @Nullable
    @Override
    LongRangeSet calcFromVariable(@NotNull DfaVariableValue var) {
      if (var.getQualifier() != null) {
        for (SpecialField sf : SpecialField.values()) {
          if (sf.isMyAccessor(var.getPsiVariable())) {
            return sf.getRange();
          }
        }
      }
      PsiModifierListOwner psiVariable = var.getPsiVariable();
      LongRangeSet fromType = LongRangeSet.fromType(var.getVariableType());
      return fromType == null ? null : LongRangeSet.fromPsiElement(psiVariable).intersect(fromType);
    }

    @Nullable
    @Override
    LongRangeSet unionFacts(@NotNull LongRangeSet left, @NotNull LongRangeSet right) {
      return left.union(right);
    }

    @Nullable
    @Override
    LongRangeSet intersectFacts(@NotNull LongRangeSet left, @NotNull LongRangeSet right) {
      LongRangeSet intersection = left.intersect(right);
      return intersection.isEmpty() ? null : intersection;
    }

    @Override
    String toString(@NotNull LongRangeSet fact) {
      return fact.toString();
    }
  };
  /**
   * This fact represents a set of possible types of this value
   * {@link TypeConstraint#EMPTY} value is equivalent to absent fact (not constrained)
   */
  public static final DfaFactType<TypeConstraint> TYPE_CONSTRAINT = new DfaFactType<TypeConstraint>("Type") {
    @Override
    boolean isSuper(@Nullable TypeConstraint superFact, @Nullable TypeConstraint subFact) {
      return superFact == null || (subFact != null && superFact.isSuperStateOf(subFact));
    }

    @Override
    boolean isUnknown(@NotNull TypeConstraint fact) {
      return fact.equals(TypeConstraint.EMPTY);
    }

    @Nullable
    @Override
    TypeConstraint intersectFacts(@NotNull TypeConstraint left, @NotNull TypeConstraint right) {
      for (DfaPsiType type : right.getInstanceofValues()) {
        left = left.withInstanceofValue(type);
        if (left == null) return null;
      }
      for (DfaPsiType type : right.getNotInstanceofValues()) {
        left = left.withNotInstanceofValue(type);
        if (left == null) return null;
      }
      return left;
    }

    @Nullable
    @Override
    TypeConstraint unionFacts(@NotNull TypeConstraint left, @NotNull TypeConstraint right) {
      return left.union(right);
    }
  };

  private DfaFactType(String name) {
    super("DfaFactType: " + name);
    // Thread-safe as all DfaFactType instances are created only from DfaFactType class static initializer
    ourFactTypes.add(this);
  }

  @Nullable
  T fromDfaValue(DfaValue value) {
    return value instanceof DfaFactMapValue ? ((DfaFactMapValue)value).get(this) : null;
  }

  // Could be expensive
  @Nullable
  T calcFromVariable(@NotNull DfaVariableValue value) {
    return null;
  }

  boolean isSuper(@Nullable T superFact, @Nullable T subFact) {
    return Objects.equals(superFact, subFact);
  }

  boolean isUnknown(@NotNull T fact) {
    return false;
  }

  /**
   * Intersects two facts of this type.
   *
   * @param left left fact
   * @param right right fact
   * @return intersection fact or null if facts are incompatible
   */
  @Nullable
  T intersectFacts(@NotNull T left, @NotNull T right) {
    return left.equals(right) ? left : null;
  }

  /**
   * Unites two facts of this type.
   *
   * @param left left fact
   * @param right right fact
   * @return union fact (null means that the fact can have any value)
   */
  @Nullable
  T unionFacts(@NotNull T left, @NotNull T right) {
    return left.equals(right) ? left : null;
  }

  String toString(@NotNull T fact) {
    return fact.toString();
  }

  static List<DfaFactType<?>> getTypes() {
    return Collections.unmodifiableList(ourFactTypes);
  }
}
