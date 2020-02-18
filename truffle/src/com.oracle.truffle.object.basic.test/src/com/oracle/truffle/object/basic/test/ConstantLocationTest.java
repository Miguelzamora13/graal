/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.object.basic.DefaultLayoutFactory;

public class ConstantLocationTest {
    private static final DynamicObjectLibrary LIBRARY = DynamicObjectLibrary.getUncached();

    final Layout layout = new DefaultLayoutFactory().createLayout(Layout.newLayout());
    final Shape rootShape = layout.createShape(new ObjectType());
    final Object value = new Object();
    final Location constantLocation = rootShape.allocator().constantLocation(value);
    final Shape shapeWithConstant = rootShape.addProperty(Property.create("constant", constantLocation, 0));

    @Test
    public void testConstantLocation() {
        DynamicObject object = shapeWithConstant.newInstance();
        Assert.assertSame(value, LIBRARY.getOrDefault(object, "constant", null));

        LIBRARY.putIfPresent(object, "constant", value);
        Assert.assertSame(shapeWithConstant, object.getShape());

        Property property = object.getShape().getProperty("constant");
        Assert.assertEquals(true, property.getLocation().canStore(value));
        Assert.assertEquals(true, property.getLocation().canSet(value));
        try {
            property.set(object, value, shapeWithConstant);
        } catch (IncompatibleLocationException | FinalLocationException e) {
            Assert.fail(e.getMessage());
        }

        Object newValue = new Object();
        Assert.assertEquals(false, property.getLocation().canStore(newValue));
        Assert.assertEquals(false, property.getLocation().canSet(newValue));
        try {
            property.set(object, newValue, shapeWithConstant);
            Assert.fail();
        } catch (FinalLocationException | IncompatibleLocationException e) {
            Assert.assertTrue(e instanceof FinalLocationException);
        }

        Assert.assertSame(value, LIBRARY.getOrDefault(object, "constant", null));
    }

    @Test
    public void testMigrateConstantLocation() {
        DynamicObject object = shapeWithConstant.newInstance();
        Assert.assertSame(shapeWithConstant, object.getShape());
        Assert.assertSame(value, LIBRARY.getOrDefault(object, "constant", null));

        Object newValue = new Object();
        LIBRARY.putIfPresent(object, "constant", newValue);
        Assert.assertNotSame(shapeWithConstant, object.getShape());
        Assert.assertSame(newValue, LIBRARY.getOrDefault(object, "constant", null));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testAddConstantLocation() {
        Property property = shapeWithConstant.getProperty("constant");

        DynamicObject object = rootShape.newInstance();
        property.setSafe(object, value, rootShape, shapeWithConstant);
        Assert.assertSame(shapeWithConstant, object.getShape());
        Assert.assertSame(value, LIBRARY.getOrDefault(object, "constant", null));

        DynamicObject object2 = rootShape.newInstance();
        Object newValue = new Object();
        Assert.assertEquals(false, property.getLocation().canStore(newValue));
        Assert.assertEquals(false, property.getLocation().canSet(newValue));
        try {
            property.set(object2, newValue, rootShape, shapeWithConstant);
            Assert.fail();
        } catch (IncompatibleLocationException e) {
            // Expected
        }
        Assert.assertSame(rootShape, object2.getShape());
        Assert.assertEquals(false, LIBRARY.containsKey(object2, "constant"));
    }

}
