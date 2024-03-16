package com.github.rebane621.ruffleapk;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MyWebView extends WebView {

    boolean blockKeybaord = false;

    public MyWebView(@NonNull Context context) {
        super(context);
    }

    public MyWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setBlockKeybaord(boolean block) {
        blockKeybaord = block;
        if (block) {
            cancelPendingInputEvents();
        }
        setFocusable(!block);
        setFocusableInTouchMode(!block);
        setDescendantFocusability(block?FOCUS_BLOCK_DESCENDANTS:FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return super.onCheckIsTextEditor() && !blockKeybaord;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (blockKeybaord) {
            outAttrs.fieldId = 0;
            outAttrs.inputType = InputType.TYPE_NULL;
            outAttrs.actionId = 0;
            outAttrs.imeOptions = 0;
            outAttrs.fieldName = null;
            outAttrs.actionLabel = null;
            outAttrs.packageName = null;
            return null;
        }
        return super.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean checkInputConnectionProxy(View view) {
        if (blockKeybaord) return false; //we hijack this now
        return super.checkInputConnectionProxy(view);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused && blockKeybaord) clearFocus();
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        if (blockKeybaord) return false;
        return super.requestFocus(direction, previouslyFocusedRect);
    }
}
