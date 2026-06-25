/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.replica.replicaisland;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Manages information about the current level, including setup, deserialization, and tear-down.
 */
public class LevelSystem extends BaseObject {
    private static final String TAG = "LevelSystem";

    public int mWidthInTiles;
    public int mHeightInTiles;
    public int mTileWidth;
    public int mTileHeight;
    public GameObject mBackgroundObject;
    public ObjectManager mRoot;
    private TiledWorld mSpawnLocations;
    private GameFlowEvent mGameFlowEvent;
    private int mAttempts;
    private LevelTree.Level mCurrentLevel;

    public LevelSystem() {
        super();
        mGameFlowEvent = new GameFlowEvent();
        reset();
    }

    @Override
    public void reset() {
        if (mBackgroundObject != null && mRoot != null) {
            mBackgroundObject.removeAll();
            mBackgroundObject.commitUpdates();
            mRoot.remove(mBackgroundObject);
            mBackgroundObject = null;
            mRoot = null;
        }
        mSpawnLocations = null;
        mAttempts = 0;
        mCurrentLevel = null;
    }

    public float getLevelWidth() {
        return mWidthInTiles * mTileWidth;
    }

    public float getLevelHeight() {
        return mHeightInTiles * mTileHeight;
    }

    public void sendRestartEvent() {
        mGameFlowEvent.post(GameFlowEvent.EVENT_RESTART_LEVEL, 0,
                sSystemRegistry.contextParameters.context);
    }

    public void sendNextLevelEvent() {
        mGameFlowEvent.post(GameFlowEvent.EVENT_GO_TO_NEXT_LEVEL, 0,
                sSystemRegistry.contextParameters.context);
    }

    public void sendGameEvent(int type, int index, boolean immediate) {
        if (immediate) {
            mGameFlowEvent.postImmediate(type, index,
                    sSystemRegistry.contextParameters.context);
        } else {
            mGameFlowEvent.post(type, index,
                    sSystemRegistry.contextParameters.context);
        }
    }

    /**
     * Loads a level from a JSON file resource. The file consists of several layers, including background
     * tile layers and at most one collision layer. Each layer is used to bootstrap related systems
     * and provide them with layer data.
     * @param level   The current level object reference.
     * @param stream  The input stream for the JSON level file resource.
     * @param root    The ObjectManager root.
     * @return boolean True if the level was loaded successfully.
     */
    public boolean loadLevel(LevelTree.Level level, InputStream stream, ObjectManager root) {
        if (stream == null) return false;

        boolean success = false;
        mCurrentLevel = level;

        try {
            // 1. InputStream을 읽어 String 데이터로 취합
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            // 2. 전체 JSON 객체 생성 및 시그니처(96) 검증
            JSONObject levelJson = new JSONObject(builder.toString());
            int signature = levelJson.getInt("signature");

            if (signature == 96) {
                final int layerCount = levelJson.getInt("layerCount");
                final int backgroundIndex = levelJson.getInt("backgroundIndex");

                mRoot = root;
                mTileWidth = 32;
                mTileHeight = 32;

                ContextParameters params = sSystemRegistry.contextParameters;
                int currentPriority = SortConstants.BACKGROUND_START + 1;

                // 파이썬 스크립트 출력 결과물 구조의 "layers" 배열 가져오기
                JSONArray layersArray = levelJson.getJSONArray("layers");

                for (int x = 0; x < layerCount; x++) {
                    JSONObject layerJson = layersArray.getJSONObject(x);

                    final int type = layerJson.getInt("type");
                    final int tileIndex = layerJson.getInt("tileIndex");
                    final float scrollSpeed = (float) layerJson.getDouble("scrollSpeed");

                    // 내포된 world 객체를 추출하여 수정된 TiledWorld JSON 생성자로 인스턴스화
                    JSONObject worldJson = layerJson.getJSONObject("world");
                    TiledWorld world = new TiledWorld(worldJson);

                    if (type == 0) { // background layer
                        assert mWidthInTiles != 0;
                        assert mTileWidth != 0;

                        if (mWidthInTiles > 0 && mTileWidth > 0) {
                            LevelBuilder builderSystem = sSystemRegistry.levelBuilder;

                            if (mBackgroundObject == null) {
                                mBackgroundObject =
                                        builderSystem.buildBackground(
                                                backgroundIndex,
                                                mWidthInTiles * mTileWidth,
                                                mHeightInTiles * mTileHeight);
                                root.add(mBackgroundObject);
                            }

                            builderSystem.addTileMapLayer(mBackgroundObject, currentPriority,
                                    scrollSpeed, params.gameWidth, params.gameHeight,
                                    mTileWidth, mTileHeight, world, tileIndex);

                            currentPriority++;
                        }

                    } else if (type == 1) { // collision
                        mWidthInTiles = world.getWidth();
                        mHeightInTiles = world.getHeight();

                        CollisionSystem collision = sSystemRegistry.collisionSystem;
                        if (collision != null) {
                            collision.initialize(world, mTileWidth, mTileHeight);
                        }
                    } else if (type == 2) { // objects
                        mSpawnLocations = world;
                        spawnObjects();
                    } else if (type == 3) { // hot spots
                        HotSpotSystem hotSpots = sSystemRegistry.hotSpotSystem;
                        if (hotSpots != null) {
                            hotSpots.setWorld(world);
                        }
                    }
                }

                sSystemRegistry.levelBuilder.promoteForegroundLayer(mBackgroundObject);
                success = true;
            } else {
                Log.e(TAG, "Invalid level JSON signature: " + signature);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load JSON level resource", e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                // 무시
            }
        }

        return success;
    }

    public void spawnObjects() {
        GameObjectFactory factory = sSystemRegistry.gameObjectFactory;
        if (factory != null && mSpawnLocations != null) {
            DebugLog.d("LevelSystem", "Spawning Objects!");

            factory.spawnFromWorld(mSpawnLocations, mTileWidth, mTileHeight);
        }
    }

    public void incrementAttemptsCount() {
        mAttempts++;
    }

    public int getAttemptsCount() {
        return mAttempts;
    }

    public LevelTree.Level getCurrentLevel() {
        return mCurrentLevel;
    }
}