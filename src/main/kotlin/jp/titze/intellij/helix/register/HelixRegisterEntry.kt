// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.register

data class HelixRegisterEntry(
    val text: String,
    val isLinewise: Boolean = false,
    val pieces: List<String> = emptyList(),
)
