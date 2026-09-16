package jp.titze.intellij.helix.register

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import jp.titze.intellij.helix.action.HelixSearchActions
import jp.titze.intellij.helix.editor.HelixInsertTracker
import jp.titze.intellij.helix.settings.HelixSettings
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object HelixRegisterManager {

    private const val DELETE_REGISTER_COUNT = 9

    var defaultRegister: HelixRegisterEntry? = null
    var yankRegister0: HelixRegisterEntry? = null
    private val deleteRegisters = arrayOfNulls<HelixRegisterEntry>(DELETE_REGISTER_COUNT)
    private val namedRegisters = mutableMapOf<Char, HelixRegisterEntry>()

    fun get(register: Char?, editor: Editor? = null): HelixRegisterEntry? {
        val reg = register ?: '"'
        return when (reg) {
            '_' -> null

            '+', '*' -> getFromClipboard()

            '"' -> defaultRegister ?: if (HelixSettings.instance.syncClipboardWithDefaultRegister) {
                getFromClipboard()
            } else {
                null
            }

            '0' -> yankRegister0

            in '1'..'9' -> deleteRegisters[reg - '1']

            in 'a'..'z', in 'A'..'Z' -> namedRegisters[reg.lowercaseChar()]

            '/' -> HelixSearchActions.lastSearchPattern?.let { HelixRegisterEntry(text = it) }

            '%' -> getBufferName(editor)?.let { HelixRegisterEntry(text = it) }

            '#' -> getSelectionIndexEntry(editor)

            '.' -> HelixInsertTracker.lastInsertedText?.let { HelixRegisterEntry(text = it) }

            else -> null
        }
    }

    fun set(register: Char, entry: HelixRegisterEntry) {
        when (register) {
            '_', '#', '.', '%', '/' -> { /* Discard silently */ }

            '+', '*' -> setClipboard(entry.text)

            '"' -> {
                defaultRegister = entry
                if (HelixSettings.instance.syncClipboardWithDefaultRegister) {
                    setClipboard(entry.text)
                }
            }

            '0' -> yankRegister0 = entry

            in '1'..'9' -> deleteRegisters[register - '1'] = entry

            in 'a'..'z' -> namedRegisters[register] = entry

            in 'A'..'Z' -> {
                val key = register.lowercaseChar()
                val existing = namedRegisters[key]
                val combinedText = if (existing != null) "${existing.text}\n${entry.text}" else entry.text
                namedRegisters[key] = HelixRegisterEntry(
                    text = combinedText,
                    isLinewise = entry.isLinewise || (existing?.isLinewise == true),
                )
            }

            '/' -> HelixSearchActions.lastSearchPattern = entry.text

            else -> { /* Ignore unknown registers */ }
        }
    }

    fun recordYank(text: String, isLinewise: Boolean, pieces: List<String>, register: Char? = null) {
        if (register == '#') return
        val entry = HelixRegisterEntry(text, isLinewise, pieces)
        if (register == null || register == '"') {
            defaultRegister = entry
            yankRegister0 = entry
            if (HelixSettings.instance.syncClipboardWithDefaultRegister) {
                setClipboard(text)
            }
        } else {
            set(register, entry)
        }
    }

    fun recordDelete(text: String, isLinewise: Boolean, pieces: List<String>, register: Char? = null) {
        if (register == '_' || register == '#') {
            // Black hole and selection index registers: NEVER write to clipboard or registers
            return
        }
        val entry = HelixRegisterEntry(text, isLinewise, pieces)
        if (register == null || register == '"') {
            defaultRegister = entry
            shiftDeleteRegisters(entry)
            if (HelixSettings.instance.syncClipboardWithDefaultRegister) {
                setClipboard(text)
            }
        } else {
            set(register, entry)
        }
    }

    private fun shiftDeleteRegisters(entry: HelixRegisterEntry) {
        for (i in (DELETE_REGISTER_COUNT - 1) downTo 1) {
            deleteRegisters[i] = deleteRegisters[i - 1]
        }
        deleteRegisters[0] = entry
    }

    fun getFromClipboard(): HelixRegisterEntry? {
        val transferable = CopyPasteManager.getInstance().contents ?: return null
        return try {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                val text = transferable.getTransferData(DataFlavor.stringFlavor) as? String ?: return null
                HelixRegisterEntry(text = text, isLinewise = text.endsWith("\n"))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun setClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private fun getBufferName(editor: Editor?): String? {
        val virtualFile = editor?.virtualFile ?: return null
        return virtualFile.name
    }

    private fun getSelectionIndexEntry(editor: Editor?): HelixRegisterEntry {
        val count = editor?.caretModel?.caretCount ?: 1
        val pieces = (0 until count).map { it.toString() }
        return HelixRegisterEntry(
            text = pieces.joinToString("\n"),
            isLinewise = false,
            pieces = pieces,
        )
    }

    fun getAllRegisters(editor: Editor? = null): List<HelixRegisterItem> {
        val list = mutableListOf<HelixRegisterItem>()
        list.add(HelixRegisterItem('"', "Default", get('"', editor)))
        list.add(HelixRegisterItem('0', "Last Yank", get('0', editor)))

        for (i in 0 until DELETE_REGISTER_COUNT) {
            val entry = deleteRegisters[i]
            if (entry != null) {
                list.add(HelixRegisterItem(('1'.code + i).toChar(), "Delete ${i + 1}", entry))
            }
        }

        for ((char, entry) in namedRegisters.toSortedMap()) {
            list.add(HelixRegisterItem(char, "Named '$char'", entry))
        }

        list.add(HelixRegisterItem('+', "Clipboard", getFromClipboard()))
        HelixSearchActions.lastSearchPattern?.let {
            list.add(HelixRegisterItem('/', "Search Pattern", HelixRegisterEntry(text = it)))
        }
        HelixInsertTracker.lastInsertedText?.let {
            list.add(HelixRegisterItem('.', "Last Insert", HelixRegisterEntry(text = it)))
        }
        getBufferName(editor)?.let {
            list.add(HelixRegisterItem('%', "Current Buffer", HelixRegisterEntry(text = it)))
        }
        list.add(HelixRegisterItem('#', "Selection Index", get('#', editor)))
        list.add(HelixRegisterItem('_', "Black Hole", null))
        return list
    }

    fun clear() {
        defaultRegister = null
        yankRegister0 = null
        for (i in deleteRegisters.indices) {
            deleteRegisters[i] = null
        }
        namedRegisters.clear()
    }
}
