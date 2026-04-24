package org.open.file.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.open.file.snapshot.store.domain.DirectoryNode
import org.open.file.snapshot.store.domain.FileNode
import org.open.file.snapshot.store.domain.SnapshotNode
import org.open.file.ui.theme.AppColors

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / 1048576.0)} MB"
}

/**
 * Pick an emoji glyph that hints at a file's type from its name. Extension
 * match comes first (the common case); a short tail of filename-based
 * checks picks up things like `Dockerfile`, `.env`, `LICENSE` where
 * extension lookup wouldn't help.
 *
 * Anything we don't recognise falls through to the plain document emoji —
 * users still get a consistent visual rhythm in the tree, just without
 * the type hint.
 */
fun fileEmojiFor(name: String): String {
    val lower = name.lowercase()
    // Filename-first overrides so `Dockerfile` beats a missing extension
    // and `.env.local` beats the ".local" ext.
    when {
        lower == "dockerfile" || lower.startsWith("dockerfile.") -> return "🐳"
        lower == "makefile" -> return "🛠️"
        lower.startsWith(".env") -> return "🔑"
        lower == "license" || lower.startsWith("license.") -> return "📜"
        lower == "readme" || lower.startsWith("readme.") -> return "📘"
        lower == ".gitignore" || lower == ".gitattributes" -> return "🌿"
    }
    val ext = lower.substringAfterLast('.', "")
    return when (ext) {
        // Docs / text
        "txt", "md", "markdown", "rst", "rtf" -> "📄"
        "pdf" -> "📕"
        "doc", "docx", "odt" -> "📝"
        "xls", "xlsx", "ods", "csv", "tsv" -> "📊"
        "ppt", "pptx", "odp" -> "📽️"
        // Code
        "kt", "kts" -> "🟣"
        "java", "jar" -> "☕"
        "py", "pyc", "pyo" -> "🐍"
        "rb" -> "💎"
        "go" -> "🐹"
        "rs" -> "🦀"
        "js", "mjs", "cjs" -> "🟨"
        "ts" -> "🔷"
        "tsx", "jsx" -> "⚛️"
        "html", "htm" -> "🌐"
        "css", "scss", "sass", "less" -> "🎨"
        "json", "jsonl" -> "🔣"
        "xml", "yaml", "yml", "toml", "ini", "conf" -> "⚙️"
        "c", "h" -> "🇨"
        "cpp", "cc", "cxx", "hpp" -> "➕"
        "cs" -> "🎯"
        "swift" -> "🕊️"
        "php" -> "🐘"
        "sh", "bash", "zsh", "fish" -> "🐚"
        "sql", "db", "sqlite" -> "🗄️"
        "lua" -> "🌙"
        "dart" -> "🎯"
        // Images
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff", "ico", "heic" -> "🖼️"
        "svg" -> "✒️"
        // Audio
        "mp3", "wav", "flac", "ogg", "m4a", "aac", "opus" -> "🎵"
        // Video
        "mp4", "mov", "avi", "mkv", "webm", "m4v", "wmv", "flv" -> "🎬"
        // Archives / disk images
        "zip", "tar", "gz", "bz2", "7z", "rar", "xz", "tgz" -> "📦"
        "iso", "dmg", "img", "vhd", "vmdk" -> "💿"
        // Executables / binaries
        "exe", "msi", "app", "apk", "ipa" -> "🧰"
        "dll", "so", "dylib" -> "🔧"
        "bin", "class", "o", "obj" -> "🔩"
        // Fonts
        "ttf", "otf", "woff", "woff2" -> "🔤"
        // Build / package
        "gradle", "pom", "cmake", "mk", "bazel", "bzl" -> "🛠️"
        "lock" -> "🔒"
        else -> "📄"
    }
}

@Composable
fun FileTreeView(
    node: SnapshotNode,
    depth: Int = 0,
    initialExpanded: Boolean = true,
) {
    when (node) {
        is FileNode -> FileNodeRow(node, depth)
        is DirectoryNode -> DirectoryNodeRow(node, depth, initialExpanded)
    }
}

@Composable
private fun FileNodeRow(node: FileNode, depth: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (12 + depth * 18).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
    ) {
        Spacer(Modifier.width(12.dp)) // Align with chevron space
        // Emoji chosen by extension — see [fileEmojiFor]. Sized to sit
        // roughly flush with the monospace text to its right so the row
        // doesn't wobble vertically compared to directory rows that use
        // the folder Icon.
        Text(
            fileEmojiFor(node.name),
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            node.name,
            // Pulled from AppColors so the tree re-colours live when the
            // user changes theme — the previous hardcoded 0xFFCBD5E1
            // stayed the same slate across every palette.
            style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = AppColors.textSecondary),
            modifier = Modifier.weight(1f)
        )
        Text(
            formatBytes(node.size),
            style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AppColors.textDim)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            node.hash.take(8),
            style = TextStyle(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = AppColors.textFaint)
        )
    }
}

@Composable
private fun DirectoryNodeRow(node: DirectoryNode, depth: Int, initialExpanded: Boolean) {
    var expanded by remember { mutableStateOf(initialExpanded && depth < 2) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = (12 + depth * 18).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = AppColors.accentLight,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.Folder, contentDescription = null, tint = AppColors.accentLight, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                node.name,
                style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = AppColors.accentLight),
                modifier = Modifier.weight(1f)
            )
            Text(
                node.hash.take(8),
                style = TextStyle(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = AppColors.textFaint)
            )
        }

        if (expanded) {
            node.children.forEach { child ->
                FileTreeView(node = child, depth = depth + 1, initialExpanded = depth < 1)
            }
        }
    }
}

/**
 * Wraps the tree in a styled container matching the dark UI.
 */
@Composable
fun FileTreePanel(node: SnapshotNode) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.015f))
            .padding(vertical = 8.dp)
            .heightIn(max = 300.dp)
    ) {
        FileTreeView(node)
    }
}
