package com.replica.replicaisland;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

public class ReplayManager {
    public static final int MODE_NONE = 0;
    public static final int MODE_RECORD = 1;
    public static final int MODE_PLAYBACK = 2;

    private int mMode = MODE_NONE;
    private ArrayList<ReplayEvent> mEventList = new ArrayList<ReplayEvent>();
    private int mPlaybackIndex = 0;

    // 🚀 [신규 추가] 전 세계 리플레이 목록 조회를 위한 메타데이터 구조체
    public static class ReplayMetadata {
        public String id;
        public String title;
        public String uploader;
        public float totalTime;
        public int levelResource;

        public ReplayMetadata(String id, String title, String uploader, float totalTime, int levelResource) {
            this.id = id;
            this.title = title;
            this.uploader = uploader;
            this.totalTime = totalTime;
            this.levelResource = levelResource;
        }
    }

    // 개별 키 입력 타임라인 구조체
    public static class ReplayEvent {
        float time;
        String type;
        int keyCode;

        public ReplayEvent(float time, String type, int keyCode) {
            this.time = time;
            this.type = type;
            this.keyCode = keyCode;
        }
    }

    public void setMode(int mode) {
        this.mMode = mode;
        this.mEventList.clear();
        this.mPlaybackIndex = 0;
    }

    public int getMode() { return mMode; }

    public void recordEvent(float gameTime, String type, int keyCode) {
        if (mMode == MODE_RECORD) {
            mEventList.add(new ReplayEvent(gameTime, type, keyCode));
        }
    }

    public String getSerializedJson() {
        try {
            JSONArray eventArray = new JSONArray();
            for (ReplayEvent event : mEventList) {
                JSONObject obj = new JSONObject();
                obj.put("time", (double) event.time); // 정밀도 유지를 위한 업캐스팅
                obj.put("type", event.type);
                obj.put("keyCode", event.keyCode);
                eventArray.put(obj);
            }
            return eventArray.toString();
        } catch (Exception e) { return "[]"; }
    }

    public void deserializeJson(String jsonStr) {
        try {
            mEventList.clear();
            mPlaybackIndex = 0;
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                // 🛠️ [버그 수정] 구형 자바 엔진의 더블 파싱 안정화 연산 적용
                double rawTime = obj.optDouble("time", 0.0);
                mEventList.add(new ReplayEvent(
                        (float) rawTime,
                        obj.getString("type"),
                        obj.getInt("keyCode")
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 🚀 [신규 추가] 전 세계 리플레이 목록(배열) 데이터를 자바 객체 리스트로 한방에 파싱하는 링커
    public static ArrayList<ReplayMetadata> parseGlobalReplaysList(String jsonStr) {
        ArrayList<ReplayMetadata> list = new ArrayList<ReplayMetadata>();
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new ReplayMetadata(
                        obj.getString("id"),
                        obj.getString("title"),
                        obj.getString("uploader"),
                        (float) obj.optDouble("totalTime", 0.0),
                        obj.optInt("levelResource", 0)
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public void updatePlayback(float currentGameTime, Game game) {
        if (mMode != MODE_PLAYBACK) return;
        while (mPlaybackIndex < mEventList.size()) {
            ReplayEvent nextEvent = mEventList.get(mPlaybackIndex);
            if (nextEvent.time > currentGameTime) break;
            if (nextEvent.type.equals("KEY_DOWN")) {
                game.onKeyDownEvent(nextEvent.keyCode);
            } else if (nextEvent.type.equals("KEY_UP")) {
                game.onKeyUpEvent(nextEvent.keyCode);
            }
            mPlaybackIndex++;
        }
    }
}