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
 * TiledWorld manages a 2D map of tile indexes that define a "world" of tiles.  These may be
 * foreground or background layers in a scrolling game, or a layer of collision tiles, or some other
 * type of tile map entirely.  The TiledWorld maps xy positions to tile indices and also handles
 * deserialization of tilemap files.
 */
public class TiledWorld extends AllocationGuard {
    private static final String TAG = "TiledWorld";

    private int[][] mTilesArray;
    private int mRowCount;
    private int mColCount;

    public TiledWorld(int cols, int rows) {
        super();
        mTilesArray = new int[cols][rows];
        mRowCount = rows;
        mColCount = cols;

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                mTilesArray[x][y] = -1;
            }
        }

        calculateSkips();
    }

    /**
     * JSON 포맷의 InputStream을 받아 바로 파싱하는 생성자
     */
    public TiledWorld(InputStream stream) {
        super();
        parseInputStream(stream);
        calculateSkips();
    }

    /**
     * 이미 상위 객체(LevelSystem 등)에서 파싱된 JSONObject를 받아 초기화하는 생성자
     */
    public TiledWorld(JSONObject worldJson) {
        super();
        parseJsonInput(worldJson);
        calculateSkips();
    }

    public int getTile(int x, int y) {
        int result = -1;
        if (x >= 0 && x < mColCount && y >= 0 && y < mRowCount) {
            result = mTilesArray[x][y];
        }
        return result;
    }

    /**
     * InputStream을 직접 읽어 JSONObject로 변환 후 데이터를 바인딩합니다.
     */
    protected boolean parseInputStream(InputStream stream) {
        if (stream == null) return false;

        boolean success = false;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject rootJson = new JSONObject(builder.toString());

            // 만약 레벨 레이어에서 호출된 단독 TiledWorld 형식이 아니라
            // 96번 시그니처 파일 전체가 들어왔을 때의 안전장치
            if (rootJson.has("signature") && rootJson.getInt("signature") == 42) {
                success = parseJsonInput(rootJson);
            } else {
                success = parseJsonInput(rootJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse TiledWorld from InputStream", e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                // 무시
            }
        }
        return success;
    }

    /**
     * 파이썬 스크립트 결과물 구조에 맞춰 JSON 객체로부터 타일 맵을 생성합니다.
     */
    protected boolean parseJsonInput(JSONObject worldJson) {
        if (worldJson == null) return false;

        boolean success = false;
        try {
            final int width = worldJson.getInt("width");
            final int height = worldJson.getInt("height");
            JSONArray tilesArray = worldJson.getJSONArray("tiles");

            mTilesArray = new int[width][height];
            mRowCount = height;
            mColCount = width;

            for (int y = 0; y < height; y++) {
                JSONArray row = tilesArray.getJSONArray(y);
                for (int x = 0; x < width; x++) {
                    mTilesArray[x][y] = row.getInt(x);
                }
            }
            success = true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse JSON structure for TiledWorld", e);
        }

        return success;
    }

    protected void calculateSkips() {
        int emptyTileCount = 0;
        for (int y = mRowCount - 1; y >= 0; y--) {
            for (int x = mColCount - 1; x >= 0; x--) {
                if (mTilesArray[x][y] < 0) {
                    emptyTileCount++;
                    mTilesArray[x][y] = -emptyTileCount;
                } else {
                    emptyTileCount = 0;
                }
            }
        }
    }

    public final int getWidth() {
        return mColCount;
    }

    public final int getHeight() {
        return mRowCount;
    }

    public final int[][] getTiles() {
        return mTilesArray;
    }
}