/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.object;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.utilities.NeverValidAssumption;

/**
 * A Shape is an immutable descriptor of the current object "shape" of a DynamicObject, i.e., object
 * layout, metadata ({@linkplain Shape#getDynamicType() type}, {@linkplain Shape#getFlags() flags}),
 * and a mapping of {@linkplain Property properties} to storage locations. This allows cached
 * {@link DynamicObjectLibrary} to do a simple shape check to determine the contents of an object
 * and do fast, constant-time property accesses.
 *
 * <p>
 * Shapes are shared between objects that assume the same shape if they follow the same shape
 * transitions, like adding the same properties in the same order, starting from a common root
 * shape. Shape transitions are automatically, weakly cached.
 *
 * <p>
 * Dynamic objects start off with an initial shape that has no instance properties (but may have
 * constant properties that are stored in the shape). Initial shapes are created via
 * {@link Shape#newBuilder()}.
 *
 * @see DynamicObject
 * @see Shape#newBuilder()
 * @see Property
 * @since 0.8 or earlier
 */
public abstract class Shape {
    static final int OBJECT_FLAGS_MASK = 0x0000_00ff;
    static final int OBJECT_FLAGS_SHIFT = 0;
    static final int OBJECT_SHARED = 1 << 16;
    static final int OBJECT_PROPERTY_ASSUMPTIONS = 1 << 17;

    /**
     * Creates a new root shape builder.
     *
     * @since 20.2.0
     */
    public static Builder newBuilder() {
        CompilerAsserts.neverPartOfCompilation();
        return new Builder();
    }

    /**
     * Builder class to construct initial {@link Shape} instances. A builder instance is not
     * thread-safe and must not be used from multiple threads at the same time.
     *
     * @see Shape#newBuilder()
     * @since 20.2.0
     */
    @SuppressWarnings("hiding")
    public static final class Builder {
        private static final Layout DEFAULT_LAYOUT = Layout.newLayout().type(DynamicObject.class).build();

        private Layout layout = DEFAULT_LAYOUT;
        private Object dynamicType = ObjectType.DEFAULT;
        private int shapeFlags;
        private boolean shared;
        private boolean propertyAssumptions;
        private Object sharedData;
        private Assumption singleContextAssumption;

        private Builder() {
        }

        /**
         * Sets custom object {@link Layout} (default: {@link DynamicObject} base class).
         *
         * Shortcut for {@code layout(Layout.newLayout().type(layoutClass).build())}.
         *
         * Enables the use of dynamic object fields declared in subclasses using the
         * {@code DynamicField} annotation.
         *
         * To enable implicit casts, use {@link #layout(Layout)} with
         * {@link Layout.Builder#addAllowedImplicitCast} instead.
         *
         * @param layoutClass custom object layout class
         * @see #layout(Layout)
         * @since 20.2.0
         */
        public Builder layout(Class<? extends DynamicObject> layoutClass) {
            if (!DynamicObject.class.isAssignableFrom(layoutClass)) {
                throw new IllegalArgumentException(String.format("Expected a subclass of %s but got: %s",
                                DynamicObject.class.getName(), layoutClass.getTypeName()));
            }
            this.layout = Layout.newLayout().type(layoutClass).build();
            return this;
        }

        /**
         * Sets custom object {@link Layout}.
         *
         * Enables the use of dynamic object fields declared in subclasses using the
         * {@code DynamicField} annotation.
         *
         * <p>
         * Examples:
         *
         * <pre>
         * <code>
         * public class MyObject extends DynamicObject implements TruffleObject {
         *     static final Layout LAYOUT = Layout.newLayout().type(MyObject.class).build();
         *
         *     &#64;DynamicField private Object _o1;
         *     &#64;DynamicField private Object _o2;
         *     &#64;DynamicField private long _i1;
         *     &#64;DynamicField private long _i2;
         *
         *     public MyObject(Shape shape) {
         *         super(shape);
         *     }
         * }
         *
         *
         * Shape myObjShape = Shape.newBuilder().layout(MyObject.LAYOUT).build();
         * MyObject obj = new MyObject(myObjShape);
         * </code>
         * </pre>
         *
         * <pre>
         * <code>
         * static final Layout LAYOUT_INT_LONG = Layout.newLayout().type(DynamicObject.class).addImplicitCast(ImplicitCast.IntToLong).build();
         *
         * Shape rootShape = Shape.newBuilder().layout(LAYOUT_INT_LONG).build();
         * </code>
         * </pre>
         *
         * @param layout custom object layout
         * @see Layout#newLayout()
         * @since 20.2.0
         */
        public Builder layout(Layout layout) {
            if (!DynamicObject.class.isAssignableFrom(layout.getType())) {
                throw new IllegalArgumentException(String.format("Expected a subclass of %s but got: %s",
                                DynamicObject.class.getName(), layout.getType().getTypeName()));
            }
            this.layout = layout;
            return this;
        }

        /**
         * Sets initial dynamic object type identifier.
         *
         * @param dynamicType type identifier object; an instance of {@link ObjectType}
         * @throws NullPointerException if the type identifier is {@code null}
         * @throws IllegalArgumentException if the type is not an instance of {@link ObjectType}
         * @since 20.2.0
         */
        public Builder dynamicType(Object dynamicType) {
            Objects.requireNonNull(dynamicType, "dynamicType");
            if (!(dynamicType instanceof ObjectType)) {
                throw new IllegalArgumentException("dynamicType must be an instance of ObjectType");
            }
            this.dynamicType = dynamicType;
            return this;
        }

        /**
         * Sets initial shape flags (default: 0). Currently limited to 8 bits.
         *
         * @param flags an int value in the range from 0 to 255 (inclusive)
         * @throws IllegalArgumentException if the flags value is not in the supported range
         * @see DynamicObjectLibrary#getShapeFlags(DynamicObject)
         * @see DynamicObjectLibrary#setShapeFlags(DynamicObject, int)
         * @since 20.2.0
         */
        public Builder shapeFlags(int flags) {
            if ((flags & ~OBJECT_FLAGS_MASK) != 0) {
                throw new IllegalArgumentException("flags must be in the range (0, 255)");
            }
            this.shapeFlags = flags;
            return this;
        }

        /**
         * If {@code true}, makes the object shared (default: {@code false}).
         *
         * @see Shape#isShared()
         * @since 20.2.0
         */
        public Builder shared(boolean isShared) {
            this.shared = isShared;
            return this;
        }

        /**
         * If {@code true}, enables the use of {@linkplain Shape#getPropertyAssumption(Object)
         * property assumptions} for this object shape and any derived shapes (default:
         * {@code false}). Property assumptions allow speculating on select properties being absent
         * or stable across shape changes.
         *
         * <p>
         * Use of property assumptions can be beneficial in single-context mode for long-lived
         * objects with stable properties but recurrent shape changes due to properties being added
         * (e.g. global objects of a context), in which case a shape cache would be unstable while
         * the property assumption allows for a stable cache.
         *
         * @see Shape#getPropertyAssumption(Object)
         * @since 20.2.0
         */
        public Builder propertyAssumptions(boolean enable) {
            this.propertyAssumptions = enable;
            return this;
        }

        /**
         * Sets shared data to be associated with the root shape and any derived shapes (e.g. a
         * {@code TruffleLanguage} instance).
         *
         * @see Shape#getSharedData()
         * @since 20.2.0
         */
        public Builder sharedData(Object sharedData) {
            this.sharedData = sharedData;
            return this;
        }

        /**
         * Sets an assumption that allows specializations on constant object instances with this
         * shape, as long as the assumption is valid. The assumption should be valid only if code is
         * not shared across contexts and invalidated when this is not longer true. The assumption
         * may be {@code null} in which case this feature is disabled (the default).
         *
         * @see #propertyAssumptions(boolean)
         * @since 20.2.0
         */
        public Builder singleContextAssumption(Assumption assumption) {
            this.singleContextAssumption = assumption;
            return this;
        }

        /**
         * Builds a new root shape using the configuration of this builder.
         *
         * @since 20.2.0
         */
        public Shape build() {
            int flags = shapeFlags;
            if (shared) {
                flags = shapeFlags | OBJECT_SHARED;
            }
            if (propertyAssumptions) {
                flags = shapeFlags | OBJECT_PROPERTY_ASSUMPTIONS;
            }

            Shape shape = layout.buildShape(dynamicType, sharedData, flags, singleContextAssumption);

            assert shape.isShared() == shared && shape.getFlags() == shapeFlags && shape.getDynamicType() == dynamicType;
            return shape;
        }
    }

    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected Shape() {
    }

    /**
     * Get a property entry by key.
     *
     * @param key the identifier to look up
     * @return a Property object, or {@code null} if not found
     * @since 0.8 or earlier
     */
    public abstract Property getProperty(Object key);

    /**
     * Add a new property in the map, yielding a new or cached Shape object.
     *
     * Planned to be deprecated. Use {@link DynamicObjectLibrary#put} or
     * {@link DynamicObjectLibrary#putWithFlags} to add properties to an object.
     *
     * @param property the property to add
     * @return the new Shape
     * @since 0.8 or earlier
     */
    public abstract Shape addProperty(Property property);

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * Planned to be deprecated. Use {@link DynamicObjectLibrary#put} or
     * {@link DynamicObjectLibrary#putWithFlags} to add properties to an object.
     *
     * @return the shape after defining the property
     * @since 0.8 or earlier
     */
    public abstract Shape defineProperty(Object key, Object value, int flags);

    /**
     * Add or change property in the map, yielding a new or cached Shape object.
     *
     * @return the shape after defining the property
     * @since 0.8 or earlier
     * @deprecated Use {@link #defineProperty(Object, Object, int)} or
     *             {@link #addConstantProperty(Object, Object, int)}
     */
    @Deprecated
    public abstract Shape defineProperty(Object key, Object value, int flags, LocationFactory locationFactory);

    /**
     * An {@link Iterable} over the shape's properties in insertion order.
     *
     * @since 0.8 or earlier
     */
    public abstract Iterable<Property> getProperties();

    /**
     * Get a list of properties that this Shape stores.
     *
     * @return list of properties
     * @since 0.8 or earlier
     * @deprecated use {@link #getPropertyList()} instead
     */
    @Deprecated
    public abstract List<Property> getPropertyList(Pred<Property> filter);

    /**
     * Get a list of all properties that this Shape stores.
     *
     * @return list of properties
     * @since 0.8 or earlier
     */
    public abstract List<Property> getPropertyList();

    /**
     * Returns all (also hidden) property objects in this shape.
     *
     * @param ascending desired order ({@code true} for insertion order, {@code false} for reverse
     *            insertion order)
     * @since 0.8 or earlier
     */
    public abstract List<Property> getPropertyListInternal(boolean ascending);

    /**
     * Get a filtered list of property keys in insertion order.
     *
     * @since 0.8 or earlier
     * @deprecated use {@link #getKeyList()} instead
     */
    @Deprecated
    public abstract List<Object> getKeyList(Pred<Property> filter);

    /**
     * Get a list of all property keys in insertion order.
     *
     * @since 0.8 or earlier
     */
    public abstract List<Object> getKeyList();

    /**
     * Get all property keys in insertion order.
     *
     * @since 0.8 or earlier
     */
    public abstract Iterable<Object> getKeys();

    /**
     * Get an assumption that the shape is valid.
     *
     * @since 0.8 or earlier
     */
    public abstract Assumption getValidAssumption();

    /**
     * Check whether this shape is valid.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isValid();

    /**
     * Get an assumption that the shape is a leaf.
     *
     * @since 0.8 or earlier
     */
    public abstract Assumption getLeafAssumption();

    /**
     * Check whether this shape is a leaf in the transition graph, i.e. transitionless.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean isLeaf();

    /**
     * @return the parent shape or {@code null} if none.
     * @since 0.8 or earlier
     * @deprecated no replacement, do not rely on a specific parent shape
     */
    @Deprecated
    public abstract Shape getParent();

    /**
     * Check whether the shape has a property with the given key.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean hasProperty(Object key);

    /**
     * Remove the given property from the shape.
     *
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#removeKey} to remove properties from an object.
     */
    @Deprecated
    public abstract Shape removeProperty(Property property);

    /**
     * Replace a property in the shape.
     *
     * @since 0.8 or earlier
     * @deprecated Use {@link DynamicObjectLibrary#put} to replace properties in an object.
     */
    @Deprecated
    public abstract Shape replaceProperty(Property oldProperty, Property newProperty);

    /**
     * Get the last property.
     *
     * @since 0.8 or earlier
     */
    public abstract Property getLastProperty();

    /**
     * @see #getFlags()
     * @since 0.8 or earlier
     * @deprecated no replacement, returns 0
     */
    @Deprecated
    public abstract int getId();

    /**
     * Returns the shape flags.
     *
     * @see DynamicObjectLibrary#getShapeFlags(DynamicObject)
     * @see DynamicObjectLibrary#setShapeFlags(DynamicObject, int)
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    public int getFlags() {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a copy of the shape, with the shape flags set to {@code newFlags}.
     *
     * @param newFlags the new shape flags; an int value in the range from 0 to 255 (inclusive)
     * @throws IllegalArgumentException if the flags value is not in the supported range
     * @see Shape.Builder#shapeFlags(int)
     * @since 20.2.0
     */
    protected Shape setFlags(int newFlags) {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Append the property, relocating it to the next allocated location.
     *
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract Shape append(Property oldProperty);

    /**
     * Obtain an {@link Allocator} instance for the purpose of allocating locations.
     *
     * @since 0.8 or earlier
     */
    public abstract Allocator allocator();

    /**
     * Returns the number of properties in this shape.
     *
     * @since 0.8 or earlier
     */
    public abstract int getPropertyCount();

    /**
     * Get the shape's object type info.
     *
     * Planned to be deprecated. To be replaced by {@link #getDynamicType()}.
     *
     * @since 0.8 or earlier
     * @see #getDynamicType()
     */
    public abstract ObjectType getObjectType();

    /**
     * Get the shape's dynamic object type identifier (formerly {@link #getObjectType()}).
     *
     * @since 20.2.0
     */
    public Object getDynamicType() {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a copy of the shape, with the dynamic object type identifier set to
     * {@code objectType}. Currently, the object type must be an instance of {@link ObjectType}.
     *
     * @param objectType the new dynamic object type identifier
     * @throws IllegalArgumentException if the type is not an instance of {@link ObjectType}
     * @see Shape.Builder#dynamicType(Object)
     * @since 20.2.0
     */
    protected Shape setDynamicType(Object objectType) {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Get the root shape.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract Shape getRoot();

    /**
     * Checks whether the given object's shape is identical to this shape.
     *
     * @since 0.8 or earlier
     */
    public abstract boolean check(DynamicObject subject);

    /**
     * Get the shape's layout.
     *
     * @see Shape.Builder#layout(Layout)
     * @since 0.8 or earlier
     */
    public abstract Layout getLayout();

    /**
     * Get the shape's shared data.
     *
     * @see Shape.Builder#sharedData(Object)
     * @since 0.8 or earlier
     */
    public abstract Object getSharedData();

    /**
     * Query whether the shape has a transition with the given key.
     *
     * @since 0.8 or earlier
     * @deprecated the result of this method may change at any time
     */
    @Deprecated
    public abstract boolean hasTransitionWithKey(Object key);

    /**
     * Clone off a separate shape with new shared data.
     *
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract Shape createSeparateShape(Object sharedData);

    /**
     * Change the shape's type, yielding a new shape.
     *
     * Planned to be deprecated. To be replaced by {@link #setDynamicType(Object)}.
     *
     * @since 0.8 or earlier
     */
    public abstract Shape changeType(ObjectType newOps);

    /**
     * Reserve the primitive extension array field.
     *
     * @since 0.8 or earlier
     * @deprecated It is unnecessary to call this method, it has no effect and always returns this.
     */
    @Deprecated
    public abstract Shape reservePrimitiveExtensionArray();

    /**
     * Create a new {@link DynamicObject} instance with this shape.
     *
     * @throws UnsupportedOperationException if this layout does not support construction
     * @since 0.8 or earlier
     */
    public abstract DynamicObject newInstance();

    /**
     * Create a {@link DynamicObjectFactory} for creating instances of this shape.
     *
     * @throws UnsupportedOperationException if this layout does not support construction
     * @since 0.8 or earlier
     */
    public abstract DynamicObjectFactory createFactory();

    /**
     * Get mutex object shared by related shapes, i.e. shapes with a common root.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract Object getMutex();

    /**
     * Are these two shapes related, i.e. do they have the same root?
     *
     * @param other Shape to compare to
     * @return true if one shape is an upcast of the other, or the Shapes are equal
     * @since 0.8 or earlier
     * @deprecated no replacement
     */
    @Deprecated
    public abstract boolean isRelated(Shape other);

    /**
     * Try to merge two related shapes to a more general shape that has the same properties and can
     * store at least the values of both shapes.
     *
     * @return this, other, or a new shape that is compatible with both shapes
     * @since 0.8 or earlier
     */
    public abstract Shape tryMerge(Shape other);

    /**
     * Returns {@code true} if this shape is {@link Shape#makeSharedShape() shared}.
     *
     * @see DynamicObjectLibrary#isShared(DynamicObject)
     * @see DynamicObjectLibrary#makeShared(DynamicObject)
     * @see Shape.Builder#shared(boolean)
     * @since 0.18
     */
    public boolean isShared() {
        return false;
    }

    /**
     * Make a shared variant of this shape, to allow safe usage of this object between threads.
     * Shared shapes will not reuse storage locations for other fields. In combination with careful
     * synchronization on writes, this can prevent reading out-of-thin-air values.
     *
     * @return a cached and shared variant of this shape
     * @see #isShared()
     * @see DynamicObjectLibrary#makeShared(DynamicObject)
     * @since 0.18
     */
    public Shape makeSharedShape() {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if this shape has instance properties (i.e., stored in the object).
     *
     * @since 20.2.0
     */
    protected boolean hasInstanceProperties() {
        return true;
    }

    /**
     * Add a new constant property to the shape, yielding a new or cached shape.
     *
     * @param key the key of the property to add
     * @param value the constant value of the property to add
     * @param propertyFlags the property's flags
     * @return a new shape with the property
     * @throws IllegalArgumentException if the property already exists in the shape
     * @since 20.2.0
     */
    public Shape addConstantProperty(Object key, Object value, int propertyFlags) {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Gets a stable property assumption for the given property key. May be invalid. If a valid
     * assumption is returned, it may be used to assume this particular property is still absent or
     * present at the current storage location in objects of this shape. The assumption is
     * invalidated if a shape change is triggered because of a property with the given key was
     * added, removed, or changed, or a {@link DynamicObjectLibrary#resetShape resetShape}.
     *
     * <p>
     * Only applicable if {@linkplain Builder#propertyAssumptions(boolean) property assumptions} are
     * enabled for this shape, otherwise always returns an invalid assumption.
     *
     * @param key the property key of interest
     * @return an assumption that the property is stable or an invalid assumption
     * @see Shape.Builder#propertyAssumptions(boolean)
     * @since 20.2.0
     */
    public Assumption getPropertyAssumption(Object key) {
        return NeverValidAssumption.INSTANCE;
    }

    /**
     * Tests if all properties in the shape match the provided predicate. May not evaluate the
     * predicate on all properties if a predicate did not match. If the shape does not contain any
     * properties, returns {@code true} and does not evaluate the predicate.
     *
     * @return {@code true} if the all properties match the predicate, else {@code false}
     * @since 20.2.0
     */
    public boolean allPropertiesMatch(@SuppressWarnings("unused") Predicate<Property> predicate) {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException();
    }

    /**
     * Utility class to allocate locations in an object layout.
     *
     * Planned to be deprecated.
     *
     * @since 0.8 or earlier
     */
    public abstract static class Allocator {
        /**
         * @since 0.8 or earlier
         */
        protected Allocator() {
        }

        /** @since 0.8 or earlier */
        @Deprecated
        protected abstract Location locationForValue(Object value, boolean useFinal, boolean nonNull);

        /**
         * Create a new location compatible with the given initial value.
         *
         * Use {@link #locationForType(Class)} or {@link Shape#defineProperty(Object, Object, int)}
         * instead.
         *
         * @param value the initial value this location is going to be assigned
         * @since 0.8 or earlier
         */
        @Deprecated
        public final Location locationForValue(Object value) {
            return locationForValue(value, false, value != null);
        }

        /**
         * Create a new location compatible with the given initial value.
         *
         * @param value the initial value this location is going to be assigned
         * @param modifiers additional restrictions and semantics
         * @since 0.8 or earlier
         * @deprecated use {@link #locationForType(Class, EnumSet)} or
         *             {@link Shape#defineProperty(Object, Object, int)} instead
         */
        @Deprecated
        public final Location locationForValue(Object value, EnumSet<LocationModifier> modifiers) {
            assert value != null || !modifiers.contains(LocationModifier.NonNull);
            return locationForValue(value, modifiers.contains(LocationModifier.Final), modifiers.contains(LocationModifier.NonNull));
        }

        /** @since 0.8 or earlier */
        protected abstract Location locationForType(Class<?> type, boolean useFinal, boolean nonNull);

        /**
         * Create a new location for a fixed type. It can only be assigned to values of this type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         * @since 0.8 or earlier
         */
        public final Location locationForType(Class<?> type) {
            return locationForType(type, false, false);
        }

        /**
         * Create a new location for a fixed type.
         *
         * @param type the Java type this location must be compatible with (may be primitive)
         * @param modifiers additional restrictions and semantics
         * @since 0.8 or earlier
         */
        public final Location locationForType(Class<?> type, EnumSet<LocationModifier> modifiers) {
            return locationForType(type, modifiers.contains(LocationModifier.Final), modifiers.contains(LocationModifier.NonNull));
        }

        /**
         * Creates a new location from a constant value. The value is stored in the shape rather
         * than in the object.
         *
         * @since 0.8 or earlier
         */
        public abstract Location constantLocation(Object value);

        /**
         * Creates a new declared location with a default value. A declared location only assumes a
         * type after the first set (initialization).
         *
         * @since 0.8 or earlier
         */
        public abstract Location declaredLocation(Object value);

        /**
         * Reserves space for the given location, so that it will not be available to subsequently
         * allocated locations.
         *
         * @since 0.8 or earlier
         */
        public abstract Allocator addLocation(Location location);

        /**
         * Creates an copy of this allocator state.
         *
         * @since 0.8 or earlier
         */
        public abstract Allocator copy();
    }

    /**
     * Represents a predicate (boolean-valued function) of one argument.
     *
     * For Java 7 compatibility (equivalent to Predicate).
     *
     * @param <T> the type of the input to the predicate
     * @since 0.8 or earlier
     * @deprecated all methods that use this interface are deprecated; use
     *             {@link java.util.function.Predicate} instead.
     */
    @Deprecated
    public interface Pred<T> {
        /**
         * Evaluates this predicate on the given argument.
         *
         * @param t the input argument
         * @return {@code true} if the input argument matches the predicate, otherwise {@code false}
         * @since 0.8 or earlier
         */
        boolean test(T t);
    }
}
