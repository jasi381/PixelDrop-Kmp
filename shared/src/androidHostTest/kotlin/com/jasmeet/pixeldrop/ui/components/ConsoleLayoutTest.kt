package com.jasmeet.pixeldrop.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleLayoutTest {
    private fun layout(w: Int, h: Int, p: FoldPosture = FoldPosture.Flat) =
        consoleLayoutFor(w.dp, h.dp, p)

    @Test fun `phone portrait stays portrait`() = assertEquals(ConsoleLayout.Portrait, layout(390, 844))

    @Test fun `near square fold inner stays portrait`() = assertEquals(ConsoleLayout.Portrait, layout(673, 841))

    @Test fun `fold inner turned landscape goes wide`() = assertEquals(ConsoleLayout.Wide, layout(841, 673))

    @Test fun `iphone duo inner goes wide`() = assertEquals(ConsoleLayout.Wide, layout(890, 626))

    @Test fun `ipad portrait stays portrait`() = assertEquals(ConsoleLayout.Portrait, layout(820, 1180))

    @Test fun `tabletop is clamshell at any size`() {
        assertEquals(ConsoleLayout.Clamshell, layout(412, 892, FoldPosture.Tabletop))
        assertEquals(ConsoleLayout.Clamshell, layout(890, 626, FoldPosture.Tabletop))
    }

    @Test fun `book is split at any size`() {
        assertEquals(ConsoleLayout.BookSplit, layout(890, 626, FoldPosture.Book))
        assertEquals(ConsoleLayout.BookSplit, layout(673, 841, FoldPosture.Book))
    }
}
