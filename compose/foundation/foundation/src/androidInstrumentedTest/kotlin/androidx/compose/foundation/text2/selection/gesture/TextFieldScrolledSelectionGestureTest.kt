/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.text2.selection.gesture

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.FocusedWindowTest
import androidx.compose.foundation.text.Handle
import androidx.compose.foundation.text.TEST_FONT_FAMILY
import androidx.compose.foundation.text.selection.HandlePressedScope
import androidx.compose.foundation.text.selection.fetchTextLayoutResult
import androidx.compose.foundation.text.selection.gestures.util.longPress
import androidx.compose.foundation.text.selection.withHandlePressed
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.testutils.TestViewConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that TextField works as expected even when it is scrolled.
 *
 * Regression test for b/314385218.
 */
@ExperimentalFoundationApi
@MediumTest
@RunWith(AndroidJUnit4::class)
class TextFieldScrolledSelectionGestureTest : FocusedWindowTest {
    @get:Rule
    val rule = createComposeRule()

    private val fontFamily = TEST_FONT_FAMILY
    private val fontSize = 15.sp
    private val textStyle = TextStyle(fontFamily = fontFamily, fontSize = fontSize)
    private val density = Density(1f)
    private val pointerAreaTag = "testTag"

    private fun setContent(content: @Composable (tag: String) -> Unit) {
        rule.setTextFieldTestContent {
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalViewConfiguration provides TestViewConfiguration(
                    minimumTouchTargetSize = DpSize.Zero,
                    touchSlop = Float.MIN_VALUE,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .wrapContentSize()
                ) {
                    content(pointerAreaTag)
                }
            }
        }
    }

    private abstract class AbstractScope(
        val textFieldState: TextFieldState,
        val onTextField: SemanticsNodeInteraction,
    ) {
        /** Returns the offset needed to translate the amount scrolled. */
        abstract fun TextLayoutResult.translateScroll(): Offset

        fun characterBoxScrolled(offset: Int): Rect = onTextField.fetchTextLayoutResult().run {
            getBoundingBox(offset).translate(translateScroll())
        }

        fun positionForCharacterScrolled(offset: Int): Offset =
            characterBoxScrolled(offset).centerLeft

        fun HandlePressedScope.moveHandleToCharacter(characterOffset: Int) {
            val boundingBox = onTextField.fetchTextLayoutResult().getBoundingBox(characterOffset)
            val destinationPosition = when (fetchHandleInfo().handle) {
                Handle.SelectionStart -> boundingBox.bottomLeft
                Handle.SelectionEnd -> boundingBox.bottomRight
                Handle.Cursor -> fail("Unexpected handle ${Handle.Cursor}")
            }
            moveHandleTo(destinationPosition)
        }

        fun assertSelectionEquals(selectionRange: Pair<Int, Int>) {
            val (start, end) = selectionRange
            assertThat(textFieldState.text.selectionInChars).isEqualTo(TextRange(start, end))
        }
    }

    private inner class HorizontalScope(
        textFieldState: TextFieldState,
        onTextField: SemanticsNodeInteraction,
        val textFieldSize: IntSize,
    ) : AbstractScope(textFieldState, onTextField) {
        override fun TextLayoutResult.translateScroll(): Offset {
            val textLayoutSize = size
            assertThat(textFieldSize.height).isEqualTo(textLayoutSize.height)
            val translateX = textFieldSize.width - textLayoutSize.width
            return Offset(translateX.toFloat(), 0f)
        }
    }

    /** Create a horizontally scrollable text field that is scrolled all the way to the end. */
    private fun runHorizontalTest(block: HorizontalScope.() -> Unit) {
        val text = (0..9).joinToString(separator = " ") { "text$it" }
        var sizeNullable: MutableState<IntSize?>? = null
        lateinit var tfs: TextFieldState
        setContent { tag ->
            sizeNullable = remember { mutableStateOf(null) }
            tfs = rememberTextFieldState(text)
            BasicTextField2(
                state = tfs,
                textStyle = textStyle,
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier
                    .width(300.dp)
                    .testTag(tag = tag)
                    .onSizeChanged { sizeNullable!!.value = it }
            )
        }
        val onTextField = rule.onNodeWithTag(pointerAreaTag)
        onTextField.requestFocus()

        // scroll to the end
        onTextField.performTouchInput {
            repeat(4) {
                swipe(start = centerRight, end = centerLeft)
            }
        }

        assertThat(sizeNullable!!.value).isNotNull()
        HorizontalScope(tfs, onTextField, sizeNullable!!.value!!).block()
    }

    @Test
    fun whenHorizontalScroll_longPressGesture_selectAndDrag() = runHorizontalTest {
        // select "text8".
        onTextField.performTouchInput { longPress(positionForCharacterScrolled(50)) }
        assertSelectionEquals(48 to 53)

        // Backwards select through "text7" so that the selection is "text7 ".
        onTextField.performTouchInput { moveTo(positionForCharacterScrolled(46)) }
        assertSelectionEquals(42 to 53)

        onTextField.performTouchInput { up() }
        assertSelectionEquals(42 to 53)
    }

    @Test
    fun whenHorizontalScroll_handleGesture_drag() = runHorizontalTest {
        // select "text8".
        onTextField.performTouchInput { longPress(positionForCharacterScrolled(50)) }
        assertSelectionEquals(48 to 53)

        // Backwards select through "text7" so that the selection is "text7 ".
        rule.withHandlePressed(Handle.SelectionStart) {
            assertSelectionEquals(48 to 53)
            moveHandleToCharacter(45)
            assertSelectionEquals(42 to 53)
        }
        assertSelectionEquals(42 to 53)
    }

    /** Create a vertically scrollable text field that is scrolled all the way to the end. */
    private inner class VerticalScope(
        textFieldState: TextFieldState,
        onTextField: SemanticsNodeInteraction,
        val textFieldSize: IntSize,
    ) : AbstractScope(textFieldState, onTextField) {
        override fun TextLayoutResult.translateScroll(): Offset {
            val textLayoutSize = size
            assertThat(textFieldSize.width).isEqualTo(textLayoutSize.width)
            val translateY = textFieldSize.height - textLayoutSize.height
            return Offset(0f, translateY.toFloat())
        }
    }

    /**
     * Create a horizontally scrollable text field that is scrolled all the way to the end.
     */
    private fun runVerticalTest(block: VerticalScope.() -> Unit) {
        val text = (0..9).joinToString(separator = "\n") { "text$it" }
        var sizeNullable: MutableState<IntSize?>? = null
        lateinit var tfs: TextFieldState
        setContent { tag ->
            sizeNullable = remember { mutableStateOf(null) }
            tfs = rememberTextFieldState(text)
            BasicTextField2(
                state = tfs,
                textStyle = TextStyle(fontFamily = fontFamily, fontSize = fontSize),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                modifier = Modifier
                    .width(300.dp)
                    .testTag(tag = tag)
                    .onSizeChanged { sizeNullable!!.value = it }
            )
        }
        assertThat(sizeNullable).isNotNull()
        val onTextField = rule.onNodeWithTag(pointerAreaTag)
        onTextField.requestFocus()

        // scroll to the end
        onTextField.performTouchInput {
            repeat(4) {
                swipe(start = bottomCenter, end = topCenter)
            }
        }

        assertThat(sizeNullable!!.value).isNotNull()
        VerticalScope(tfs, onTextField, sizeNullable!!.value!!).block()
    }

    @Test
    fun whenVerticalScroll_longPressGesture_selectAndDrag() = runVerticalTest {
        // select "text8".
        onTextField.performTouchInput { longPress(positionForCharacterScrolled(50)) }
        assertSelectionEquals(48 to 53)

        // Backwards select through "text7" so that the selection is "text7 ".
        onTextField.performTouchInput { moveTo(positionForCharacterScrolled(46)) }
        assertSelectionEquals(42 to 53)

        onTextField.performTouchInput { up() }
        assertSelectionEquals(42 to 53)
    }

    @Test
    fun whenVerticalScroll_handleGesture_drag() = runVerticalTest {
        // select "text8".
        onTextField.performTouchInput { longPress(positionForCharacterScrolled(50)) }
        assertSelectionEquals(48 to 53)

        // Backwards select through "text7" so that the selection is "text7 ".
        rule.withHandlePressed(Handle.SelectionStart) {
            assertSelectionEquals(48 to 53)
            moveHandleToCharacter(45)
            assertSelectionEquals(42 to 53)
        }
        assertSelectionEquals(42 to 53)
    }
}