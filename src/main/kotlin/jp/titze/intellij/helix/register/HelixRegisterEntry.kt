package jp.titze.intellij.helix.register

data class HelixRegisterEntry(
    val text: String,
    val isLinewise: Boolean = false,
    val pieces: List<String> = emptyList(),
)
