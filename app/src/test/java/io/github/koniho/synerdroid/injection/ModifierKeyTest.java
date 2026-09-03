package io.github.koniho.synerdroid.injection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ModifierKeyTest {
    @Test public void recognizesX11AndPortableModifiers() {
        assertTrue(Injection.isModifierKey(0xffe1));
        assertTrue(Injection.isModifierKey(0xffe3));
        assertTrue(Injection.isModifierKey(0xefe1));
        assertTrue(Injection.isModifierKey(0xefe9));
        assertFalse(Injection.isModifierKey('a'));
        assertFalse(Injection.isModifierKey(0xef08));
    }
}
