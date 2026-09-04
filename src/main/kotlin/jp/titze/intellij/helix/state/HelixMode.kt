package jp.titze.intellij.helix.state

enum class HelixMode(val displayName: String, val shortCode: String) {
    NORMAL("Normal", "NOR"),
    INSERT("Insert", "INS"),
    SELECT("Select", "SEL")
}
