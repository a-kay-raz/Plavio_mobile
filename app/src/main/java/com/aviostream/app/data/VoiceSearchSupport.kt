package com.aviostream.app.data

/**
 * Voice search — pure seams. The feature is built so a voice failure
 * can NEVER reach the search flow:
 *
 *  - Recognition is the SYSTEM's (RecognizerIntent) — no audio pipeline,
 *    no models, no server in this app; the recognizer app does the
 *    listening under its own UI.
 *  - The mic renders when a recognition activity actually resolves on
 *    the device. The app holds NO RECORD_AUDIO permission and asks for
 *    none (the system's recognizer listens under its own permission);
 *    the [PermissionState] / [micVisible] seams below are retained as
 *    the tested pure decision core, with the button now treating
 *    recognition resolution as the only gate. When the mic doesn't
 *    render, the search bar is exactly today's bar.
 *  - The hand-off is one pure function ([firstResult]): the recognised
 *    text is written into the SAME query state typed text uses, and
 *    from there the identical debounce/settle/warm pipeline runs.
 *    Cancel, error, and no-match all produce null here, and null means
 *    "leave the query untouched".
 */
public object VoiceSearchSupport {

    /** Where the RECORD_AUDIO permission stands, from the UI's view. */
    public enum class PermissionState {
        /** Never asked (or status unknown): show the mic; tapping asks. */
        UNKNOWN,

        /** Granted: show the mic; tapping launches the recognizer. */
        GRANTED,

        /** Asked and refused, or granted then revoked: mic disappears. */
        DENIED,
    }

    /**
     * The availability gate. Both conditions are required; either one
     * failing hides the mic and leaves typed search completely alone.
     */
    public fun micVisible(recognitionResolves: Boolean, permission: PermissionState): Boolean =
        recognitionResolves && permission != PermissionState.DENIED

    /** Derive the UI permission state from the two facts the UI knows. */
    public fun permissionState(granted: Boolean, askedBefore: Boolean): PermissionState = when {
        granted -> PermissionState.GRANTED
        askedBefore -> PermissionState.DENIED
        else -> PermissionState.UNKNOWN
    }

    /**
     * The result hand-off: the first non-blank recognition candidate,
     * trimmed — or null when there is nothing usable (no match, empty
     * result, recognizer error surfaces as no data at the seam). Null
     * ALWAYS means the caller leaves the current query untouched.
     */
    public fun firstResult(results: List<String>?): String? =
        results?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }
}
