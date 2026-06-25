package com.replica.replicaisland;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

/**
 * Created by lintonye on 15-01-15.
 * Modernized by removing com.jimulabs.mirrorsandbox dependency.
 */
public class MainBox {
    private Game mGame;
    private View mRootView;

    // 부모 클래스(MirrorSandboxBase)의 super(root) 호출 제거
    public MainBox(View root) {
        this.mRootView = root;
    }

    /**
     * 기존 $onLayoutDone 메서드에서 $를 제거하고 일반 메서드로 변경했습니다.
     * 다른 클래스에서 호출하고 있다면 이 메서드가 실행됩니다.
     */
    public void onLayoutDone(View rootView) {
        mGame = new Game();
        GLSurfaceView surfaceView = (GLSurfaceView) rootView.findViewById(R.id.glsurfaceview);
        mGame.setSurfaceView(surfaceView);
        Context context = surfaceView.getContext();

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int defaultWidth = 480;
        int defaultHeight = 320;
        if (dm.widthPixels != defaultWidth) {
            float ratio = ((float) dm.widthPixels) / dm.heightPixels;
            defaultWidth = (int) (defaultHeight * ratio);
        }

        mGame.bootstrap(context, dm.widthPixels, dm.heightPixels, defaultWidth, defaultHeight, 1);
        surfaceView.setRenderer(mGame.getRenderer());

        LevelTree.loadLevelTree(R.xml.level_tree, context);
        LevelTree.loadAllDialog(context);
        mGame.setPendingLevel(LevelTree.get(3, 0));

        mGame.onStartCallback = new Runnable() {
            @Override
            public void run() {
                GameObject player = BaseObject.sSystemRegistry.gameObjectManager.getPlayer();
                Log.d("MainBox", "player=" + player);
                player.setVelocity(new Vector2(0, 1500));
                player.setCurrentAction(GameObject.ActionType.DEATH);
            }
        };
    }

    /**
     * 기존 $onDestroy 메서드에서 $를 제거하고 일반 메서드로 변경했습니다.
     */
    public void onDestroy() {
        if (mGame != null) {
            mGame.stop();
        }
    }
}