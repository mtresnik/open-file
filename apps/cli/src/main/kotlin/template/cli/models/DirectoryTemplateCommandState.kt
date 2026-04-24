package template.cli.models

import org.open.file.cli.models.CommandState
import org.open.file.template.models.directory.DirectoryTemplateData


class DirectoryTemplateCommandState(success: Boolean, data: DirectoryTemplateData) : CommandState<DirectoryTemplateData>(success, data)
