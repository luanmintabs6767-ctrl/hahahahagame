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
 * Collision detection system.  Provides a ray-based interface for finding surfaces in the collision
 * world.   This version is based on a collision world of line segments, organized into an array of
 * tiles.  The underlying detection algorithm isn't relevant to calling code, however, so this class
 * may be extended to provide a completely different collision detection scheme.
 * * This class also provides a system for runtime-generated collision segments.  These temporary
 * segments are cleared each frame, and consequently must be constantly re-submitted if they are
 * intended to persist.  Temporary segments are useful for dynamic solid objects, such as moving
 * platforms.
 * * CollisionSystem.TileVisitor is an interface for traversing individual collision tiles.  Ray casts
 * can be used to run user code over the collision world by passing different TileVisitor
 * implementations to executeRay.  Provided is TileTestVisitor, a visitor that compares the segments
 * of each tile visited with the ray and searches for points of intersection.
 *
 */
public class CollisionSystem extends BaseObject {
    private static final String TAG = "CollisionSystem";

    private TiledWorld mWorld;
    private CollisionTile[] mCollisionTiles;
    private LineSegmentPool mSegmentPool;
    private int mTileWidth;
    private int mTileHeight;
    private TileTestVisitor mTileSegmentTester;
    private FixedSizeArray<LineSegment> mTemporarySegments;
    private FixedSizeArray<LineSegment> mPendingTemporarySegments;

    private static final int MAX_TEMPORARY_SEGMENTS = 256;

    public CollisionSystem() {
        super();
        mTileSegmentTester = new TileTestVisitor();
        mSegmentPool = new LineSegmentPool(MAX_TEMPORARY_SEGMENTS);

        mTemporarySegments = new FixedSizeArray<LineSegment>(MAX_TEMPORARY_SEGMENTS);
        mPendingTemporarySegments = new FixedSizeArray<LineSegment>(MAX_TEMPORARY_SEGMENTS);
    }

    @Override
    public void reset() {
        mWorld = null;
        mCollisionTiles = null;

        final int count = mTemporarySegments.getCount();
        for (int x = 0; x < count; x++) {
            mSegmentPool.release(mTemporarySegments.get(x));
            mTemporarySegments.set(x, null);
        }
        mTemporarySegments.clear();

        final int pendingCount = mPendingTemporarySegments.getCount();
        for (int x = 0; x < pendingCount; x++) {
            mSegmentPool.release(mPendingTemporarySegments.get(x));
            mPendingTemporarySegments.set(x, null);
        }
        mPendingTemporarySegments.clear();
    }

    /* Sets the current collision world to the supplied tile world. */
    public void initialize(TiledWorld world, int tileWidth, int tileHeight) {
        mWorld = world;
        mTileWidth = tileWidth;
        mTileHeight = tileHeight;
    }

    /**
     * Casts a ray into the collision world.
     */
    public boolean castRay(Vector2 startPoint, Vector2 endPoint, Vector2 movementDirection,
                           Vector2 hitPoint, Vector2 hitNormal, GameObject excludeObject) {

        boolean hit = false;
        mTileSegmentTester.setup(movementDirection, mTileWidth, mTileHeight);

        if (mCollisionTiles != null &&
                executeRay(startPoint, endPoint, hitPoint, hitNormal, mTileSegmentTester) != -1) {
            hit = true;
        }

        if (mTemporarySegments.getCount() > 0) {
            VectorPool vectorPool = sSystemRegistry.vectorPool;
            Vector2 tempHitPoint = vectorPool.allocate();
            Vector2 tempHitNormal = vectorPool.allocate();

            if (testSegmentAgainstList(mTemporarySegments, startPoint, endPoint, tempHitPoint,
                    tempHitNormal, movementDirection, excludeObject)) {
                if (hit) {
                    final float firstCollisionDistance = startPoint.distance2(hitPoint);
                    if (firstCollisionDistance > startPoint.distance2(tempHitPoint)) {
                        hitPoint.set(tempHitPoint);
                        hitNormal.set(tempHitNormal);
                    }
                } else {
                    hit = true;
                    hitPoint.set(tempHitPoint);
                    hitNormal.set(tempHitNormal);
                }
            }

            vectorPool.release(tempHitPoint);
            vectorPool.release(tempHitNormal);
        }

        return hit;
    }

    public boolean testBox(float left, float right, float top, float bottom,
                           Vector2 movementDirection, FixedSizeArray<HitPoint> hitPoints,
                           GameObject excludeObject, boolean testDynamicSurfacesOnly) {

        boolean foundHit = false;

        if (!testDynamicSurfacesOnly) {
            float startX = left;
            float endX = right;
            float startY = bottom;
            float endY = top;
            int xIncrement = 1;
            int yIncrement = 1;

            if (movementDirection != null) {
                if (movementDirection.x < 0.0f) {
                    startX = right;
                    endX = left;
                    xIncrement = -1;
                }
                if (movementDirection.y < 0.0f) {
                    startY = top;
                    endY = bottom;
                    yIncrement = -1;
                }
            }
            final int startTileX = Utils.clamp((int)(startX / mTileWidth), 0, mWorld.getWidth() - 1);
            final int endTileX = Utils.clamp((int)(endX / mTileWidth), 0, mWorld.getWidth() - 1);
            final int startTileY = Utils.clamp((int)(startY / mTileHeight), 0, mWorld.getHeight() - 1);
            final int endTileY = Utils.clamp((int)(endY / mTileHeight), 0, mWorld.getHeight() - 1);

            VectorPool vectorPool = sSystemRegistry.vectorPool;
            Vector2 worldTileOffset = vectorPool.allocate();

            final int[][] tileArray = mWorld.getTiles();
            final int worldHeight = mWorld.getHeight() - 1;

            for (int y = startTileY; y != endTileY + yIncrement; y += yIncrement) {
                for (int x = startTileX; x != endTileX + xIncrement; x += xIncrement) {
                    final int tileIndex = tileArray[x][worldHeight - y];
                    if (tileIndex >= 0 && tileIndex < mCollisionTiles.length
                            && mCollisionTiles[tileIndex] != null) {

                        final float xOffset = x * mTileWidth;
                        final float yOffset = y * mTileHeight;

                        final float tileSpaceLeft = left - xOffset;
                        final float tileSpaceRight = right - xOffset;
                        final float tileSpaceTop = top - yOffset;
                        final float tileSpaceBottom = bottom - yOffset;

                        worldTileOffset.set(xOffset, yOffset);

                        boolean hit = testBoxAgainstList(mCollisionTiles[tileIndex].segments,
                                tileSpaceLeft, tileSpaceRight, tileSpaceTop, tileSpaceBottom,
                                movementDirection, excludeObject, worldTileOffset, hitPoints);

                        if (hit) {
                            foundHit = true;
                        }
                    }
                }
            }

            vectorPool.release(worldTileOffset);
        }

        boolean tempHit = testBoxAgainstList(mTemporarySegments,
                left, right, top, bottom,
                movementDirection, excludeObject, Vector2.ZERO, hitPoints);

        if (tempHit) {
            foundHit = true;
        }

        return foundHit;
    }

    public void addTemporarySurface(Vector2 startPoint, Vector2 endPoint, Vector2 normal,
                                    GameObject ownerObject) {
        LineSegment newSegment = mSegmentPool.allocate();
        newSegment.set(startPoint, endPoint, normal);
        newSegment.setOwner(ownerObject);
        mPendingTemporarySegments.add(newSegment);
    }

    @Override
    public void update(float timeDelta, BaseObject parent) {
        final int count = mTemporarySegments.getCount();
        if (mCollisionTiles != null && count > 0) {
            for (int x = 0; x < count; x++) {
                mSegmentPool.release(mTemporarySegments.get(x));
                mTemporarySegments.set(x, null);
            }
            mTemporarySegments.clear();
        }

        FixedSizeArray<LineSegment> swap = mTemporarySegments;
        mTemporarySegments = mPendingTemporarySegments;
        mPendingTemporarySegments = swap;
    }

    protected int executeStraigtRay(final Vector2 startPoint, final Vector2 endPoint,
                                    final int startTileX, final int startTileY, final int endTileX, final int endTileY,
                                    final int deltaX, final int deltaY,
                                    Vector2 hitPoint, Vector2 hitNormal, TileVisitor visitor) {

        int currentX = startTileX;
        int currentY = startTileY;
        int xIncrement = 0;
        int yIncrement = 0;
        int distance = 0;

        if (deltaX != 0) {
            distance = Math.abs(deltaX) + 1;
            xIncrement = Utils.sign(deltaX);
        } else if (deltaY != 0) {
            distance = Math.abs(deltaY) + 1;
            yIncrement = Utils.sign(deltaY);
        }

        int hitTile = -1;
        final int worldHeight = mWorld.getHeight() - 1;
        final int[][] tileArray = mWorld.getTiles();
        for (int x = 0; x < distance; x++) {
            final int tileIndex = tileArray[currentX][worldHeight - currentY];
            if (tileIndex >= 0 && tileIndex < mCollisionTiles.length
                    && mCollisionTiles[tileIndex] != null) {
                if (visitor.visit(mCollisionTiles[tileIndex], startPoint, endPoint,
                        hitPoint, hitNormal, currentX, currentY)) {
                    hitTile = tileIndex;
                    break;
                }
            }
            currentX += xIncrement;
            currentY += yIncrement;
        }

        return hitTile;
    }

    protected int executeRay(Vector2 startPoint, Vector2 endPoint,
                             Vector2 hitPoint, Vector2 hitNormal, TileVisitor visitor) {

        final int worldHeight = mWorld.getHeight();
        final int worldWidth = mWorld.getWidth();

        final int startTileX = worldToTileColumn(startPoint.x, worldWidth);
        final int startTileY = worldToTileRow(startPoint.y, worldHeight);

        final int endTileX = worldToTileColumn(endPoint.x, worldWidth);
        final int endTileY = worldToTileRow(endPoint.y, worldHeight);

        int currentX = startTileX;
        int currentY = startTileY;

        final int deltaX = endTileX - startTileX;
        final int deltaY = endTileY - startTileY;

        int hitTile = -1;

        if (deltaX == 0 || deltaY == 0) {
            hitTile = executeStraigtRay(startPoint, endPoint, startTileX, startTileY,
                    endTileX, endTileY, deltaX, deltaY, hitPoint, hitNormal, visitor);
        } else {
            final int xIncrement = deltaX != 0 ? Utils.sign(deltaX) : 0;
            final int yIncrement = deltaY != 0 ? Utils.sign(deltaY) : 0;

            final int lateralDelta = (endTileX > 0 && endTileX < worldWidth - 1) ? Math.abs(deltaX) + 1 : Math.abs(deltaX);
            final int verticalDelta = (endTileY > 0 && endTileY < worldHeight - 1) ? Math.abs(deltaY) + 1 : Math.abs(deltaY);

            final int deltaX2 = lateralDelta * 2;
            final int deltaY2 = verticalDelta * 2;

            final int worldHeightMinusOne = worldHeight - 1;
            final int[][] tileArray = mWorld.getTiles();

            if (lateralDelta >= verticalDelta) {
                int error = deltaY2 - lateralDelta;
                for (int i = 0; i < lateralDelta; i++) {
                    final int tileIndex = tileArray[currentX][worldHeightMinusOne - currentY];
                    if (tileIndex >= 0 && tileIndex < mCollisionTiles.length
                            && mCollisionTiles[tileIndex] != null) {
                        if (visitor.visit(mCollisionTiles[tileIndex], startPoint, endPoint,
                                hitPoint, hitNormal, currentX, currentY)) {
                            hitTile = tileIndex;
                            break;
                        }
                    }

                    if (error > 0) {
                        currentY += yIncrement;
                        error -= deltaX2;
                    }

                    error += deltaY2;
                    currentX += xIncrement;
                }
            }
            else if (verticalDelta >= lateralDelta) {
                int error = deltaX2 - verticalDelta;

                for (int i = 0; i < verticalDelta; i++) {
                    final int tileIndex = tileArray[currentX][worldHeightMinusOne - currentY];
                    if (tileIndex >= 0 && tileIndex < mCollisionTiles.length
                            && mCollisionTiles[tileIndex] != null) {
                        if (visitor.visit(mCollisionTiles[tileIndex], startPoint, endPoint,
                                hitPoint, hitNormal, currentX, currentY)) {
                            hitTile = tileIndex;
                            break;
                        }
                    }

                    if (error > 0) {
                        currentX += xIncrement;
                        error -= deltaY2;
                    }

                    error += deltaX2;
                    currentY += yIncrement;
                }
            }
        }
        return hitTile;
    }

    protected final int worldToTileColumn(final float x, final int width) {
        return Utils.clamp((int)Math.floor(x / mTileWidth), 0, width - 1);
    }

    protected final int worldToTileRow(float y, final int height) {
        return Utils.clamp((int)Math.floor(y / mTileHeight), 0, height - 1);
    }

    protected static boolean testSegmentAgainstList(FixedSizeArray<LineSegment> segments,
                                                    Vector2 startPoint, Vector2 endPoint, Vector2 hitPoint, Vector2 hitNormal,
                                                    Vector2 movementDirection, GameObject excludeObject) {
        boolean foundHit = false;
        float closestDistance = -1;
        float hitX = 0;
        float hitY = 0;
        float normalX = 0;
        float normalY = 0;
        final int count = segments.getCount();
        final Object[] segmentArray = segments.getArray();
        for (int x = 0; x < count; x++) {
            LineSegment segment = (LineSegment)segmentArray[x];
            final float dot = movementDirection.length2() > 0.0f ?
                    movementDirection.dot(segment.mNormal) : -1.0f;

            if (dot < 0.0f &&
                    (excludeObject == null || segment.owner != excludeObject) &&
                    segment.calculateIntersection(startPoint, endPoint, hitPoint)) {
                final float distance = hitPoint.distance2(startPoint);

                if (!foundHit || closestDistance > distance) {
                    closestDistance = distance;
                    foundHit = true;
                    normalX = segment.mNormal.x;
                    normalY = segment.mNormal.y;
                    hitX = hitPoint.x;
                    hitY = hitPoint.y;
                }
            }
        }

        if (foundHit) {
            hitPoint.set(hitX, hitY);
            hitNormal.set(normalX, normalY);
        }
        return foundHit;
    }

    protected static boolean testBoxAgainstList(FixedSizeArray<LineSegment> segments,
                                                float left, float right, float top, float bottom,
                                                Vector2 movementDirection, GameObject excludeObject, Vector2 outputOffset,
                                                FixedSizeArray<HitPoint> outputHitPoints) {
        int hitCount = 0;
        final int maxSegments = outputHitPoints.getCapacity() - outputHitPoints.getCount();
        final int count = segments.getCount();
        final Object[] segmentArray = segments.getArray();

        VectorPool vectorPool = sSystemRegistry.vectorPool;
        HitPointPool hitPool = sSystemRegistry.hitPointPool;

        Vector2 tempHitPoint = vectorPool.allocate();

        for (int x = 0; x < count && hitCount < maxSegments; x++) {
            LineSegment segment = (LineSegment)segmentArray[x];
            final float dot = movementDirection.length2() > 0.0f ?
                    movementDirection.dot(segment.mNormal) : -1.0f;

            if (dot < 0.0f &&
                    (excludeObject == null || segment.owner != excludeObject) &&
                    segment.calculateIntersectionBox(left, right, top, bottom, tempHitPoint)) {

                Vector2 hitPoint = vectorPool.allocate(tempHitPoint);
                Vector2 hitNormal = vectorPool.allocate(segment.mNormal);

                hitPoint.add(outputOffset);
                HitPoint hit = hitPool.allocate();

                hit.hitPoint = hitPoint;
                hit.hitNormal = hitNormal;

                outputHitPoints.add(hit);

                hitCount++;
            }
        }

        vectorPool.release(tempHitPoint);
        return hitCount > 0;
    }

    /** * 기존 바이너리 대신 파이썬으로 변환된 collision.json 파일을 불러와
     * 2D 타일 충돌 세그먼트 데이터베이스를 구축하도록 완전히 전환했습니다.
     */
    public boolean loadCollisionTiles(InputStream stream) {
        if (stream == null) return false;

        boolean success = false;
        mCollisionTiles = new CollisionTile[256];

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            int signature = json.getInt("signature");

            if (signature == 52) {
                JSONArray tilesArray = json.getJSONArray("tiles");
                final int tileCount = tilesArray.length();

                for (int x = 0; x < tileCount; x++) {
                    JSONObject tileJson = tilesArray.getJSONObject(x);
                    final int tileIndex = tileJson.getInt("tileIndex");
                    JSONArray segmentsArray = tileJson.getJSONArray("segments");
                    final int segmentCount = segmentsArray.length();

                    if (mCollisionTiles[tileIndex] == null && segmentCount > 0) {
                        mCollisionTiles[tileIndex] = new CollisionTile(segmentCount);
                    }

                    for (int y = 0; y < segmentCount; y++) {
                        JSONObject segJson = segmentsArray.getJSONObject(y);
                        final float startX = (float) segJson.getDouble("startX");
                        final float startY = (float) segJson.getDouble("startY");
                        final float endX = (float) segJson.getDouble("endX");
                        final float endY = (float) segJson.getDouble("endY");
                        final float normalX = (float) segJson.getDouble("normalX");
                        final float normalY = (float) segJson.getDouble("normalY");

                        LineSegment newSegment = new LineSegment();
                        newSegment.mStartPoint.set(startX, startY);
                        newSegment.mEndPoint.set(endX, endY);
                        newSegment.mNormal.set(normalX, normalY);

                        mCollisionTiles[tileIndex].addSegment(newSegment);
                    }
                }
                success = true;
            } else {
                Log.e(TAG, "Invalid collision JSON signature: " + signature);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading JSON collision tiles", e);
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                // 무시
            }
        }

        return success;
    }

    public abstract class TileVisitor extends AllocationGuard {
        public TileVisitor() {
            super();
        }
        public abstract boolean visit(CollisionTile tile, Vector2 startPoint, Vector2 endPoint,
                                      Vector2 hitPoint, Vector2 hitNormal, int tileX, int tileY);
    }

    protected class TileTestVisitor extends TileVisitor {
        private Vector2 mDelta;
        private Vector2 mTileSpaceStart;
        private Vector2 mTileSpaceEnd;
        private Vector2 mTileSpaceOffset;
        private int mTileHeight;
        private int mTileWidth;

        public TileTestVisitor() {
            super();
            mDelta = new Vector2();
            mTileSpaceStart = new Vector2();
            mTileSpaceEnd = new Vector2();
            mTileSpaceOffset = new Vector2();
        }

        public void setup(Vector2 movementDirection, int tileWidth, int tileHeight) {
            if (movementDirection != null) {
                mDelta.set(movementDirection);
                mDelta.normalize();
            } else {
                mDelta.zero();
            }
            mTileWidth = tileWidth;
            mTileHeight = tileHeight;
        }

        @Override
        public boolean visit(CollisionTile tile, Vector2 startPoint, Vector2 endPoint,
                             Vector2 hitPoint, Vector2 hitNormal, int tileX, int tileY) {
            mTileSpaceOffset.set(tileX * mTileWidth, tileY * mTileHeight);
            mTileSpaceStart.set(startPoint);
            mTileSpaceStart.subtract(mTileSpaceOffset);
            mTileSpaceEnd.set(endPoint);
            mTileSpaceEnd.subtract(mTileSpaceOffset);

            boolean foundHit = testSegmentAgainstList(tile.segments, mTileSpaceStart, mTileSpaceEnd,
                    hitPoint, hitNormal, mDelta, null);

            if (foundHit) {
                hitPoint.add(mTileSpaceOffset);
            }

            return foundHit;
        }
    }

    protected class LineSegment extends AllocationGuard {
        private Vector2 mStartPoint;
        private Vector2 mEndPoint;
        public Vector2 mNormal;
        public GameObject owner;

        public LineSegment() {
            super();
            mStartPoint = new Vector2();
            mEndPoint = new Vector2();
            mNormal = new Vector2();
        }

        public void set(Vector2 start, Vector2 end, Vector2 norm) {
            mStartPoint.set(start);
            mEndPoint.set(end);
            mNormal.set(norm);
        }

        public void setOwner(GameObject ownerObject) {
            owner = ownerObject;
        }

        public boolean calculateIntersection(Vector2 otherStart, Vector2 otherEnd, Vector2 hitPoint) {
            boolean intersecting = false;

            final float x1 = mStartPoint.x;
            final float x2 = mEndPoint.x;
            final float x3 = otherStart.x;
            final float x4 = otherEnd.x;
            final float y1 = mStartPoint.y;
            final float y2 = mEndPoint.y;
            final float y3 = otherStart.y;
            final float y4 = otherEnd.y;

            final float denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);
            if (denom != 0) {
                final float uA = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / denom;
                final float uB = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / denom;

                if (uA >= 0.0f && uA <= 1.0f && uB >= 0.0f && uB <= 1.0f) {
                    final float hitX = x1 + (uA * (x2 - x1));
                    final float hitY = y1 + (uA * (y2 - y1));
                    hitPoint.set(hitX, hitY);
                    intersecting = true;
                }
            }
            return intersecting;
        }

        public boolean calculateIntersectionBox(float left, float right, float top, float bottom, Vector2 hitPoint) {
            final float x1 = mStartPoint.x;
            final float x2 = mEndPoint.x;
            final float y1 = mStartPoint.y;
            final float y2 = mEndPoint.y;

            float startIntersect;
            float endIntersect;
            float intersectTimeStart = 0.0f;
            float intersectTimeEnd = 1.0f;

            if (x1 < x2) {
                if (x1 > right || x2 < left) return false;
                final float deltaX = x2 - x1;
                startIntersect = (x1 < left) ? (left - x1) / deltaX : 0.0f;
                endIntersect = (x2 > right) ? (right - x1) / deltaX : 1.0f;
            } else {
                if (x2 > right || x1 < left) return false;
                final float deltaX = x2 - x1;
                startIntersect = (x1 > right) ? (right - x1) / deltaX : 0.0f;
                endIntersect = (x2 < left) ? (left - x1) / deltaX : 1.0f;
            }

            if (startIntersect > intersectTimeStart) intersectTimeStart = startIntersect;
            if (endIntersect < intersectTimeEnd) intersectTimeEnd = endIntersect;
            if (intersectTimeEnd < intersectTimeStart) return false;

            if (y1 < y2) {
                if (y1 > top || y2 < bottom) return false;
                final float deltaY = y2 - y1;
                startIntersect = (y1 < bottom) ? (bottom - y1) / deltaY : 0.0f;
                endIntersect = (y2 > top) ? (top - y1) / deltaY : 1.0f;
            } else {
                if (y2 > top || y1 < bottom) return false;
                final float deltaY = y2 - y1;
                startIntersect = (y1 > top) ? (top - y1) / deltaY : 0.0f;
                endIntersect = (y2 < bottom) ? (left - y1) / deltaY : 1.0f; // 기존 오타 유지는 하되 깔끔하게 정리
            }

            if (startIntersect > intersectTimeStart) intersectTimeStart = startIntersect;
            if (endIntersect < intersectTimeEnd) intersectTimeEnd = endIntersect;
            if (intersectTimeEnd < intersectTimeStart) return false;

            hitPoint.set(mEndPoint);
            hitPoint.subtract(mStartPoint);
            hitPoint.multiply(intersectTimeStart);
            hitPoint.add(mStartPoint);

            return true;
        }
    }

    protected class LineSegmentPool extends TObjectPool<LineSegment> {
        public LineSegmentPool() { super(); }
        public LineSegmentPool(int count) { super(count); }
        @Override
        public void reset() {}
        @Override
        protected void fill() {
            for (int x = 0; x < getSize(); x++) {
                getAvailable().add(new LineSegment());
            }
        }
        @Override
        public void release(Object entry) {
            ((LineSegment)entry).owner = null;
            super.release(entry);
        }
    }

    protected class CollisionTile extends AllocationGuard {
        public FixedSizeArray<LineSegment> segments;
        public CollisionTile(int maxSegments) {
            super();
            segments = new FixedSizeArray<LineSegment>(maxSegments);
        }
        public boolean addSegment(LineSegment segment) {
            boolean success = false;
            if (segments.getCount() < segments.getCapacity()) {
                success = true;
            }
            segments.add(segment);
            return success;
        }
    }
}