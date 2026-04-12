package org.open.file.snapshot.models

import java.io.File

data class FileRef(val file: File,
                   val children: List<FileRef> = emptyList())