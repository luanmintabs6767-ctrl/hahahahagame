package com.replica.replicaisland;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.MotionEvent;

public class MultiTouchFilter extends SingleTouchFilter {
	private boolean mCheckedForMultitouch = false;
	private boolean mSupportsMultitouch = false;
	
    @Override
    public void updateTouch(MotionEvent event) {
        ContextParameters params = sSystemRegistry.contextParameters;
        final int action = event.getAction();
        final int actualEvent = action & MotionEvent.ACTION_MASK;
        final int pointerCount = event.getPointerCount();
        
        if (actualEvent == MotionEvent.ACTION_UP || 
            actualEvent == MotionEvent.ACTION_CANCEL) {
            for (int x = 0; x < pointerCount; x++) {
                BaseObject.sSystemRegistry.inputSystem.touchUp(event.getPointerId(x), 
                        event.getX(x) * (1.0f / params.viewScaleX), 
                        event.getY(x) * (1.0f / params.viewScaleY));
            }
        } else if (actualEvent == MotionEvent.ACTION_POINTER_UP) {
            int index = (action & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
            BaseObject.sSystemRegistry.inputSystem.touchUp(event.getPointerId(index), 
                    event.getX(index) * (1.0f / params.viewScaleX), 
                    event.getY(index) * (1.0f / params.viewScaleY));
        } else {
            for (int x = 0; x < pointerCount; x++) {
                BaseObject.sSystemRegistry.inputSystem.touchDown(event.getPointerId(x), 
                        event.getX(x) * (1.0f / params.viewScaleX),
                        event.getY(x) * (1.0f / params.viewScaleY));
            }
        }
    }
    
    @Override
    public boolean supportsMultitouch(Context context) {
    	if (!mCheckedForMultitouch) {
    		PackageManager packageManager = context.getPackageManager();
    		mSupportsMultitouch = packageManager.hasSystemFeature("android.hardware.touchscreen.multitouch");
    		mCheckedForMultitouch = true;
    	}
    	
    	return mSupportsMultitouch;
    }
}
