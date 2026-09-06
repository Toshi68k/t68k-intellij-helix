package jp.titze.intellij.helix.state

enum class HelixMode(val displayName: String, val shortCode: String, val isInsertable: Boolean = false) {
    NORMAL("Normal", "NOR", isInsertable = false),
    INSERT("Insert", "INS", isInsertable = true),
    SELECT("Select", "SEL", isInsertable = false),
}
