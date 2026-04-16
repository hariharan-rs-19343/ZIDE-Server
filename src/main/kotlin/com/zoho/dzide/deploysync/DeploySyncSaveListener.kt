package com.zoho.dzide.deploysync

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager

class DeploySyncSaveListener : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val filePath = virtualFile.path

        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val basePath = project.basePath ?: continue
            if (filePath.startsWith(basePath)) {
                ResourceSyncManager.getInstance(project).handleDocumentSave(filePath)
                return
            }
        }
    }
}
