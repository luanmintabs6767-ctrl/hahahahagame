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

import android.view.KeyEvent;

public class InputGameInterface extends BaseObject {
    private static final float ORIENTATION_DEAD_ZONE_MIN = 0.03f;
    private static final float ORIENTATION_DEAD_ZONE_MAX = 0.1f;
    private static final float ORIENTATION_DEAD_ZONE_SCALE = 0.75f;

    private final static float ROLL_TIMEOUT = 0.1f;
    private final static float ROLL_RESET_DELAY = 0.075f;

    private final static float ROLL_FILTER = 0.4f;
    private final static float ROLL_DECAY = 8.0f;

    private final static float KEY_FILTER = 0.25f;
    private final static float SLIDER_FILTER = 0.25f;

    // 인풋 컴포넌트 버튼 개체
    private InputButton mJumpButton = new InputButton();
    private InputButton mAttackButton = new InputButton();
    private InputButton mPossessButton = new InputButton(); // 빙의 버튼 개체 생성

    private InputXY mDirectionalPad = new InputXY();
    private InputXY mTilt = new InputXY();

    private int mLeftKeyCode = KeyEvent.KEYCODE_DPAD_LEFT;
    private int mRightKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
    private int mJumpKeyCode = KeyEvent.KEYCODE_SPACE;
    private int mAttackKeyCode = KeyEvent.KEYCODE_SHIFT_LEFT;
    private int mPossessKeyCode = KeyEvent.KEYCODE_C; // 물리 키보드용 매핑 키 (C)

    private float mOrientationDeadZoneMin = ORIENTATION_DEAD_ZONE_MIN;
    private float mOrientationDeadZoneMax = ORIENTATION_DEAD_ZONE_MAX;
    private float mOrientationDeadZoneScale = ORIENTATION_DEAD_ZONE_SCALE;
    private float mOrientationSensitivity = 1.0f;
    private float mOrientationSensitivityFactor = 1.0f;
    private float mMovementSensitivity = 1.0f;

    private boolean mUseClickButtonForAttack = true;
    private boolean mUseOrientationForMovement = false;
    private boolean mUseOnScreenControls = false;

    private Object mJumpTouchSource = null;
    private Object mAttackTouchSource = null;

    private float mLastRollTime;

    public InputGameInterface() {
        super();
        reset();
    }

    @Override
    public void reset() {
        mJumpButton.release();
        mAttackButton.release();
        mPossessButton.release();
        mDirectionalPad.release();
        mTilt.release();
    }

    @Override
    public void update(float timeDelta, BaseObject parent) {
        InputSystem input = sSystemRegistry.inputSystem;
        final InputButton[] keys = input.getKeyboard().getKeys();
        final InputXY orientation = input.getOrientationSensor();

        mTilt.clone(orientation);

        final InputTouchScreen touch = input.getTouchScreen();
        final float gameTime = sSystemRegistry.timeSystem.getGameTime();

        float sliderOffset = 0;

        // 온스크린 방향 패드 슬라이더 연산
        if (mUseOnScreenControls) {
            final InputXY sliderTouch = touch.findPointerInRegion(
                    ButtonConstants.MOVEMENT_SLIDER_REGION_X,
                    ButtonConstants.MOVEMENT_SLIDER_REGION_Y,
                    ButtonConstants.MOVEMENT_SLIDER_REGION_WIDTH,
                    ButtonConstants.MOVEMENT_SLIDER_REGION_HEIGHT);

            if (sliderTouch != null) {
                final float halfWidth = ButtonConstants.MOVEMENT_SLIDER_BAR_WIDTH / 2.0f;
                final float center = ButtonConstants.MOVEMENT_SLIDER_X + halfWidth;
                final float offset = sliderTouch.getX() - center;
                float magnitudeRamp = Math.abs(offset) > halfWidth ? 1.0f : (Math.abs(offset) / halfWidth);

                final float magnitude = magnitudeRamp * Utils.sign(offset) * SLIDER_FILTER * mMovementSensitivity;
                sliderOffset = magnitudeRamp * Utils.sign(offset);
                mDirectionalPad.press(gameTime, magnitude, 0.0f);
            } else {
                mDirectionalPad.release();
            }
        } else if (mUseOrientationForMovement) {
            mDirectionalPad.clone(orientation);
            mDirectionalPad.setMagnitude(
                    filterOrientationForMovement(orientation.getX()),
                    filterOrientationForMovement(orientation.getY()));
        } else {
            // 트랙볼 연산부
            final InputXY trackball = input.getTrackball();
            final InputButton left = keys[mLeftKeyCode];
            final InputButton right = keys[mRightKeyCode];
            final float leftPressedTime = left.getLastPressedTime();
            final float rightPressedTime = right.getLastPressedTime();

            if (trackball.getLastPressedTime() > Math.max(leftPressedTime, rightPressedTime)) {
                if (gameTime - trackball.getLastPressedTime() < ROLL_TIMEOUT) {
                    float newX;
                    float newY;
                    final float delay = Math.max(ROLL_RESET_DELAY, timeDelta);
                    if (gameTime - mLastRollTime <= delay) {
                        newX = mDirectionalPad.getX() + (trackball.getX() * ROLL_FILTER * mMovementSensitivity);
                        newY = mDirectionalPad.getY() + (trackball.getY() * ROLL_FILTER * mMovementSensitivity);
                    } else {
                        float oldX = mDirectionalPad.getX() != 0.0f ? mDirectionalPad.getX() / 2.0f : 0.0f;
                        float oldY = mDirectionalPad.getX() != 0.0f ? mDirectionalPad.getX() / 2.0f : 0.0f;
                        newX = oldX + (trackball.getX() * ROLL_FILTER * mMovementSensitivity);
                        newY = oldY + (trackball.getX() * ROLL_FILTER * mMovementSensitivity);
                    }

                    mDirectionalPad.press(gameTime, newX, newY);
                    mLastRollTime = gameTime;
                    trackball.release();
                } else {
                    float x = mDirectionalPad.getX();
                    float y = mDirectionalPad.getY();
                    if (x != 0.0f) {
                        int sign = Utils.sign(x);
                        x = x - (sign * ROLL_DECAY * timeDelta);
                        if (Utils.sign(x) != sign) {
                            x = 0.0f;
                        }
                    }

                    if (y != 0.0f) {
                        int sign = Utils.sign(y);
                        y = y - (sign * ROLL_DECAY * timeDelta);
                        if (Utils.sign(x) != sign) {
                            y = 0.0f;
                        }
                    }

                    if (x == 0 && y == 0) {
                        mDirectionalPad.release();
                    } else {
                        mDirectionalPad.setMagnitude(x, y);
                    }
                }

            } else {
                float xMagnitude = 0.0f;
                float yMagnitude = 0.0f;
                float pressTime = 0.0f;
                if (leftPressedTime > rightPressedTime) {
                    xMagnitude = -left.getMagnitude() * KEY_FILTER * mMovementSensitivity;
                    pressTime = leftPressedTime;
                } else {
                    xMagnitude = right.getMagnitude() * KEY_FILTER * mMovementSensitivity;
                    pressTime = rightPressedTime;
                }

                if (xMagnitude != 0.0f) {
                    mDirectionalPad.press(pressTime, xMagnitude, yMagnitude);
                } else {
                    mDirectionalPad.release();
                }
            }
        }

        // 가상 패드용 버튼 위치 매핑
        final InputButton jumpKey = keys[mJumpKeyCode];

        float flyButtonRegionX = ButtonConstants.FLY_BUTTON_REGION_X;
        float stompButtonRegionX = ButtonConstants.STOMP_BUTTON_REGION_X;

        // 💥 [터치 인식 좌표계 전면 대수술]
        // HudSystem의 POSSESS_BUTTON_X인 15.0f 공간과 정밀 동기화 완료
        float possessButtonRegionX = 15.0f;
        float possessButtonRegionY = 15.0f;

        if (mUseOnScreenControls) {
            ContextParameters params = sSystemRegistry.contextParameters;
            flyButtonRegionX = params.gameWidth - ButtonConstants.FLY_BUTTON_REGION_WIDTH - ButtonConstants.FLY_BUTTON_REGION_X;
            stompButtonRegionX = params.gameWidth - ButtonConstants.STOMP_BUTTON_REGION_WIDTH - ButtonConstants.STOMP_BUTTON_REGION_X;

            // 💥 [핵심 변경] 안드로이드 터치 센서의 상단 기준(0,0)에 일치하도록 Y축 반전 보정 수식을 역추적 탑재
            // HudSystem이 하단에서 15.0f 공간에 그린 위치를 터치 센서 스크린 축(Top-Down) 변환 완료!
            possessButtonRegionY = params.gameHeight - ButtonConstants.STOMP_BUTTON_REGION_HEIGHT - possessButtonRegionY;
        }

        // 1. 점프(플라이) 버튼 터치 영역 처리
        final InputXY jumpTouch = touch.findPointerInRegion(
                flyButtonRegionX,
                ButtonConstants.FLY_BUTTON_REGION_Y,
                ButtonConstants.FLY_BUTTON_REGION_WIDTH,
                ButtonConstants.FLY_BUTTON_REGION_HEIGHT);

        if (jumpKey.getPressed()) {
            mJumpButton.press(jumpKey.getLastPressedTime(), jumpKey.getMagnitude());
            mJumpTouchSource = jumpKey;
        } else if (jumpTouch != null) {
            if (!mJumpButton.getPressed()) {
                mJumpButton.press(jumpTouch.getLastPressedTime(), 1.0f);
            }
            mJumpTouchSource = jumpTouch;
        } else {
            mJumpButton.release();
            mJumpTouchSource = null;
        }

        // 2. 공격(쿵찍) 버튼 터치 영역 처리
        final InputButton attackKey = keys[mAttackKeyCode];
        final InputButton clickButton = keys[KeyEvent.KEYCODE_DPAD_CENTER];

        final InputXY stompTouch = touch.findPointerInRegion(
                stompButtonRegionX,
                ButtonConstants.STOMP_BUTTON_REGION_Y,
                ButtonConstants.STOMP_BUTTON_REGION_WIDTH,
                ButtonConstants.STOMP_BUTTON_REGION_HEIGHT);

        if (mUseClickButtonForAttack && clickButton.getPressed()) {
            mAttackButton.press(clickButton.getLastPressedTime(), clickButton.getMagnitude());
            mAttackTouchSource = clickButton;
        } else if (attackKey.getPressed()) {
            mAttackButton.press(attackKey.getLastPressedTime(), attackKey.getMagnitude());
            mAttackTouchSource = attackKey;
        } else if (stompTouch != null) {
            if (!mAttackButton.getPressed()) {
                mAttackButton.press(stompTouch.getLastPressedTime(), 1.0f);
            }
            mAttackTouchSource = stompTouch;
        } else {
            mAttackButton.release();
            mAttackTouchSource = null;
        }

        // 3. 💥 [수정 완료] 이제 겹침 간섭이 없고 반전 수식이 해결된 왼쪽 아래의 정확한 인접 박스로 터치를 완벽히 낚아챕니다.
        final InputXY possessTouch = touch.findPointerInRegion(
                possessButtonRegionX,
                possessButtonRegionY,
                ButtonConstants.STOMP_BUTTON_REGION_WIDTH,
                ButtonConstants.STOMP_BUTTON_REGION_HEIGHT);

        final InputButton possessKey = keys[mPossessKeyCode];

        // 키보드 C키 및 화면 가상버튼 터치를 병렬 논리합 처리
        if (possessKey.getPressed()) {
            mPossessButton.press(possessKey.getLastPressedTime(), possessKey.getMagnitude());
        } else if (possessTouch != null) {
            if (!mPossessButton.getPressed()) {
                mPossessButton.press(possessTouch.getLastPressedTime(), 1.0f);
            }
        } else {
            mPossessButton.release();
        }

        // HUD 출력 플래그 동기화 업로드
        final HudSystem hud = sSystemRegistry.hudSystem;
        if (hud != null) {
            hud.setButtonState(
                    mJumpButton.getPressed(),
                    mAttackButton.getPressed(),
                    mDirectionalPad.getPressed(),
                    mPossessButton.getPressed() // 4번째 인자로 실시간 전달 처리
            );
            hud.setMovementSliderOffset(sliderOffset);
        }
    }

    private float filterOrientationForMovement(float magnitude) {
        float scaledMagnitude = magnitude * mOrientationSensitivityFactor;

        return deadZoneFilter(scaledMagnitude, mOrientationDeadZoneMin, mOrientationDeadZoneMax, mOrientationDeadZoneScale);
    }

    private float deadZoneFilter(float magnitude, float min, float max, float scale) {
        float smoothedMagnatude = magnitude;
        if (Math.abs(magnitude) < min) {
            smoothedMagnatude = 0.0f;
        } else if (Math.abs(magnitude) < max) {
            smoothedMagnatude *= scale;
        }

        return smoothedMagnatude;
    }

    public final InputXY getDirectionalPad() {
        return mDirectionalPad;
    }

    public final InputXY getTilt() {
        return mTilt;
    }

    public final InputButton getJumpButton() {
        return mJumpButton;
    }

    public final InputButton getAttackButton() {
        return mAttackButton;
    }

    public final boolean isJumpAndAttackSameTouch() {
        return mJumpTouchSource != null && mAttackTouchSource != null && mJumpTouchSource == mAttackTouchSource;
    }

    public final InputButton getPossessButton() {
        return mPossessButton;
    }

    public void setKeys(int left, int right, int jump, int attack, int possess) {
        mLeftKeyCode = left;
        mRightKeyCode = right;
        mJumpKeyCode = jump;
        mAttackKeyCode = attack;
        mPossessKeyCode = possess;
    }

    public void setUseClickForAttack(boolean click) {
        mUseClickButtonForAttack = click;
    }

    public void setUseOrientationForMovement(boolean orientation) {
        mUseOrientationForMovement = orientation;
    }

    public void setOrientationMovementSensitivity(float sensitivity) {
        mOrientationSensitivity = sensitivity;
        mOrientationSensitivityFactor = 2.9f * sensitivity + 0.1f;
    }

    public void setMovementSensitivity(float sensitivity) {
        mMovementSensitivity  = sensitivity;
    }

    public void setUseOnScreenControls(boolean onscreen) {
        mUseOnScreenControls = onscreen;
    }
}