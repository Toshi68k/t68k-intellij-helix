package jp.titze.intellij.helix.ui

data class WhichKeyItem(val key: String, val label: String, val description: String = "")

object HelixWhichKeyMenus {

    val spaceItems = listOf(
        WhichKeyItem("b", "Buffer / Tab picker", "RecentFiles"),
        WhichKeyItem("f", "File picker", "GotoFile"),
        WhichKeyItem("/", "Global search", "FindInPath"),
        WhichKeyItem("s", "Symbol picker", "Structure"),
        WhichKeyItem("S", "Workspace symbol picker", "GotoSymbol"),
        WhichKeyItem("d", "Diagnostics picker", "ShowError"),
        WhichKeyItem("D", "Workspace diagnostics", "Problems"),
        WhichKeyItem("j", "Jumplist picker", "Jumplist"),
        WhichKeyItem("a", "Code action", "Intentions"),
        WhichKeyItem("r", "Rename symbol", "RenameElement"),
        WhichKeyItem("w", "Save", "SaveAll"),
        WhichKeyItem("y", "Yank main selection", "Clipboard"),
        WhichKeyItem("p", "Paste clipboard after"),
        WhichKeyItem("P", "Paste clipboard before"),
        WhichKeyItem("R", "Replace with clipboard"),
        WhichKeyItem("k", "Hover / Documentation", "QuickJavaDoc"),
        WhichKeyItem("?", "Command palette", "GotoAction"),
    )

    val gotoItems = listOf(
        WhichKeyItem("d", "Goto definition", "GotoDeclaration"),
        WhichKeyItem("y", "Goto type definition", "GotoTypeDeclaration"),
        WhichKeyItem("r", "Goto reference", "FindUsages"),
        WhichKeyItem("h", "Goto line start"),
        WhichKeyItem("l", "Goto line end"),
        WhichKeyItem("s", "Goto first non-whitespace"),
        WhichKeyItem("g", "Goto line / file start", "line <count>"),
        WhichKeyItem("e", "Goto file end"),
    )

    val matchItems = listOf(
        WhichKeyItem("s", "Surround add", "ms<char>"),
        WhichKeyItem("r", "Surround replace", "mr<from><to>"),
        WhichKeyItem("d", "Surround delete", "md<char>"),
        WhichKeyItem("m", "Match bracket", "Jump to matching bracket"),
        WhichKeyItem("a", "Select around textobject", "ma<obj>"),
        WhichKeyItem("i", "Select inside textobject", "mi<obj>"),
    )

    val bracketOpenItems = listOf(
        WhichKeyItem("d", "Previous diagnostic", "GotoPreviousError"),
        WhichKeyItem("D", "First diagnostic", "First error"),
        WhichKeyItem("f", "Previous function", "MethodUp"),
        WhichKeyItem("t", "Previous class", "Previous class/type"),
        WhichKeyItem("a", "Previous parameter", "Previous argument"),
        WhichKeyItem("c", "Previous comment", "Previous comment"),
        WhichKeyItem("T", "Previous test", "Previous test"),
        WhichKeyItem("p", "Previous paragraph", "Previous paragraph"),
        WhichKeyItem("g", "Previous change", "VcsShowPrevChangeMarker"),
        WhichKeyItem("G", "First change", "First change"),
        WhichKeyItem("Space", "Add newline above", "Blank line above"),
        WhichKeyItem("b", "Previous buffer / tab", "PreviousTab"),
    )

    val bracketCloseItems = listOf(
        WhichKeyItem("d", "Next diagnostic", "GotoNextError"),
        WhichKeyItem("D", "Last diagnostic", "Last error"),
        WhichKeyItem("f", "Next function", "MethodDown"),
        WhichKeyItem("t", "Next class", "Next class/type"),
        WhichKeyItem("a", "Next parameter", "Next argument"),
        WhichKeyItem("c", "Next comment", "Next comment"),
        WhichKeyItem("T", "Next test", "Next test"),
        WhichKeyItem("p", "Next paragraph", "Next paragraph"),
        WhichKeyItem("g", "Next change", "VcsShowNextChangeMarker"),
        WhichKeyItem("G", "Last change", "Last change"),
        WhichKeyItem("Space", "Add newline below", "Blank line below"),
        WhichKeyItem("b", "Next buffer / tab", "NextTab"),
    )

    val viewItems = listOf(
        WhichKeyItem("c", "Center view", "align_view_center"),
        WhichKeyItem("t", "Align view top", "align_view_top"),
        WhichKeyItem("b", "Align view bottom", "align_view_bottom"),
        WhichKeyItem("m", "Align view middle (horiz)", "align_view_middle"),
        WhichKeyItem("j", "Scroll view down", "scroll_down"),
        WhichKeyItem("k", "Scroll view up", "scroll_up"),
        WhichKeyItem("d", "Scroll half page down", "half_page_down"),
        WhichKeyItem("u", "Scroll half page up", "half_page_up"),
        WhichKeyItem("f", "Scroll page down", "page_down"),
        WhichKeyItem("F", "Scroll page up", "page_up"),
        WhichKeyItem("z", "Center view", "align_view_center"),
    )

    fun getMenu(prefix: String): Pair<String, List<WhichKeyItem>>? = when (prefix) {
        " " -> "SPACE MENU" to spaceItems
        "g" -> "GOTO MENU" to gotoItems
        "m" -> "MATCH MENU" to matchItems
        "[" -> "JUMP BACK MENU" to bracketOpenItems
        "]" -> "JUMP FORWARD MENU" to bracketCloseItems
        "z" -> "VIEW MENU" to viewItems
        "Z" -> "STICKY VIEW MENU" to viewItems
        else -> null
    }
}
