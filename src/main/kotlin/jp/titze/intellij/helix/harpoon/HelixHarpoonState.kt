// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.harpoon

class HarpoonSlotState {
    var slotIndex: Int = 0
    var fileUrl: String = ""
    var line: Int = 1
    var column: Int = 1
    var previewText: String = ""
}

class HelixHarpoonState {
    var slots: MutableList<HarpoonSlotState> = ArrayList()
}
