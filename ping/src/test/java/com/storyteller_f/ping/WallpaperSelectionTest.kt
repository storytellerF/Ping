package com.storyteller_f.ping

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.*
import org.junit.Test

class WallpaperSelectionTest {
    @Test fun appliedWallpaperIgnoresAnotherModelsPreview() {
        val preferences = preferencesOf(selected to "applied.model3.json", preview to "candidate.model3.json")
        assertEquals("applied.model3.json", preferences.selectedWallPaper(false))
    }

    @Test fun previewEngineUsesCandidateModel() {
        val preferences = preferencesOf(selected to "applied.model3.json", preview to "candidate.model3.json")
        assertEquals("candidate.model3.json", preferences.selectedWallPaper(true))
    }

    @Test fun clearedPreviewFallsBackToAppliedModel() {
        val preferences = preferencesOf(selected to "applied.model3.json")
        assertEquals("applied.model3.json", preferences.selectedWallPaper(true))
    }

    @Test fun unsetModelDoesNotProduceAnEmptyPath() {
        assertNull(preferencesOf(selected to "", preview to "").selectedWallPaper(true))
    }
}
