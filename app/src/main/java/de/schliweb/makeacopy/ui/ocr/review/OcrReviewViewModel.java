package de.schliweb.makeacopy.ui.ocr.review;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

import de.schliweb.makeacopy.ui.ocr.review.model.OcrDoc;
import de.schliweb.makeacopy.ui.ocr.review.model.OcrDocUtils;
import de.schliweb.makeacopy.ui.ocr.review.store.OcrJsonStore;
import lombok.Getter;
import lombok.Setter;

/**
 * A ViewModel class for managing the state of an OCR review screen. This class provides
 * functionality for undo/redo operations, document persistence, and live updates to the
 * document being edited.
 *
 * The ViewModel operates on an {@code OcrDoc} object, which represents the current state
 * of the OCR document, and exposes its state through LiveData. Changes to the document
 * can be saved to a file or loaded from a file.
 *
 * This class also tracks undo and redo operations using two stacks, limiting the depth
 * of the undo stack to a predefined maximum.
 */
public class OcrReviewViewModel extends ViewModel {
    private static final int MAX_UNDO = 50;

    private final MutableLiveData<OcrDoc> doc = new MutableLiveData<>(new OcrDoc());
    private final MutableLiveData<Boolean> canUndo = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> canRedo = new MutableLiveData<>(false);

    // Optional target scan id to resolve autosave path under filesDir/scans/<id>
    @Getter
    @Setter
    private String targetScanId;

    // Single lock to serialize access to on-disk JSON operations (save/load)
    private final Object ioLock = new Object();

    private final Deque<OcrDoc> undoStack = new ArrayDeque<>();
    private final Deque<OcrDoc> redoStack = new ArrayDeque<>();

    public LiveData<OcrDoc> getDoc() { return doc; }
    public LiveData<Boolean> getCanUndo() { return canUndo; }
    public LiveData<Boolean> getCanRedo() { return canRedo; }

    /**
     * Sets the document for review. If the provided document is null, a new document
     * is created and set as the current document. Updates related UI states for undo
     * and redo functionality.
     *
     * @param d the document to be set. If null, a new instance of {@code OcrDoc} is created.
     */
    public void setDoc(OcrDoc d) {
        if (d == null) d = new OcrDoc();
        doc.setValue(d);
        updateButtons();
    }

    /**
     * Creates a snapshot of the current document state for undo functionality.
     *
     * If the current document is not null, the method performs the following steps:
     * 1. Creates a deep copy of the current document and pushes it onto the undo stack.
     * 2. Ensures the undo stack does not exceed the maximum allowed size by removing the oldest entries if necessary.
     * 3. Clears the redo stack to maintain a consistent state.
     * 4. Updates the state of the undo and redo buttons by calling {@code updateButtons()}.
     *
     * No action is taken if the current document is null.
     */
    public void snapshot() {
        OcrDoc current = doc.getValue();
        if (current == null) return;
        // push deep copy to undo, clear redo
        OcrDoc copy = OcrDocUtils.deepCopy(current);
        undoStack.push(copy);
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
        updateButtons();
    }

    /**
     * Reverts the document state to the last snapshot stored in the undo stack.
     *
     * If the undo stack is not empty, this method performs the following steps:
     * 1. Pushes a deep copy of the current document to the redo stack, enabling future redo operations.
     * 2. Retrieves and removes the last document state from the undo stack and sets it as the current document.
     * 3. Updates the state of the undo and redo buttons to reflect the changes.
     *
     * If the undo stack is empty, no action is performed.
     */
    public void undo() {
        if (undoStack.isEmpty()) return;
        OcrDoc current = doc.getValue();
        if (current != null) {
            redoStack.push(OcrDocUtils.deepCopy(current));
        }
        OcrDoc prev = undoStack.pop();
        doc.setValue(prev);
        updateButtons();
    }

    /**
     * Reapplies the last undone change from the redo stack, restoring the document state
     * to what it was before the previous undo operation.
     *
     * If the redo stack is not empty, this method will:
     * 1. Push a deep copy of the current document onto the undo stack to preserve the current
     *    state for potential undo operations.
     * 2. Ensure the undo stack does not exceed the maximum allowed size, removing the oldest
     *    entries if necessary.
     * 3. Retrieve and remove the most recent document state from the redo stack.
     * 4. Set the retrieved state as the current document.
     * 5. Update the UI state of the undo and redo buttons to reflect the changes.
     *
     * If the redo stack is empty, no action is performed, and the method exits early.
     */
    public void redo() {
        if (redoStack.isEmpty()) return;
        OcrDoc current = doc.getValue();
        if (current != null) {
            undoStack.push(OcrDocUtils.deepCopy(current));
            while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        }
        OcrDoc next = redoStack.pop();
        doc.setValue(next);
        updateButtons();
    }

    /**
     * Updates the state of the undo and redo buttons based on the current state of the undo and redo stacks.
     *
     * This method checks whether the undo and redo stacks are empty and sets the corresponding
     * LiveData values, `canUndo` and `canRedo`, accordingly. If the undo stack is not empty,
     * the undo button is enabled by setting `canUndo` to true. Similarly, if the redo stack
     * is not empty, the redo button is enabled by setting `canRedo` to true.
     * If the stacks are empty, the buttons are disabled.
     *
     * This method is typically called after operations that modify the undo or redo stacks
     * to ensure the UI reflects the current state of the document's edit history.
     */
    private void updateButtons() {
        canUndo.setValue(!undoStack.isEmpty());
        canRedo.setValue(!redoStack.isEmpty());
    }

    /**
     * Loads an {@code OcrDoc} from the provided file into the ViewModel. This method ensures
     * thread-safe operations when loading the document and updating related states.
     * It updates the current document, clears the undo and redo stacks, and refreshes
     * the states of the undo and redo buttons.
     *
     * @param file The file from which the {@code OcrDoc} will be loaded. This file is expected
     *             to contain serialized OCR document data in a format compatible with
     *             {@code OcrJsonStore.load}.
     */
    public void load(File file) {
        synchronized (ioLock) {
            OcrDoc d2 = OcrJsonStore.load(file);
            // postValue in case called from background thread
            doc.postValue(d2);
            undoStack.clear();
            redoStack.clear();
            updateButtons();
        }
    }

    /**
     * Saves the current OCR document to the specified file in a thread-safe manner.
     * The method serializes the current document into a JSON format and writes it to the file.
     * If the current document is null, a new empty {@code OcrDoc} is created and saved.
     *
     * @param file The file where the current {@code OcrDoc} will be saved. This file is
     *             expected to be writable.
     * @return {@code true} if the document was successfully saved to the file,
     *         {@code false} otherwise.
     */
    public boolean save(File file) {
        synchronized (ioLock) {
            OcrDoc d = doc.getValue();
            if (d == null) d = new OcrDoc();
            return OcrJsonStore.save(file, d);
        }
    }

}
